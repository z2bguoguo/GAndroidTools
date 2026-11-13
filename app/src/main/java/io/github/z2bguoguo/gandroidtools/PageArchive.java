// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Version 2 distinguishes actual window buffers from crops of the flattened screenshot. */
final class PageArchive {
  private PageArchive() {}

  static void write(
      File file, Bitmap edited, JSONObject tree, LayerSnapshot snapshot, List<TreeNode> hidden)
      throws Exception {
    File partial = new File(file.getParentFile(), file.getName() + ".part");
    try {
      try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(partial))) {
        JSONObject safeTree = sanitize(tree, hidden);
        JSONArray layers =
            new JSONArray()
                .put(
                    new JSONObject()
                        .put("id", "screen")
                        .put("kind", "display-composite")
                        .put("file", "screen.png")
                        .put(
                            "bounds",
                            new JSONArray(
                                new int[] {0, 0, edited.getWidth(), edited.getHeight()})));
        image(zip, "screen.png", edited);
        text(zip, "controls.json", safeTree.toString(2));
        JSONObject windowMetadata =
            snapshot == null ? new JSONObject().put("atomic", false) : snapshot.metadata();
        if (snapshot != null) {
          for (int i = 0; i < snapshot.windows.size(); i++) {
            LayerSnapshot.Window window = snapshot.windows.get(i);
            JSONObject descriptor = windowMetadata.getJSONArray("windows").getJSONObject(i);
            boolean hiddenHere = false;
            for (TreeNode node : hidden)
              if ((node.windowId == -1 || node.windowId == window.id)
                  && Rect.intersects(node.bounds, window.bounds)) hiddenHere = true;
            if (window.image != null
                && hiddenHere
                && (window.image.getWidth() != window.bounds.width()
                    || window.image.getHeight() != window.bounds.height())) {
              descriptor.put("status", "omitted-for-mask-safety").put("title", "");
              layers.put(descriptor);
              continue;
            }
            if (window.image != null) {
              Bitmap copy = window.image.copy(Bitmap.Config.ARGB_8888, true);
              try {
                Canvas canvas = new Canvas(copy);
                Paint clear = new Paint();
                clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                for (TreeNode node : hidden) {
                  int owner = node.windowId;
                  if (owner != -1 && owner != window.id) continue;
                  Rect rect = new Rect(node.bounds);
                  rect.offset(-window.bounds.left, -window.bounds.top);
                  canvas.drawRect(rect, clear);
                  descriptor.put("title", "");
                }
                String path = "layers/window-" + window.id + ".png";
                image(zip, path, copy);
                descriptor.put("file", path);
              } finally {
                copy.recycle();
              }
            }
            layers.put(descriptor);
          }
        }
        List<TreeNode> controls = TreeNode.parse(safeTree);
        controls.sort(Comparator.comparingLong(n -> (long) n.bounds.width() * n.bounds.height()));
        long budget = ToolApp.app.config.number("layerMemoryMb") * 1024L * 1024 / 4;
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (TreeNode node : controls) {
          if (count >= ToolApp.app.config.number("controlLayers")) break;
          Rect rect = new Rect(node.bounds);
          if (!rect.intersect(0, 0, edited.getWidth(), edited.getHeight())
              || rect.isEmpty()
              || !seen.add(rect.toShortString())
              || (long) rect.width() * rect.height() > budget) continue;
          long pixels = (long) rect.width() * rect.height();
          budget -= pixels;
          String path = "layers/control-" + count++ + ".png";
          Bitmap fragment =
              Bitmap.createBitmap(edited, rect.left, rect.top, rect.width(), rect.height());
          try {
            image(zip, path, fragment);
          } finally {
            if (fragment != edited) fragment.recycle();
          }
          layers.put(
              new JSONObject()
                  .put("kind", "composite-fragment")
                  .put("file", path)
                  .put("controlKey", node.key)
                  .put("bounds", LayerSnapshot.box(rect))
                  .put("independentSurface", false)
                  .put("source", "screen.png"));
        }
        text(
            zip,
            "layers.json",
            new JSONObject()
                .put("coordinateSystem", "screen-pixels")
                .put("capture", windowMetadata)
                .put("layers", layers)
                .toString(2));
        text(
            zip,
            "manifest.json",
            new JSONObject()
                .put("format", "gandroidtools.page")
                .put("version", 2)
                .put("image", "screen.png")
                .put("controls", "controls.json")
                .put("layers", "layers.json")
                .put("capturedAt", tree.optString("capturedAt"))
                .put("screenWidth", edited.getWidth())
                .put("screenHeight", edited.getHeight())
                .put("edited", !hidden.isEmpty())
                .put("atomic", false)
                .put("passwordTextExcluded", true)
                .toString(2));
      }
      if (!partial.renameTo(file)) throw new IllegalStateException("Cannot publish page archive");
    } finally {
      partial.delete();
    }
  }

  static JSONObject sanitize(JSONObject tree, List<TreeNode> hidden) throws Exception {
    JSONObject result = new JSONObject(tree.toString());
    JSONArray nodes = result.optJSONArray("nodes");
    if (nodes != null)
      for (int i = 0; i < nodes.length(); i++) {
        JSONObject value = nodes.getJSONObject(i);
        TreeNode node = new TreeNode(value);
        for (TreeNode mask : hidden)
          if ((mask.windowId == -1 || node.windowId == -1 || mask.windowId == node.windowId)
              && Rect.intersects(mask.bounds, node.bounds)) {
            value.put("text", "").put("description", "");
            break;
          }
      }
    return result;
  }

  static void text(ZipOutputStream zip, String path, String text) throws Exception {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  static void image(ZipOutputStream zip, String path, Bitmap bitmap) throws Exception {
    zip.putNextEntry(new ZipEntry(path));
    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip))
      throw new IllegalStateException("Cannot encode archive image");
    zip.closeEntry();
  }
}
