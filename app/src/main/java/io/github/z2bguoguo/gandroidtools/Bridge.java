// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;

public final class Bridge {
  private final ToolApp app;
  private volatile IRemoteTools remote;
  private volatile boolean binding;
  private volatile String automationState = "DISCONNECTED";

  public String controlStatus() {
    if (InputService.instance != null && !automationState.equals("READY"))
      return ToolApp.t("无障碍兼容通道", "Accessibility fallback");
    return switch (automationState) {
      case "READY" -> ToolApp.t("Shizuku 控件通道就绪", "Shizuku controls ready");
      case "BUSY" -> ToolApp.t("系统控件通道被占用", "System control channel occupied");
      default -> ToolApp.t("控件通道未就绪", "Controls not ready");
    };
  }

  public void retryControls() {
    app.worker.execute(
        () -> {
          try {
            call("retryAutomation");
          } catch (Exception error) {
            app.log.error("controls.retry", error);
          }
        });
  }

  private final Shizuku.UserServiceArgs args;
  private final ServiceConnection connection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
          binding = false;
          if (remote != null && remote.asBinder().equals(binder)) return;
          remote = IRemoteTools.Stub.asInterface(binder);
          app.log.add("INFO", "shizuku", "User service connected");
          app.worker.execute(
              () -> {
                try {
                  JSONObject status = call("status", new JSONObject());
                  app.log.add(
                      "INFO",
                      "shizuku.identity",
                      "processUid="
                          + status.optInt("uid")
                          + " · executionUid="
                          + status.optInt("executionUid"));
                  app.log.add(
                      status.optBoolean("automation") ? "INFO" : "WARN",
                      "capabilities",
                      status.optBoolean("automation")
                          ? "UI automation available"
                          : status.optString("automationError"));
                  if (!status.optString("automationTrace").isEmpty())
                    app.log.add(
                        "DEBUG", "capabilities.detail", status.getString("automationTrace"));
                  if (!status.optString("contextError").isEmpty())
                    app.log.add("WARN", "shizuku.context", status.getString("contextError"));
                } catch (Exception e) {
                  app.log.error("shizuku", e);
                }
              });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          remote = null;
          binding = false;
          automationState = "DISCONNECTED";
          app.log.add("WARN", "shizuku", "User service disconnected");
        }
      };

  public Bridge(ToolApp app) {
    this.app = app;
    args =
        new Shizuku.UserServiceArgs(new ComponentName(app, RemoteService.class))
            .processNameSuffix("tools")
            .daemon(false)
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE);
    Shizuku.addBinderReceivedListenerSticky(
        () -> {
          app.log.add("INFO", "shizuku", "Binder received");
          if (authorized()) bind();
        });
    Shizuku.addBinderDeadListener(
        () -> {
          remote = null;
          binding = false;
          automationState = "DISCONNECTED";
          app.log.add("WARN", "shizuku", "Binder died; reauthorization may be required");
        });
    Shizuku.addRequestPermissionResultListener(
        (request, result) -> {
          if (result == PackageManager.PERMISSION_GRANTED) bind();
          else app.log.add("WARN", "shizuku", "Permission denied");
        });
  }

  public boolean authorized() {
    try {
      return Shizuku.pingBinder()
          && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    } catch (Exception e) {
      return false;
    }
  }

  public boolean ready() {
    return remote != null && remote.asBinder().isBinderAlive();
  }

  public void authorize() {
    try {
      if (!Shizuku.pingBinder())
        throw new IllegalStateException(ToolApp.t("请先启动 Shizuku", "Start Shizuku first"));
      if (authorized()) bind();
      else Shizuku.requestPermission(100);
    } catch (Exception e) {
      app.log.error("shizuku", e);
      Ui.toast(app, e.getMessage());
    }
  }

  public synchronized void bind() {
    if (ready() || binding) return;
    try {
      binding = true;
      Shizuku.bindUserService(args, connection);
    } catch (Exception e) {
      binding = false;
      app.log.error("shizuku.bind", e);
    }
  }

  public JSONObject call(String action, JSONObject data) throws Exception {
    long start = android.os.SystemClock.elapsedRealtime();
    data.put("action", action);
    InputService access = InputService.instance;
    if (action.equals("node") && data.optString("key").startsWith("access:")) {
      if (access == null)
        throw new IllegalStateException("Accessibility controls disconnected; refresh the tree");
      return access.control(action, data);
    }
    if ((action.equals("tree") || action.equals("input"))
        && access != null
        && (!ready() || !automationState.equals("READY"))) return access.control(action, data);
    IRemoteTools service = remote;
    if (service == null)
      throw new IllegalStateException(
          ToolApp.t("Shizuku 服务未连接，请在首页授权", "Shizuku service is not connected; authorize on Home"));
    JSONObject response = new JSONObject(service.call(data.toString()));
    if (response.has("automationState")) automationState = response.getString("automationState");
    if (!response.optBoolean("ok")) {
      if ((action.equals("tree") || action.equals("input"))
          && InputService.instance != null
          && !automationState.equals("READY")) return InputService.instance.control(action, data);
      app.log.add(
          "ERROR",
          "remote." + action,
          response.optString("errorTrace", response.optString("error")));
      throw new IllegalStateException(response.optString("error", "Remote operation failed"));
    }
    if (action.equals("freeze") && !response.optBoolean("treeAvailable") && access != null) {
      try {
        JSONObject tree =
            access.control(
                "tree", new JSONObject().put("allWindows", data.optBoolean("allWindows", false)));
        response
            .put("nodes", tree.getJSONArray("nodes"))
            .put("treeAvailable", true)
            .put("treeError", "")
            .put("controlBackend", "accessibility");
        app.log.add(
            "INFO",
            "controls.fallback",
            "Frozen controls captured through enabled accessibility service");
      } catch (Exception error) {
        app.log.add("WARN", "controls.fallback", ErrorDetails.trace(error));
      }
    }
    app.log.add(
        "DEBUG",
        "bridge",
        action + " · " + (android.os.SystemClock.elapsedRealtime() - start) + " ms");
    return response;
  }

  public JSONObject call(String action) throws Exception {
    return call(action, new JSONObject());
  }

  public record FrozenFrame(JSONObject metadata, Bitmap image) {}

  private final Object captureLock = new Object();

  /** Keep the metadata ID and image transfer together when screenshot tools run concurrently. */
  public FrozenFrame freeze(boolean allWindows) throws Exception {
    synchronized (captureLock) {
      JSONObject metadata = call("freeze", new JSONObject().put("allWindows", allWindows));
      return new FrozenFrame(metadata, screenshot(-metadata.getInt("captureId")));
    }
  }

  public Bitmap screenshot(int display) throws Exception {
    IRemoteTools service = remote;
    if (service == null) throw new IllegalStateException("Shizuku disconnected");
    try (ParcelFileDescriptor fd = service.screenshot(display);
        ParcelFileDescriptor.AutoCloseInputStream in =
            new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
      Bitmap bitmap = BitmapFactory.decodeStream(in);
      if (bitmap == null)
        throw new IllegalStateException(
            "Screenshot unavailable; display or protected content may not permit capture");
      return bitmap;
    }
  }
}
