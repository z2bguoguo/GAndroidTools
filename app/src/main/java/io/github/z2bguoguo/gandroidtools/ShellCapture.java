// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Fixed shell screenshot fallback; never exports a general shell command API. */
final class ShellCapture {
  private ShellCapture() {}

  static Bitmap capture() throws Exception {
    Process process = new ProcessBuilder("/system/bin/screencap", "-p").start();
    FutureTask<byte[]> pixels =
        new FutureTask<>(() -> read(process.getInputStream(), 32 * 1024 * 1024));
    FutureTask<byte[]> errors = new FutureTask<>(() -> read(process.getErrorStream(), 8192));
    start(pixels, "gat-screencap-pixels");
    start(errors, "gat-screencap-errors");
    try {
      if (!process.waitFor(5, TimeUnit.SECONDS))
        throw new IllegalStateException("screencap timed out");
      if (process.exitValue() != 0)
        throw new IllegalStateException(
            "screencap exited "
                + process.exitValue()
                + ": "
                + new String(
                    errors.get(1, TimeUnit.SECONDS), java.nio.charset.StandardCharsets.UTF_8));
      byte[] png = pixels.get(1, TimeUnit.SECONDS);
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeByteArray(png, 0, png.length, bounds);
      if (bounds.outWidth <= 0
          || bounds.outHeight <= 0
          || (long) bounds.outWidth * bounds.outHeight > 32L * 1024 * 1024)
        throw new IllegalStateException("Invalid or oversized screenshot");
      Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
      if (bitmap == null) throw new IllegalStateException("Cannot decode screencap PNG");
      return bitmap;
    } finally {
      process.destroyForcibly();
      process.getInputStream().close();
      process.getErrorStream().close();
      process.getOutputStream().close();
      pixels.cancel(true);
      errors.cancel(true);
    }
  }

  private static void start(FutureTask<byte[]> task, String name) {
    Thread worker = new Thread(task, name);
    worker.setDaemon(true);
    worker.start();
  }

  private static byte[] read(InputStream source, int limit) throws Exception {
    try (InputStream input = source;
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (output.size() + count > limit)
          throw new IllegalStateException("Screenshot output limit exceeded");
        output.write(buffer, 0, count);
      }
      return output.toByteArray();
    }
  }
}
