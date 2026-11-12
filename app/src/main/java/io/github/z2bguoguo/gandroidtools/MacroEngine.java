// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;

public final class MacroEngine {
  private final ToolApp app;
  private final AtomicBoolean running = new AtomicBoolean();
  private volatile boolean cancelled;
  private volatile boolean recording;
  private final JSONArray events = new JSONArray();
  private long lastEvent;

  public MacroEngine(ToolApp app) {
    this.app = app;
  }

  public boolean running() {
    return running.get();
  }

  public boolean recording() {
    return recording;
  }

  public void stop() {
    cancelled = true;
  }

  public synchronized void startRecording() {
    if (running()) throw new IllegalStateException("A macro is running");
    while (events.length() > 0) events.remove(0);
    lastEvent = android.os.SystemClock.elapsedRealtime();
    recording = true;
    app.log.add("INFO", "macro", "Recording started");
  }

  public synchronized String stopRecording() throws Exception {
    recording = false;
    StringBuilder code = new StringBuilder("// Recorded locally. Review before replay.\n");
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.getJSONObject(i);
      code.append("tool.wait(").append(e.getLong("delay")).append(");\n");
      code.append("tool.")
          .append(e.getString("op"))
          .append("(")
          .append(e.getJSONObject("selector"));
      if (e.has("value")) code.append(",").append(JSONObject.quote(e.getString("value")));
      code.append(");\n");
    }
    app.log.add("INFO", "macro", "Recording stopped · " + events.length() + " events");
    return code.toString();
  }

  public synchronized void record(String op, JSONObject selector, String value) {
    if (!recording) return;
    if (events.length() >= 1000) {
      recording = false;
      app.log.add("WARN", "macro", "Recording limit reached");
      return;
    }
    try {
      long now = android.os.SystemClock.elapsedRealtime();
      JSONObject e =
          new JSONObject()
              .put("delay", Math.min(10000, Math.max(50, now - lastEvent)))
              .put("op", op)
              .put("selector", selector);
      if (value != null) e.put("value", value);
      events.put(e);
      lastEvent = now;
    } catch (Exception e) {
      app.log.error("macro.record", e);
    }
  }

  public void run(String source, java.util.function.Consumer<Exception> done) {
    if (source.length() > 200000) throw new IllegalArgumentException("Script too large");
    if (!running.compareAndSet(false, true))
      throw new IllegalStateException("A macro is already running");
    cancelled = false;
    app.worker.execute(
        () -> {
          Exception failure = null;
          try {
            MacroRuntime.run(
                source, app.config.number("macroTimeout") * 1000, () -> cancelled, this::execute);
            app.log.add("INFO", "macro", "Run completed");
          } catch (Exception e) {
            failure = e;
            app.log.error(
                "macro",
                new IllegalStateException("Macro stopped: " + e.getClass().getSimpleName()));
          } finally {
            running.set(false);
            Exception value = failure;
            app.main.post(() -> done.accept(value));
          }
        });
  }

  private void check(long deadline) {
    if (cancelled || Thread.currentThread().isInterrupted())
      throw new IllegalStateException("Macro cancelled");
    if (System.nanoTime() > deadline) throw new IllegalStateException("Macro time limit reached");
  }

  private Object execute(String name, Object[] args, Context cx, Scriptable scope, long deadline)
      throws Exception {
    if (name.equals("wait")) {
      int ms = (int) Math.max(0, Math.min(60000, Context.toNumber(args[0])));
      long end = System.nanoTime() + ms * 1000000L;
      while (System.nanoTime() < end) {
        check(deadline);
        Thread.sleep(25);
      }
      return true;
    }
    if (name.equals("log")) {
      app.log.add("INFO", "macro", "Script checkpoint");
      return true;
    }
    if (name.equals("back")) {
      app.bridge.call("back");
      return true;
    }
    if (name.equals("tap")) {
      app.bridge.call(
          "tap",
          new JSONObject()
              .put("x", (int) Context.toNumber(args[0]))
              .put("y", (int) Context.toNumber(args[1])));
      return true;
    }
    if (name.equals("swipe")) {
      JSONObject p = json(args[0], cx, scope);
      app.bridge.call("swipe", p);
      return true;
    }
    JSONObject selector = json(args[0], cx, scope);
    TreeNode node = find(selector);
    if (name.equals("find")) {
      return node == null
          ? null
          : NativeJSON.parse(
              cx,
              scope,
              new JSONObject()
                  .put("key", node.key)
                  .put("text", node.text)
                  .put("id", node.id)
                  .toString(),
              (c, s, t, a) -> a[1]);
    }
    if (node == null) throw new IllegalStateException("Macro control not found");
    JSONObject p = new JSONObject().put("key", node.key).put("operation", name);
    if (name.equals("text")) p.put("value", Context.toString(args[1]));
    if (!app.bridge.call("node", p).optBoolean("performed"))
      throw new IllegalStateException("Control rejected macro action");
    return true;
  }

  private static JSONObject json(Object value, Context cx, Scriptable scope) throws Exception {
    return new JSONObject(Context.toString(NativeJSON.stringify(cx, scope, value, null, null)));
  }

  public TreeNode find(JSONObject selector) throws Exception {
    JSONObject node = Selectors.unique(app.bridge.call("tree").getJSONArray("nodes"), selector);
    return node == null ? null : new TreeNode(node);
  }

  public File directory() {
    File f = new File(app.getFilesDir(), "macros");
    f.mkdirs();
    return f;
  }
}
