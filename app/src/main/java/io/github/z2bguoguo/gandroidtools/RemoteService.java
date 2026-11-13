// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs exclusively in a Shizuku-started shell process. No arbitrary shell API is exposed. */
public final class RemoteService extends IRemoteTools.Stub {
  private final HandlerThread thread = new HandlerThread("gat-automation");
  private UiAutomation automation;
  private String automationError = "Not connected";
  private String automationTrace = "";
  private String contextError = "";
  private long automationRetryAt;
  private String captureBackend = "", captureError = "";
  private final ControlTree controls = new ControlTree("shell");
  private String automationState = "DISCONNECTED";
  private Object automationConnection;
  private final Map<Integer, Parked> parked = new LinkedHashMap<>();

  private static final class Parked {
    final int task;
    final String component;
    final VirtualDisplay display;
    final ImageReader reader;
    Bitmap preview;
    long lastPreview;

    Parked(int task, String component, VirtualDisplay display, ImageReader reader) {
      this.task = task;
      this.component = component;
      this.display = display;
      this.reader = reader;
    }
  }

  private Context context;
  private Bitmap frozenBitmap;
  private int frozenId;

  public RemoteService() {
    this(null);
  }

  /** Shizuku already owns ActivityThread; never call systemMain a second time. */
  public RemoteService(Context supplied) {
    thread.start();
    CallIdentity.run(
        shellIdentity,
        () -> {
          try {
            Context base = supplied;
            if (base == null) {
              Class<?> activityThread = Class.forName("android.app.ActivityThread");
              Object current = activityThread.getMethod("currentActivityThread").invoke(null);
              if (current == null)
                throw new IllegalStateException("Shizuku runtime has no ActivityThread");
              base = (Context) activityThread.getMethod("getSystemContext").invoke(current);
            }
            context =
                base.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
          } catch (Throwable error) {
            contextError = ErrorDetails.trace(error);
          }
          return null;
        });
  }

  private void ensureAutomation() {
    if (automation != null || android.os.SystemClock.elapsedRealtime() < automationRetryAt) return;
    automationRetryAt = android.os.SystemClock.elapsedRealtime() + 30_000;
    UiAutomation candidate = null;
    Object impl = null;
    String stage = "connection constructor";
    try {
      Class<?> connection = Class.forName("android.app.IUiAutomationConnection");
      impl =
          Class.forName("android.app.UiAutomationConnection")
              .getDeclaredConstructor()
              .newInstance();
      stage = "UiAutomation constructor";
      candidate =
          UiAutomation.class
              .getConstructor(android.os.Looper.class, connection)
              .newInstance(thread.getLooper(), impl);
      stage = "connect (preserve accessibility services)";
      // Run lazily inside the identity-cleared request, with a separate callback looper.
      if (android.os.Build.VERSION.SDK_INT >= 31) {
        UiAutomation.class
            .getMethod("connectWithTimeout", int.class, long.class)
            .invoke(candidate, 1, 4000L);
      } else {
        UiAutomation.class.getMethod("connect", int.class).invoke(candidate, 1);
      }
      automation = candidate;
      automationConnection = impl;
      automationState = "READY";
      automationError = automationTrace = "";
    } catch (Throwable error) {
      automationState = AutomationFailure.classify(error);
      automationError =
          automationState.equals("BUSY")
              ? "UiAutomation is already registered by another connection. Enable the accessibility"
                  + " control fallback or stop the tool owning that connection, then retry."
              : stage + ": " + ErrorDetails.message(error);
      automationTrace = stage + "\n" + ErrorDetails.trace(error);
      closeFailedAutomation(candidate, impl);
    }
  }

  // This method runs only in the shell app_process, never in the application VM.
  @android.annotation.SuppressLint("SoonBlockedPrivateApi")
  private static void closeFailedAutomation(UiAutomation candidate, Object connection) {
    if (connection != null)
      try {
        connection.getClass().getMethod("shutdown").invoke(connection);
      } catch (Throwable ignored) {
        /* May not have registered a client. */
      }
    if (candidate != null)
      try {
        // A framework exception during connect can leave UiAutomation in CONNECTING,
        // where disconnect() refuses to stop its callback thread.
        java.lang.reflect.Field callback =
            UiAutomation.class.getDeclaredField("mRemoteCallbackThread");
        callback.setAccessible(true);
        Object value = callback.get(candidate);
        if (value instanceof HandlerThread worker) worker.quitSafely();
      } catch (Throwable ignored) {
        /* Older frameworks can use the supplied looper. */
      }
  }

  private Bitmap captureScreen() throws Exception {
    ensureAutomation();
    captureError = "";
    if (automation != null) {
      try {
        Bitmap bitmap = automation.takeScreenshot();
        if (bitmap == null)
          throw new IllegalStateException("UiAutomation returned an empty screenshot");
        captureBackend = "UiAutomation";
        return bitmap;
      } catch (Exception error) {
        captureError = ErrorDetails.trace(error);
      }
    } else captureError = automationState.equals("BUSY") ? automationError : automationTrace;
    try {
      Bitmap bitmap = ShellCapture.capture();
      captureBackend = "shell screencap";
      return bitmap;
    } catch (Exception error) {
      throw new IllegalStateException(
          "Both screenshot backends failed: "
              + ErrorDetails.message(error)
              + "\nUiAutomation: "
              + (captureError.isEmpty() ? automationError : captureError),
          error);
    }
  }

  private final CallIdentity.Identity shellIdentity =
      new CallIdentity.Identity() {
        public long clear() {
          return android.os.Binder.clearCallingIdentity();
        }

        public void restore(long token) {
          android.os.Binder.restoreCallingIdentity(token);
        }
      };

  @Override
  public synchronized String call(String request) {
    return CallIdentity.run(shellIdentity, () -> dispatch(request));
  }

  private String dispatch(String request) {
    JSONObject result = new JSONObject();
    try {
      JSONObject p = new JSONObject(request);
      switch (p.getString("action")) {
        case "retryAutomation", "status" -> {
          if (p.getString("action").equals("retryAutomation")) automationRetryAt = 0;
          ensureAutomation();
          result.put("uid", android.os.Process.myUid());
          result.put("executionUid", android.os.Binder.getCallingUid());
          result.put("automation", automation != null);
          result.put("automationState", automationState);
          result.put("automationError", automationError);
          result.put("automationTrace", automationTrace);
          result.put("contextError", contextError);
          result.put("captureBackend", captureBackend);
          result.put("virtualDisplay", context != null);
        }
        case "freeze" -> {
          if (frozenBitmap != null) frozenBitmap.recycle();
          frozenBitmap = null;
          frozenBitmap = captureScreen();
          if (frozenBitmap == null) throw new IllegalStateException("Screenshot unavailable");
          frozenId = frozenId == Integer.MAX_VALUE ? 1 : frozenId + 1;
          result.put("captureId", frozenId);
          result.put("capturedAt", java.time.Instant.now().toString());
          result.put("captureBackend", captureBackend);
          result.put("captureWarning", captureError);
          result.put("controlBackend", "UiAutomation");
          try {
            requireAutomation();
            result.put("nodes", dump(false));
            result.put("treeAvailable", true);
          } catch (Exception error) {
            controls.clear();
            result.put("nodes", new JSONArray());
            result.put("treeAvailable", false);
            result.put(
                "treeError",
                automationState.equals("BUSY") ? automationError : ErrorDetails.trace(error));
            result.put("controlBackend", "unavailable");
            result.put("automationState", automationState);
          }
        }
        case "tree" -> {
          requireAutomation();
          result.put("nodes", dump(p.optBoolean("allWindows", false)));
        }
        case "node" -> {
          requireAutomation();
          result.put("performed", controls.perform(p));
        }
        case "input" -> {
          requireAutomation();
          AccessibilityNodeInfo root = automation.getRootInActiveWindow();
          if (root == null) {
            result.put("performed", false);
            break;
          }
          if (BuildConfig.APPLICATION_ID.contentEquals(
              root.getPackageName() == null ? "" : root.getPackageName())) {
            root.recycle();
            result.put("performed", false);
            break;
          }
          try {
            AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focus == null) {
              result.put("performed", false);
              break;
            }
            try {
              if (focus.isPassword()
                  || !focus.isEditable()
                  || !focus.isEnabled()
                  || !focus.isVisibleToUser())
                throw new SecurityException("Password or non-editable field");
              Bundle b = new Bundle();
              b.putCharSequence(
                  AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                  p.getString("value"));
              result.put(
                  "performed", focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b));
            } finally {
              focus.recycle();
            }
          } finally {
            root.recycle();
          }
        }
        case "tap" -> {
          int x = p.getInt("x"), y = p.getInt("y");
          result.put(
              "output",
              shell(List.of("input", "tap", Integer.toString(x), Integer.toString(y)), 4));
        }
        case "swipe" -> {
          result.put(
              "output",
              shell(
                  List.of(
                      "input",
                      "swipe",
                      "" + p.getInt("x1"),
                      "" + p.getInt("y1"),
                      "" + p.getInt("x2"),
                      "" + p.getInt("y2"),
                      "" + Math.max(50, Math.min(2000, p.optInt("duration", 400)))),
                  5));
        }
        case "back" -> result.put("output", shell(List.of("input", "keyevent", "4"), 4));
        case "services" -> {
          result.put("enabled", setting("get", "enabled_accessibility_services", null).trim());
          result.put(
              "installed",
              shell(
                  List.of(
                      "pm",
                      "query-services",
                      "--brief",
                      "-a",
                      "android.accessibilityservice.AccessibilityService"),
                  5));
        }
        case "service" -> {
          String component = p.getString("component");
          ComponentName c = ComponentName.unflattenFromString(component);
          if (c == null || !component.matches("[a-zA-Z0-9_.$]+/[a-zA-Z0-9_.$]+"))
            throw new IllegalArgumentException("Invalid service component");
          java.util.LinkedHashSet<String> enabled = new java.util.LinkedHashSet<>();
          for (String s : setting("get", "enabled_accessibility_services", null).trim().split(":"))
            if (!s.isBlank() && !s.equals("null")) enabled.add(s);
          if (p.getBoolean("enabled")) enabled.add(component);
          else enabled.remove(component);
          setting("put", "enabled_accessibility_services", String.join(":", enabled));
          setting("put", "accessibility_enabled", enabled.isEmpty() ? "0" : "1");
          String actual = setting("get", "enabled_accessibility_services", null).trim();
          boolean present = java.util.Arrays.asList(actual.split(":")).contains(component);
          if (present != p.getBoolean("enabled"))
            throw new IllegalStateException("System rejected accessibility setting");
        }
        case "tasks" -> result.put("tasks", tasks());
        case "focusedTask" -> result.put("task", focusedTask());
        case "park" -> result.put("task", park(p.getInt("taskId")));
        case "restore" -> restore(p.getInt("taskId"));
        case "parked" -> {
          JSONArray list = new JSONArray();
          JSONArray live = tasks();
          for (Parked item : new ArrayList<>(parked.values())) {
            int currentDisplay = -1;
            for (int i = 0; i < live.length(); i++) {
              JSONObject t = live.getJSONObject(i);
              if (t.getInt("taskId") == item.task) currentDisplay = t.getInt("displayId");
            }
            if (currentDisplay == 0 || currentDisplay == -1) {
              release(item);
              continue;
            }
            list.put(
                new JSONObject()
                    .put("taskId", item.task)
                    .put("component", item.component)
                    .put("label", appTitle(ComponentName.unflattenFromString(item.component)))
                    .put("displayId", item.display.getDisplay().getDisplayId()));
          }
          result.put("tasks", list);
        }
        default -> throw new IllegalArgumentException("Unknown operation");
      }
      result.put("ok", true);
    } catch (Throwable error) {
      try {
        result
            .put("ok", false)
            .put("error", ErrorDetails.message(error))
            .put("errorTrace", ErrorDetails.trace(error))
            .put("automationState", automationState);
      } catch (Exception ignored) {
        /* JSON literals cannot fail. */
      }
    }
    return result.toString();
  }

  private void requireAutomation() {
    ensureAutomation();
    if (automation == null)
      throw new IllegalStateException("UI automation unavailable: " + automationError);
  }

  private JSONArray dump(boolean all) throws Exception {
    return controls.dump(all, automation::getRootInActiveWindow, automation::getWindows);
  }

  private String setting(String verb, String key, String value) throws Exception {
    List<String> args =
        new ArrayList<>(List.of("settings", "--user", "current", verb, "secure", key));
    if (value != null) args.add(value);
    return shell(args, 4);
  }

  private static String shell(List<String> command, int seconds) throws Exception {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Thread drain =
        new Thread(
            () -> {
              try (InputStream in = process.getInputStream()) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) != -1) {
                  if (bytes.size() + n > 512 * 1024) {
                    process.destroyForcibly();
                    break;
                  }
                  bytes.write(b, 0, n);
                }
              } catch (Exception ignored) {
                /* Exit status below reports failure. */
              }
            });
    drain.start();
    if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("System command timed out");
    }
    drain.join(1000);
    String output = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    if (process.exitValue() != 0
        || output.startsWith("Error:")
        || output.contains("SecurityException")) throw new IllegalStateException(output);
    return output;
  }

  private Object taskService() throws Exception {
    return Class.forName(
            android.os.Build.VERSION.SDK_INT >= 29
                ? "android.app.ActivityTaskManager"
                : "android.app.ActivityManager")
        .getMethod("getService")
        .invoke(null);
  }

  private Class<?> taskInterface() throws Exception {
    return Class.forName(
        android.os.Build.VERSION.SDK_INT >= 29
            ? "android.app.IActivityTaskManager"
            : "android.app.IActivityManager");
  }

  private JSONArray tasks() throws Exception {
    Object service = taskService();
    List<?> list =
        (List<?>)
            taskInterface()
                .getMethod(
                    android.os.Build.VERSION.SDK_INT >= 30
                        ? "getAllRootTaskInfos"
                        : "getAllStackInfos")
                .invoke(service);
    JSONArray out = new JSONArray();
    for (Object info : list) {
      Class<?> cls = info.getClass();
      int id =
          cls.getField(android.os.Build.VERSION.SDK_INT >= 30 ? "taskId" : "stackId").getInt(info);
      int display = cls.getField("displayId").getInt(info);
      ComponentName top = (ComponentName) cls.getField("topActivity").get(info);
      if (top != null)
        out.put(
            new JSONObject()
                .put("taskId", id)
                .put("displayId", display)
                .put("component", top.flattenToShortString())
                .put("label", appTitle(top)));
    }
    return out;
  }

  private String appTitle(ComponentName component) {
    if (component == null) return "Application";
    try {
      return context
          .getPackageManager()
          .getApplicationLabel(
              context.getPackageManager().getApplicationInfo(component.getPackageName(), 0))
          .toString();
    } catch (Exception error) {
      return component.getShortClassName().replace(".", " ").trim();
    }
  }

  private JSONObject focusedTask() throws Exception {
    if (android.os.Build.VERSION.SDK_INT < 30)
      throw new IllegalStateException("Task parking requires Android 11 or later");
    Object info = taskInterface().getMethod("getFocusedRootTaskInfo").invoke(taskService());
    if (info == null) throw new IllegalStateException("No foreground task");
    int id = info.getClass().getField("taskId").getInt(info);
    JSONArray all = tasks();
    for (int i = 0; i < all.length(); i++) {
      JSONObject task = all.getJSONObject(i);
      if (task.getInt("taskId") == id && task.getInt("displayId") == 0) return task;
    }
    throw new IllegalStateException("Focused task is not on the primary display");
  }

  private JSONObject park(int task) throws Exception {
    if (android.os.Build.VERSION.SDK_INT < 30)
      throw new IllegalStateException("Task parking requires Android 11 or later");
    if (context == null) throw new IllegalStateException("Display manager unavailable");
    JSONObject chosen = null;
    JSONArray all = tasks();
    for (int i = 0; i < all.length(); i++)
      if (all.getJSONObject(i).getInt("taskId") == task) chosen = all.getJSONObject(i);
    if (chosen == null || chosen.getInt("displayId") != 0)
      throw new IllegalArgumentException("Choose a task on the main display");
    String component = chosen.getString("component");
    if (component.contains("gandroidtools") || component.contains("launcher"))
      throw new IllegalArgumentException("Cannot park the toolbox or launcher");
    if (parked.size() >= 4)
      throw new IllegalStateException("At most four hidden displays are supported");
    if (parked.containsKey(task)) throw new IllegalStateException("Task already parked");
    DisplayManager dm = context.getSystemService(DisplayManager.class);
    android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    ImageReader reader =
        ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 3);
    // Drain frames to keep the virtual display rendering without unbounded allocation.

    VirtualDisplay vd =
        dm.createVirtualDisplay(
            "GAndroidTools " + task,
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            reader.getSurface(),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
    if (vd == null) {
      reader.close();
      throw new IllegalStateException("System refused virtual display");
    }
    Parked item = new Parked(task, component, vd, reader);
    reader.setOnImageAvailableListener(
        r -> {
          synchronized (r) {
            try (Image image = r.acquireLatestImage()) {
              if (image == null
                  || android.os.SystemClock.elapsedRealtime() - item.lastPreview < 1500) return;
              item.lastPreview = android.os.SystemClock.elapsedRealtime();
              Image.Plane plane = image.getPlanes()[0];
              int padding = plane.getRowStride() - plane.getPixelStride() * image.getWidth();
              Bitmap padded =
                  Bitmap.createBitmap(
                      image.getWidth() + padding / plane.getPixelStride(),
                      image.getHeight(),
                      Bitmap.Config.ARGB_8888);
              padded.copyPixelsFromBuffer(plane.getBuffer());
              Bitmap cropped =
                  Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
              Bitmap thumb =
                  Bitmap.createScaledBitmap(
                      cropped, 240, Math.max(1, 240 * image.getHeight() / image.getWidth()), true);
              synchronized (item) {
                if (item.preview != null) item.preview.recycle();
                item.preview = thumb;
              }
              if (cropped != padded && cropped != thumb) cropped.recycle();
              if (padded != thumb) padded.recycle();
            } catch (RuntimeException ignored) {
              /* Display closed. */
            }
          }
        },
        new Handler(thread.getLooper()));
    try {
      move(task, vd.getDisplay().getDisplayId());
      boolean verified = false;
      JSONArray after = tasks();
      for (int i = 0; i < after.length(); i++)
        if (after.getJSONObject(i).getInt("taskId") == task
            && after.getJSONObject(i).getInt("displayId") == vd.getDisplay().getDisplayId())
          verified = true;
      if (!verified) throw new IllegalStateException("Task did not move to the virtual display");
      parked.put(task, item);
      return chosen.put("displayId", vd.getDisplay().getDisplayId());
    } catch (Exception e) {
      try {
        move(task, 0);
      } catch (Exception restore) {
        e.addSuppressed(restore);
      }
      vd.release();
      reader.close();
      throw e;
    }
  }

  private void move(int task, int display) throws Exception {
    Object service = taskService();
    taskInterface()
        .getMethod(
            android.os.Build.VERSION.SDK_INT >= 30 ? "moveRootTaskToDisplay" : "moveStackToDisplay",
            int.class,
            int.class)
        .invoke(service, task, display);
  }

  private void restore(int task) throws Exception {
    Parked item = parked.get(task);
    if (item == null) throw new IllegalStateException("Parked task no longer exists");
    move(task, 0);
    try {
      shell(List.of("am", "task", "focus", Integer.toString(task)), 5);
    } finally {
      release(item);
    }
  }

  private void release(Parked p) {
    parked.remove(p.task);
    p.display.release();
    synchronized (p.reader) {
      p.reader.close();
    }
    synchronized (p) {
      if (p.preview != null) {
        p.preview.recycle();
        p.preview = null;
      }
    }
  }

  @Override
  public synchronized ParcelFileDescriptor screenshot(int display) {
    return CallIdentity.run(shellIdentity, () -> screenshotInternal(display));
  }

  private ParcelFileDescriptor screenshotInternal(int display) {
    try {
      Bitmap bitmap;
      if (display < 0) {
        if (display != -frozenId)
          throw new IllegalStateException("Frozen screenshot expired; capture again");
        bitmap = frozenBitmap;
        frozenBitmap = null;
      } else if (display == 0) {
        bitmap = captureScreen();
      } else {
        Parked match = null;
        for (Parked p : parked.values())
          if (p.display.getDisplay().getDisplayId() == display) match = p;
        if (match == null) throw new IllegalStateException("Hidden display is no longer available");
        synchronized (match) {
          if (match.preview == null) throw new IllegalStateException("Preview is not ready");
          bitmap = match.preview.copy(Bitmap.Config.ARGB_8888, false);
        }
      }
      if (bitmap == null) throw new IllegalStateException("System screenshot unavailable");
      ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
      new Thread(
              () -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream out =
                    new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                  bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (Exception ignored) {
                  /* Reader sees an incomplete frame. */
                } finally {
                  bitmap.recycle();
                }
              },
              "gat-frame")
          .start();
      return pipe[0];
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public synchronized void destroy() {
    CallIdentity.run(
        shellIdentity,
        () -> {
          destroyInternal();
          return null;
        });
  }

  synchronized void releaseAutomation() {
    if (automation != null)
      try {
        UiAutomation.class.getMethod("disconnect").invoke(automation);
      } catch (Exception ignored) {
        /* Shutdown below also releases this connection. */
      }
    closeFailedAutomation(automation, automationConnection);
    automation = null;
    automationConnection = null;
    automationState = "DISCONNECTED";
    controls.clear();
  }

  private void destroyInternal() {
    for (Parked p : new ArrayList<>(parked.values()))
      try {
        move(p.task, 0);
        release(p);
      } catch (Exception ignored) {
        /* System will relocate tasks when process dies. */
      }
    releaseAutomation();
    thread.quitSafely();
    System.exit(0);
  }
}
