// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ToolApp extends Application {
  public static ToolApp app;
  public final ExecutorService worker = Executors.newCachedThreadPool();
  public final Handler main = new Handler(Looper.getMainLooper());
  public Config config;
  public EventLog log;
  public Bridge bridge;
  public InfoStore info;
  public MacroEngine macros;

  @Override
  public void onCreate() {
    super.onCreate();
    app = this;
    config = new Config(this);
    log = new EventLog(this);
    bridge = new Bridge(this);
    info = new InfoStore(this);
    macros = new MacroEngine(this);
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          log.error("crash", error);
          if (previous != null) previous.uncaughtException(thread, error);
        });
    log.add(
        "INFO",
        "app",
        "Start " + BuildConfig.VERSION_NAME + " · Android " + android.os.Build.VERSION.RELEASE);
  }

  public static String t(String zh, String en) {
    return app.config.english() ? en : zh;
  }
}
