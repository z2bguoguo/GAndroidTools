// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventLog {
  private final ArrayDeque<String> lines = new ArrayDeque<>();
  private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
  private final File file;

  public EventLog(Context context) {
    file = new File(context.getFilesDir(), "runtime.log");
  }

  public synchronized void add(String level, String area, String message) {
    String line =
        ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            + " ["
            + level
            + "] "
            + area
            + " · "
            + redact(message);
    lines.add(line);
    while (lines.size() > ToolApp.app.config.number("logLines")) lines.removeFirst();
    try {
      if (file.length() > 2 * 1024 * 1024) {
        File old = new File(file.getParentFile(), "runtime.previous.log");
        if (old.exists() && !old.delete()) throw new java.io.IOException("Cannot rotate logs");
        if (!file.renameTo(old)) throw new java.io.IOException("Cannot rotate logs");
      }
      try (FileWriter out = new FileWriter(file, true)) {
        out.write(line + "\n");
      }
    } catch (Exception e) {
      Log.e("GAndroidTools", "Log persistence failed", e);
    }
    for (Runnable r : listeners) ToolApp.app.main.post(r);
  }

  public void error(String area, Throwable error) {
    StringWriter trace = new StringWriter();
    error.printStackTrace(new PrintWriter(trace));
    add("ERROR", area, trace.toString());
  }

  public synchronized List<String> snapshot() {
    return new ArrayList<>(lines);
  }

  public synchronized String export() {
    StringBuilder out = new StringBuilder();
    for (String name : new String[] {"runtime.previous.log", "runtime.log"})
      try {
        File f = new File(file.getParentFile(), name);
        if (f.exists()) out.append(FileText.read(f.toPath()));
      } catch (Exception e) {
        out.append("Log read failed\n");
      }
    return out.toString();
  }

  public void listen(Runnable r) {
    listeners.add(r);
  }

  public void unlisten(Runnable r) {
    listeners.remove(r);
  }

  static String redact(String value) {
    return value
        .replaceAll(
            "(?i)(token|secret|password|authorization)([\\s:=]+)[^\\s,;]+", "$1$2[redacted]")
        .replaceAll("otpauth://[^\\s]+", "[OTP URI redacted]");
  }
}
