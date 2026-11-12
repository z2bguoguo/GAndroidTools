// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.util.List;
import java.util.function.BooleanSupplier;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/** Interpreter boundary independent of Android; host operations remain explicitly typed. */
public final class MacroRuntime {
  public interface Host {
    Object call(String name, Object[] args, Context context, Scriptable scope, long deadline)
        throws Exception;
  }

  private MacroRuntime() {}

  public static void check(long deadline, BooleanSupplier cancelled) {
    if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
      throw new IllegalStateException("Macro cancelled");
    if (System.nanoTime() > deadline) throw new IllegalStateException("Macro time limit reached");
  }

  public static void run(String source, int timeoutMillis, BooleanSupplier cancelled, Host host) {
    if (source.length() > 200000) throw new IllegalArgumentException("Script exceeds 200 KB");
    long deadline = System.nanoTime() + Math.max(1, timeoutMillis) * 1000000L;
    ContextFactory factory =
        new ContextFactory() {
          @Override
          protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInstructionObserverThreshold(10000);
            cx.setClassShutter(name -> false);
            return cx;
          }

          @Override
          protected void observeInstructionCount(Context cx, int count) {
            check(deadline, cancelled);
          }
        };
    factory.call(
        cx -> {
          ScriptableObject scope = cx.initSafeStandardObjects();
          for (String key :
              new String[] {
                "Packages", "java", "javax", "org", "com", "getClass", "JavaAdapter", "JavaImporter"
              }) ScriptableObject.deleteProperty(scope, key);
          ScriptableObject tool = (ScriptableObject) cx.newObject(scope);
          for (String name :
              List.of(
                  "wait",
                  "tap",
                  "swipe",
                  "back",
                  "find",
                  "click",
                  "longClick",
                  "text",
                  "scroll",
                  "log")) {
            BaseFunction fn =
                new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {
                  @Override
                  public Object call(
                      Context context, Scriptable parent, Scriptable self, Object[] args) {
                    check(deadline, cancelled);
                    try {
                      return host.call(name, args, context, scope, deadline);
                    } catch (Exception e) {
                      throw Context.throwAsScriptRuntimeEx(e);
                    }
                  }
                };
            tool.defineProperty(name, fn, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
          }
          scope.defineProperty(
              "tool", tool, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
          cx.evaluateString(scope, source, "macro.js", 1, null);
          return null;
        });
  }
}
