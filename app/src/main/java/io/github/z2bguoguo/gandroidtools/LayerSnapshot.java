// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Immutable window buffers and metadata, owned by one logical (not atomic) capture. */
final class LayerSnapshot implements AutoCloseable {
  static final class Window {
    final int id, type, z;
    final Rect bounds;
    final String title, packageName;
    Bitmap image;
    long timestamp;
    String status;

    Window(AccessibilityWindowInfo info) {
      id = info.getId();
      type = info.getType();
      z = info.getLayer();
      bounds = new Rect();
      info.getBoundsInScreen(bounds);
      CharSequence text = info.getTitle();
      title = text == null ? "" : text.subSequence(0, Math.min(256, text.length())).toString();
      AccessibilityNodeInfo root = info.getRoot();
      try {
        packageName =
            root == null || root.getPackageName() == null ? "" : root.getPackageName().toString();
      } finally {
        if (root != null) root.recycle();
      }
      status = "metadata-only";
    }
  }

  final List<Window> windows = new ArrayList<>();
  final long startedNs;
  long completedNs;
  String capability;
  private static final java.util.concurrent.Semaphore PIXEL_SNAPSHOTS =
      new java.util.concurrent.Semaphore(2);
  private boolean pixelSlot;
  private int references = 1;
  private boolean accepting = true;
  private long bytes;

  private LayerSnapshot(JSONObject primary) {
    startedNs = primary.optLong("captureStartedElapsedNs", SystemClock.elapsedRealtimeNanos());
  }

  static LayerSnapshot capture(JSONObject primary) {
    LayerSnapshot snapshot = new LayerSnapshot(primary);
    InputService service = InputService.instance;
    snapshot.capability =
        service == null
            ? "accessibility-service-disabled"
            : Build.VERSION.SDK_INT < 34 ? "window-pixels-require-api-34" : "window-surfaces";
    if (service == null) {
      snapshot.completedNs = SystemClock.elapsedRealtimeNanos();
      snapshot.accepting = false;
      return snapshot;
    }
    List<AccessibilityWindowInfo> list;
    try {
      list = service.getWindows();
    } catch (Exception error) {
      snapshot.capability = "window-list-unavailable";
      snapshot.accepting = false;
      snapshot.completedNs = SystemClock.elapsedRealtimeNanos();
      return snapshot;
    }
    int limit = ToolApp.app.config.number("windowLayers");
    try {
      for (AccessibilityWindowInfo info : list) {
        if (snapshot.windows.size() >= limit) break;
        if (info.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
        try {
          Window window = new Window(info);
          if (window.packageName.equals(BuildConfig.APPLICATION_ID)
              && window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
          snapshot.windows.add(window);
        } catch (Exception ignored) {
          /* A window can disappear while metadata is queried. */
        }
      }
    } finally {
      for (AccessibilityWindowInfo info : list) info.recycle();
    }
    snapshot.windows.sort(Comparator.comparingInt((Window w) -> w.z).reversed());
    if (Build.VERSION.SDK_INT >= 34 && ToolApp.app.config.flag("windowLayerPixels", true)) {
      if (PIXEL_SNAPSHOTS.tryAcquire()) {
        snapshot.pixelSlot = true;
        snapshot.capturePixels(service);
      } else snapshot.capability = "window-pixel-budget-in-use";
    }
    synchronized (snapshot) {
      snapshot.accepting = false;
      for (Window window : snapshot.windows)
        if (window.status.equals("requested")) window.status = "timed-out";
    }
    snapshot.completedNs = SystemClock.elapsedRealtimeNanos();
    return snapshot;
  }

  @android.annotation.TargetApi(34)
  private void capturePixels(InputService service) {
    long budget =
        Math.min(
            ToolApp.app.config.number("layerMemoryMb") * 1024L * 1024,
            Math.max(8L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8));
    List<Window> planned = new ArrayList<>();
    long estimated = 0;
    // Prefer real application windows over system decorations within the same bounded budget.
    List<Window> priority = new ArrayList<>(windows);
    priority.sort(
        Comparator.comparingInt(w -> w.type == AccessibilityWindowInfo.TYPE_APPLICATION ? 0 : 1));
    for (Window window : priority) {
      if (window.packageName.isBlank()) {
        window.status = "no-readable-root";
        continue;
      }
      long size = (long) window.bounds.width() * window.bounds.height() * 4;
      if (size <= 0 || estimated + size > budget) {
        window.status = "budget-skipped";
        continue;
      }
      estimated += size;
      window.status = "requested";
      planned.add(window);
    }
    CountDownLatch done = new CountDownLatch(planned.size());
    for (Window window : planned) {
      try {
        service.takeScreenshotOfWindow(
            window.id,
            ToolApp.app.worker,
            new AccessibilityService.TakeScreenshotCallback() {
              @Override
              public void onSuccess(AccessibilityService.ScreenshotResult result) {
                Bitmap hardware = null, copy = null;
                try (android.hardware.HardwareBuffer buffer = result.getHardwareBuffer()) {
                  hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                  if (hardware == null) throw new IllegalStateException("Empty window buffer");
                  long size = (long) hardware.getWidth() * hardware.getHeight() * 4;
                  synchronized (LayerSnapshot.this) {
                    if (!accepting || references == 0 || size > budget - bytes) {
                      window.status = "budget-or-deadline-skipped";
                      return;
                    }
                    copy = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    if (copy == null)
                      throw new IllegalStateException("Cannot retain window buffer");
                    window.image = copy;
                    copy = null;
                    bytes += size;
                    window.timestamp = result.getTimestamp();
                    window.status = "captured";
                  }
                } catch (Exception error) {
                  synchronized (LayerSnapshot.this) {
                    window.status = "capture-failed";
                  }
                } finally {
                  if (hardware != null) hardware.recycle();
                  if (copy != null) copy.recycle();
                  done.countDown();
                }
              }

              @Override
              public void onFailure(int error) {
                synchronized (LayerSnapshot.this) {
                  window.status =
                      error == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
                          ? "protected-window"
                          : "platform-error-" + error;
                }
                done.countDown();
              }
            });
      } catch (Exception error) {
        window.status = "request-rejected";
        done.countDown();
      }
    }
    try {
      done.await(2500, TimeUnit.MILLISECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }

  synchronized LayerSnapshot retain() {
    if (references == 0) throw new IllegalStateException("Layer snapshot released");
    references++;
    return this;
  }

  @Override
  public synchronized void close() {
    if (references == 0 || --references > 0) return;
    accepting = false;
    if (pixelSlot) {
      pixelSlot = false;
      PIXEL_SNAPSHOTS.release();
    }
    for (Window window : windows)
      if (window.image != null) {
        window.image.recycle();
        window.image = null;
      }
  }

  synchronized int pixelCount() {
    int count = 0;
    for (Window window : windows) if (window.image != null) count++;
    return count;
  }

  JSONObject metadata() throws Exception {
    JSONArray result = new JSONArray();
    synchronized (this) {
      for (Window w : windows)
        result.put(
            new JSONObject()
                .put("kind", "window-surface")
                .put("windowId", w.id)
                .put("type", w.type)
                .put("z", w.z)
                .put("title", w.title)
                .put("package", w.packageName)
                .put("bounds", box(w.bounds))
                .put("status", w.status)
                .put("platformTimestampMs", w.timestamp)
                .put("pixelWidth", w.image == null ? 0 : w.image.getWidth())
                .put("pixelHeight", w.image == null ? 0 : w.image.getHeight()));
    }
    return new JSONObject()
        .put("capability", capability)
        .put("atomic", false)
        .put("captureStartedElapsedNs", startedNs)
        .put("captureCompletedElapsedNs", completedNs)
        .put("windows", result);
  }

  /**
   * A genuine application-window buffer can omit separate overlay windows. No same-window view
   * removal is claimed.
   */
  synchronized Bitmap cleanApplication(Bitmap display, String packageName) {
    Window best = null;
    for (Window w : windows) {
      if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION
          || w.image == null
          || !w.packageName.equals(packageName)
          || w.bounds.width() < display.getWidth() / 2
          || w.bounds.height() < display.getHeight() / 2) continue;
      double x = (double) w.image.getWidth() / w.bounds.width(),
          y = (double) w.image.getHeight() / w.bounds.height();
      if (Math.abs(x - y) > 0.03 || Math.abs(x - 1) > 0.03) continue;
      if (best == null
          || (long) w.bounds.width() * w.bounds.height()
              > (long) best.bounds.width() * best.bounds.height()) best = w;
    }
    if (best == null) return null;
    Bitmap output = display.copy(Bitmap.Config.ARGB_8888, true);
    if (output == null) return null;
    Canvas canvas = new Canvas(output);
    android.graphics.Paint paint = new android.graphics.Paint();
    paint.setXfermode(
        new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_OVER));
    canvas.drawBitmap(best.image, null, best.bounds, paint);
    return output;
  }

  static JSONArray box(Rect rect) {
    return new JSONArray().put(rect.left).put(rect.top).put(rect.right).put(rect.bottom);
  }
}
