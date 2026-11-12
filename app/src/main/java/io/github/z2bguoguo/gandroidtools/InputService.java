package io.github.z2bguoguo.gandroidtools;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;

public final class InputService extends AccessibilityService {
  public static volatile InputService instance;

  @Override
  protected void onServiceConnected() {
    instance = this;
    ToolApp.app.log.add("INFO", "accessibility", "Input bridge connected");
  }

  @Override
  public void onDestroy() {
    instance = null;
    super.onDestroy();
  }

  @Override
  public void onInterrupt() {
    ToolApp.app.log.add("WARN", "accessibility", "Input bridge interrupted");
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (!ToolApp.app.macros.recording()
        || event.isPassword()
        || getPackageName()
            .contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) return;
    AccessibilityNodeInfo n = event.getSource();
    if (n == null) return;
    try {
      if (n.isPassword()) return;
      String op =
          switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED -> "click";
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "longClick";
            case AccessibilityEvent.TYPE_VIEW_SCROLLED -> "scroll";
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "text";
            default -> null;
          };
      if (op == null) return;
      if (op.equals("text") && !ToolApp.app.config.flag("recordText", false)) return;
      JSONObject selector =
          new JSONObject()
              .put("package", String.valueOf(n.getPackageName()))
              .put("class", String.valueOf(n.getClassName()));
      if (n.getViewIdResourceName() != null) selector.put("id", n.getViewIdResourceName());
      else if (n.getContentDescription() != null)
        selector.put("description", n.getContentDescription().toString());
      else if (!op.equals("text") && n.getText() != null)
        selector.put("text", n.getText().toString());
      else return;
      ToolApp.app.macros.record(
          op, selector, op.equals("text") && n.getText() != null ? n.getText().toString() : null);
    } catch (Exception e) {
      ToolApp.app.log.error("macro.record", e);
    } finally {
      n.recycle();
    }
  }

  public boolean input(String text) {
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null) return false;
    try {
      if (getPackageName()
          .contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) return false;
      AccessibilityNodeInfo n = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      if (n == null) return false;
      try {
        if (!n.isEditable() || n.isPassword() || !n.isEnabled() || !n.isVisibleToUser())
          return false;
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
      } finally {
        n.recycle();
      }
    } finally {
      root.recycle();
    }
  }
}
