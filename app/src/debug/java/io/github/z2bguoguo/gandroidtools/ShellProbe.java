// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;

/**
 * Debug-only integration check, launched with adb shell app_process after instrumentation exits.
 */
public final class ShellProbe {
  public static void main(String[] args) {
    try {
      if (android.os.Process.myUid() != 2000) throw new AssertionError("Run as shell");
      if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
      Class<?> activityThread = Class.forName("android.app.ActivityThread");
      Object runtime = activityThread.getMethod("systemMain").invoke(null);
      Context context = (Context) activityThread.getMethod("getSystemContext").invoke(runtime);
      RemoteService service = new RemoteService(context);
      if (activityThread.getMethod("currentActivityThread").invoke(null) != runtime)
        throw new AssertionError("Remote service replaced its host ActivityThread");
      JSONObject status = new JSONObject(service.call("{\"action\":\"status\"}"));
      if (!status.optBoolean("ok") || !status.optBoolean("automation"))
        throw new AssertionError("Automation unavailable: " + status.optString("automationTrace"));
      if (!status.optString("contextError").isEmpty())
        throw new AssertionError("Shell context unavailable: " + status.getString("contextError"));
      JSONObject frozen = new JSONObject(service.call("{\"action\":\"freeze\"}"));
      if (!frozen.optBoolean("ok") || !frozen.optBoolean("treeAvailable"))
        throw new AssertionError(
            "Frozen controls failed: "
                + frozen.optString("treeError")
                + frozen.optString("errorTrace"));
      try (ParcelFileDescriptor fd = service.screenshot(-frozen.getInt("captureId"));
          ParcelFileDescriptor.AutoCloseInputStream input =
              new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
        Bitmap frame = BitmapFactory.decodeStream(input);
        if (frame == null || frame.getWidth() < 1)
          throw new AssertionError("Frozen frame unavailable");
        frame.recycle();
      }
      Bitmap fallback = ShellCapture.capture();
      if (fallback.getWidth() < 1 || fallback.getHeight() < 1)
        throw new AssertionError("Fallback frame unavailable");
      fallback.recycle();
      RemoteService contender = new RemoteService(context);
      JSONObject busy = new JSONObject(contender.call("{\"action\":\"status\"}"));
      if (!busy.optString("automationState").equals("BUSY"))
        throw new AssertionError("Expected exclusive connection conflict");
      JSONObject degraded = new JSONObject(contender.call("{\"action\":\"freeze\"}"));
      if (!degraded.optBoolean("ok")
          || degraded.optBoolean("treeAvailable")
          || !degraded.optString("captureBackend").equals("shell screencap"))
        throw new AssertionError("Busy capture did not retain pixels");
      try (ParcelFileDescriptor fd = contender.screenshot(-degraded.getInt("captureId"));
          ParcelFileDescriptor.AutoCloseInputStream input =
              new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
        Bitmap pixels = BitmapFactory.decodeStream(input);
        if (pixels == null) throw new AssertionError("Busy screenshot unavailable");
        pixels.recycle();
      }
      if (!new JSONObject(service.call("{\"action\":\"status\"}")).optBoolean("automation"))
        throw new AssertionError("Competing attempt disrupted owner");
      service.releaseAutomation();
      JSONObject recovered = new JSONObject(contender.call("{\"action\":\"retryAutomation\"}"));
      if (!recovered.optBoolean("automation"))
        throw new AssertionError(
            "Connection did not recover after owner released it: " + recovered);
      contender.releaseAutomation();
      System.out.println(
          "GAT_SHELL_PROBE_OK uid=2000 automation=true frozen=true screencap=true busyFallback=true"
              + " recovered=true");
      service.destroy();
    } catch (Throwable error) {
      error.printStackTrace();
      System.exit(1);
    }
  }
}
