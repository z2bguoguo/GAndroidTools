// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ControlFallbackTest {
  private String shell(UiAutomation automation, String command) throws Exception {
    try (ParcelFileDescriptor fd = automation.executeShellCommand(command);
        ParcelFileDescriptor.AutoCloseInputStream input =
            new ParcelFileDescriptor.AutoCloseInputStream(fd);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[4096];
      int n;
      while ((n = input.read(b)) != -1) {
        if (out.size() + n > 32768) throw new AssertionError("Shell output limit");
        out.write(b, 0, n);
      }
      return out.toString("UTF-8").trim();
    }
  }

  @Test
  public void nativeControlsCoexistWithAutomationAndExcludePasswordContent() throws Exception {
    UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(1);
    ToolApp app =
        (ToolApp)
            InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    String component = new android.content.ComponentName(app, InputService.class).flattenToString();
    String previous =
        shell(automation, "settings --user current get secure enabled_accessibility_services");
    boolean owned = Arrays.asList(previous.split(":")).contains(component);
    if (previous.equals("null")) previous = "";
    if (!previous.matches("[a-zA-Z0-9_.$/:]*"))
      throw new AssertionError("Invalid service settings fixture");
    try {
      shell(
          automation,
          "settings --user current put secure enabled_accessibility_services "
              + (previous.isEmpty() ? component : previous + ":" + component));
      shell(automation, "settings --user current put secure accessibility_enabled 1");
      long deadline = android.os.SystemClock.elapsedRealtime() + 6000;
      while (InputService.instance == null && android.os.SystemClock.elapsedRealtime() < deadline)
        Thread.sleep(100);
      assertNotNull("Native fallback service did not connect", InputService.instance);
      try (ActivityScenario<ControlProbeActivity> scenario =
          ActivityScenario.launch(ControlProbeActivity.class)) {
        JSONObject tree = null;
        deadline = android.os.SystemClock.elapsedRealtime() + 4000;
        String fieldKey = "";
        while (fieldKey.isEmpty() && android.os.SystemClock.elapsedRealtime() < deadline) {
          tree = app.bridge.call("tree");
          for (TreeNode n : TreeNode.parse(tree))
            if (n.id.equals("android:id/edit")) fieldKey = n.key;
          if (fieldKey.isEmpty()) Thread.sleep(100);
        }
        assertFalse("Fixture field missing", fieldKey.isEmpty());
        assertEquals("accessibility", tree.getString("controlBackend"));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try (LayerSnapshot layers = LayerSnapshot.capture(tree)) {
          assertFalse(layers.windows.isEmpty());
          if (android.os.Build.VERSION.SDK_INT >= 34)
            assertTrue(
                "Independent window screenshot missing: " + layers.metadata(),
                layers.pixelCount() > 0);
          else assertEquals(0, layers.pixelCount());
          TreeNode hidden =
              TreeNode.parse(tree).stream()
                  .filter(n -> n.id.equals("android:id/edit"))
                  .findFirst()
                  .orElseThrow();
          if (android.os.Build.VERSION.SDK_INT >= 34) {
            assertTrue(
                "Application window buffer missing: " + layers.metadata(),
                layers.windows.stream().anyMatch(w -> w.id == hidden.windowId && w.image != null));
          }
          verifyMaskedArchive(automation, tree, layers, hidden);
          android.graphics.Bitmap composite =
              android.graphics.Bitmap.createBitmap(
                  96, 180, android.graphics.Bitmap.Config.ARGB_8888);
          composite.eraseColor(android.graphics.Color.BLUE);
          File page = Exports.file("layer-test-" + System.nanoTime() + ".gatpage");
          try {
            PageArchive.write(page, composite, tree, layers, List.of());
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(page)) {
              assertNotNull(zip.getEntry("layers.json"));
              assertNotNull(zip.getEntry("controls.json"));
            }
          } finally {
            composite.recycle();
            page.delete();
          }
        }

        assertFalse(tree.toString().contains("excluded-password-fixture"));
        assertTrue(fieldKey.startsWith("access:"));
        assertTrue(
            app.bridge
                .call(
                    "node",
                    new JSONObject()
                        .put("key", fieldKey)
                        .put("operation", "text")
                        .put("value", "changed fixture"))
                .getBoolean("performed"));
        String stale = fieldKey;
        app.bridge.call("tree");
        assertThrows(
            IllegalStateException.class,
            () ->
                app.bridge.call(
                    "node", new JSONObject().put("key", stale).put("operation", "click")));
      }
    } finally {
      if (!owned) {
        String current =
            shell(automation, "settings --user current get secure enabled_accessibility_services");
        List<String> services = new ArrayList<>(Arrays.asList(current.split(":")));
        services.remove(component);
        services.remove("null");
        services.remove("");
        String retained = String.join(":", services);
        if (!retained.matches("[a-zA-Z0-9_.$/:]*"))
          throw new AssertionError("Invalid final service settings");
        shell(
            automation,
            retained.isEmpty()
                ? "settings --user current delete secure enabled_accessibility_services"
                : "settings --user current put secure enabled_accessibility_services " + retained);
        if (retained.isEmpty())
          shell(automation, "settings --user current put secure accessibility_enabled 0");
      }
    }
  }

  private void verifyMaskedArchive(
      UiAutomation automation, JSONObject tree, LayerSnapshot layers, TreeNode hidden)
      throws Exception {
    Bitmap screen = automation.takeScreenshot();
    assertNotNull(screen);
    Bitmap edited =
        Bitmap.createBitmap(screen.getWidth(), screen.getHeight(), Bitmap.Config.ARGB_8888);
    ControlPreview.drawImage(new Canvas(edited), screen, List.of(hidden), i -> false);
    File page = Exports.file("masked-layers-" + System.nanoTime() + ".gatpage");
    try {
      PageArchive.write(page, edited, tree, layers, List.of(hidden));
      try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(page)) {
        JSONObject controls = new JSONObject(zipText(zip, "controls.json"));
        for (TreeNode node : TreeNode.parse(controls))
          if (node.id.equals(hidden.id)) assertEquals("", node.text);
        JSONArray descriptors = new JSONObject(zipText(zip, "layers.json")).getJSONArray("layers");
        for (int i = 0; i < descriptors.length(); i++) {
          JSONObject descriptor = descriptors.getJSONObject(i);
          if (!descriptor.optString("kind").equals("window-surface")
              || descriptor.optInt("windowId", -2) != hidden.windowId) continue;
          if (!descriptor.has("file")) continue;
          try (java.io.InputStream input =
              zip.getInputStream(zip.getEntry(descriptor.getString("file")))) {
            Bitmap image = android.graphics.BitmapFactory.decodeStream(input);
            assertNotNull(image);
            try {
              JSONArray bounds = descriptor.getJSONArray("bounds");
              int x = hidden.bounds.centerX() - bounds.getInt(0),
                  y = hidden.bounds.centerY() - bounds.getInt(1);
              assertTrue(x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight());
              assertEquals(
                  "Hidden control leaked through a window buffer",
                  0,
                  Color.alpha(image.getPixel(x, y)));
            } finally {
              image.recycle();
            }
          }
        }
        assertNull(zip.getEntry("original.png"));
      }
    } finally {
      screen.recycle();
      edited.recycle();
      page.delete();
    }
  }

  private String zipText(java.util.zip.ZipFile zip, String path) throws Exception {
    try (java.io.InputStream input = zip.getInputStream(zip.getEntry(path));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
      return bytes.toString("UTF-8");
    }
  }

  @Test
  public void hiddenPixelsExportWithAlphaAndKeepVisiblePixels() throws Exception {
    Bitmap source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
    source.eraseColor(Color.RED);
    Bitmap output = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
    TreeNode node =
        new TreeNode(
            new JSONObject().put("path", "0").put("bounds", new JSONArray(new int[] {1, 1, 3, 3})));
    ControlPreview.drawImage(new Canvas(output), source, List.of(node), i -> false);
    assertEquals(0, Color.alpha(output.getPixel(1, 1)));
    assertEquals(Color.RED, output.getPixel(0, 0));
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    output.compress(Bitmap.CompressFormat.PNG, 100, png);
    byte[] bytes = png.toByteArray();
    Bitmap decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    assertEquals(0, Color.alpha(decoded.getPixel(2, 2)));
    source.recycle();
    output.recycle();
    decoded.recycle();
  }
}
