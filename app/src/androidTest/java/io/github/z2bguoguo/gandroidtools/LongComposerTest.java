// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class LongComposerTest {
  @Test
  public void fixedBarsAppearOnceAndHistoryUsesOnlyObservedUnmaskedPixels() throws Exception {
    ToolApp app =
        (ToolApp)
            InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    app.config.set("autoFixed", true);
    app.config.set("historyRepair", true);
    app.config.set("longArchive", true);
    app.config.set("jpeg", false);
    int w = 96, h = 180, top = 22, bottom = 18, shift = 63;
    int[] document = new int[w * h * 4];
    Random random = new Random(719);
    for (int i = 0; i < document.length; i++) document[i] = 0xff000000 | random.nextInt(0xffffff);
    File cache = new File(app.getCacheDir(), "long-test-" + System.nanoTime());
    assertTrue(cache.mkdirs());
    List<File> frames = new ArrayList<>();
    LongComposer.Result result = null;
    try {
      for (int index = 0; index < 3; index++) {
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++)
          for (int x = 0; x < w; x++)
            pixels[y * w + x] =
                y < top
                    ? 0xff245c49
                    : y >= h - bottom ? 0xff154b69 : document[(y - top + index * shift) * w + x];
        for (int y = 115; y < 141; y++)
          for (int x = 66; x < 90; x++)
            pixels[y * w + x] = (x == 77 || y == 128) ? Color.WHITE : 0xffff00ff;
        File frame = new File(cache, "frame-" + index + ".png");
        frames.add(frame);
        Bitmap bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(frame)) {
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
          bitmap.recycle();
        }
        JSONArray nodes =
            new JSONArray()
                .put(
                    new JSONObject()
                        .put("id", "page/scroll")
                        .put("class", "android.widget.ScrollView")
                        .put("scrollable", true)
                        .put("bounds", new JSONArray(new int[] {0, top, w, h - bottom})))
                .put(
                    new JSONObject()
                        .put("id", "page/floating")
                        .put("class", "android.widget.Button")
                        .put("text", "floating fixture")
                        .put("bounds", new JSONArray(new int[] {66, 115, 90, 141})));
        try (FileWriter writer = new FileWriter(new File(cache, frame.getName() + ".json"))) {
          writer.write(
              new JSONObject()
                  .put("nodes", nodes)
                  .put("region", new JSONArray(new int[] {0, 0, w, h}))
                  .toString());
        }
      }
      result = new LongComposer(app.config, () -> false, false).compose(frames);
      assertEquals(1, result.images().size());
      assertTrue(result.repaired() > 0);
      assertTrue(result.unknown() > 0);
      Bitmap output = BitmapFactory.decodeFile(result.images().get(0).toString());
      assertNotNull(output);
      try {
        assertEquals(h + shift * 2, output.getHeight());
        for (int y = 0; y < output.getHeight(); y++)
          for (int x = 0; x < w; x++) {
            int color = output.getPixel(x, y);
            if (y < top) assertEquals(0xff245c49, color);
            else if (y >= output.getHeight() - bottom) assertEquals(0xff154b69, color);
            else if (Color.alpha(color) != 0)
              assertEquals(
                  "Document pixel changed at " + x + "," + y, document[(y - top) * w + x], color);
          }
        assertEquals(0, Color.alpha(output.getPixel(70, 125 + shift * 2)));
      } finally {
        output.recycle();
      }
      try (ZipFile archive = new ZipFile(result.history())) {
        JSONObject metadata =
            new JSONObject(
                new String(
                    read(archive, "frames/frame-2.json"), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("", metadata.getJSONArray("nodes").getJSONObject(1).optString("text"));
        JSONObject report =
            new JSONObject(
                new String(
                    read(archive, "analysis.json"), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(report.getLong("repairedPixels") > 0);
        assertTrue(report.getLong("unobservedPixels") > 0);
        assertEquals(top, report.getJSONArray("frames").getJSONObject(1).getInt("fixedTop"));
      }
    } finally {
      if (result != null) {
        for (File file : result.images()) file.delete();
        if (result.history() != null) result.history().delete();
      }
      File[] files = cache.listFiles();
      if (files != null) for (File file : files) file.delete();
      cache.delete();
    }
  }

  private byte[] read(ZipFile zip, String path) throws Exception {
    try (java.io.InputStream in = zip.getInputStream(zip.getEntry(path));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
      return out.toByteArray();
    }
  }
}
