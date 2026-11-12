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
  private final Shizuku.UserServiceArgs args;
  private final ServiceConnection connection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
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
                } catch (Exception e) {
                  app.log.error("shizuku", e);
                }
              });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          remote = null;
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

  public void bind() {
    try {
      Shizuku.bindUserService(args, connection);
    } catch (Exception e) {
      app.log.error("shizuku.bind", e);
    }
  }

  public JSONObject call(String action, JSONObject data) throws Exception {
    IRemoteTools service = remote;
    if (service == null)
      throw new IllegalStateException(
          ToolApp.t("Shizuku 服务未连接，请在首页授权", "Shizuku service is not connected; authorize on Home"));
    long start = android.os.SystemClock.elapsedRealtime();
    data.put("action", action);
    JSONObject response = new JSONObject(service.call(data.toString()));
    if (!response.optBoolean("ok"))
      throw new IllegalStateException(response.optString("error", "Remote operation failed"));
    app.log.add(
        "DEBUG",
        "bridge",
        action + " · " + (android.os.SystemClock.elapsedRealtime() - start) + " ms");
    return response;
  }

  public JSONObject call(String action) throws Exception {
    return call(action, new JSONObject());
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
