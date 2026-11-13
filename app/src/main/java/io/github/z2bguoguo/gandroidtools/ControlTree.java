// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Each provider owns a separate bounded node cache; keys cannot cross providers. */
final class ControlTree {
  private final String backend;
  private final String session = java.util.UUID.randomUUID().toString();
  private final Map<String, AccessibilityNodeInfo> nodes = new LinkedHashMap<>();
  private int treeBytes;
  private long treeGeneration;

  ControlTree(String backend) {
    this.backend = backend;
  }

  synchronized void clear() {
    for (AccessibilityNodeInfo old : nodes.values()) old.recycle();
    nodes.clear();
  }

  synchronized JSONArray dump(
      boolean all,
      Supplier<AccessibilityNodeInfo> active,
      Supplier<List<AccessibilityWindowInfo>> windows)
      throws Exception {
    clear();
    treeBytes = 0;
    treeGeneration++;
    JSONArray out = new JSONArray();
    try {
      if (all) {
        List<AccessibilityWindowInfo> list = windows.get();
        try {
          for (AccessibilityWindowInfo window : list) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null)
              try {
                walk(root, "w" + window.getId(), 0, out);
              } finally {
                root.recycle();
              }
          }
        } finally {
          for (AccessibilityWindowInfo window : list) window.recycle();
        }
      } else {
        AccessibilityNodeInfo root = active.get();
        if (root != null)
          try {
            walk(root, "0", 0, out);
          } finally {
            root.recycle();
          }
      }
      return out;
    } catch (Exception error) {
      clear();
      throw error;
    }
  }

  synchronized boolean perform(JSONObject request) throws Exception {
    AccessibilityNodeInfo node = nodes.get(request.getString("key"));
    if (node == null || !node.refresh())
      throw new IllegalStateException(
          "Control is stale or belongs to another provider; refresh the page tree");
    String operation = request.getString("operation");
    if (operation.equals("text")) {
      if (node.isPassword() || !node.isEditable() || !node.isEnabled() || !node.isVisibleToUser())
        throw new SecurityException("This field does not allow snippet input");
      Bundle bundle = new Bundle();
      bundle.putCharSequence(
          AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, request.getString("value"));
      return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);
    }
    int action =
        switch (operation) {
          case "click" -> AccessibilityNodeInfo.ACTION_CLICK;
          case "longClick" -> AccessibilityNodeInfo.ACTION_LONG_CLICK;
          case "show" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId();
          case "scroll" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
          default -> throw new IllegalArgumentException("Unsupported node action");
        };
    return node.performAction(action);
  }

  private void walk(AccessibilityNodeInfo n, String key, int depth, JSONArray out)
      throws Exception {
    if (nodes.size() >= 2200 || depth > 60 || treeBytes > 600000) return;
    Rect r = new Rect();
    n.getBoundsInScreen(r);
    boolean secret = n.isPassword();
    String token = backend + ":" + session + ":" + treeGeneration + "@" + key;
    nodes.put(token, AccessibilityNodeInfo.obtain(n));
    JSONObject encoded =
        new JSONObject()
            .put("key", token)
            .put("path", key)
            .put("depth", depth)
            .put("text", secret ? "" : bounded(n.getText()))
            .put("description", secret ? "" : bounded(n.getContentDescription()))
            .put("id", n.getViewIdResourceName())
            .put("class", bounded(n.getClassName()))
            .put("package", bounded(n.getPackageName()))
            .put("bounds", new JSONArray(new int[] {r.left, r.top, r.right, r.bottom}))
            .put("clickable", n.isClickable())
            .put("editable", n.isEditable())
            .put("scrollable", n.isScrollable())
            .put("password", secret)
            .put("enabled", n.isEnabled());
    treeBytes += encoded.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    out.put(encoded);
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo child = n.getChild(i);
      if (child != null) {
        try {
          walk(child, key + "." + i, depth + 1, out);
        } finally {
          child.recycle();
        }
      }
    }
  }

  private static String bounded(CharSequence s) {
    return s == null ? "" : s.subSequence(0, Math.min(512, s.length())).toString();
  }
}
