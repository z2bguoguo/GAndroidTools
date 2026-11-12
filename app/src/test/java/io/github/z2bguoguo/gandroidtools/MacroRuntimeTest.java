// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class MacroRuntimeTest {
  @Test(timeout = 2500)
  public void infiniteLoopHitsDeadline() {
    assertThrows(
        RuntimeException.class,
        () -> MacroRuntime.run("while(true){}", 60, () -> false, (n, a, c, s, d) -> null));
  }

  @Test
  public void cancellationStopsHostCall() {
    assertThrows(
        RuntimeException.class,
        () -> MacroRuntime.run("tool.back()", 500, () -> true, (n, a, c, s, d) -> null));
  }

  @Test
  public void languageLoopsAndObjectsWork() {
    List<String> actions = new ArrayList<>();
    MacroRuntime.run(
        "const obj={n:3}; for(let i=0;i<obj.n;i++) tool.log(i);",
        1000,
        () -> false,
        (n, a, c, s, d) -> {
          actions.add(n);
          return null;
        });
    assertEquals(List.of("log", "log", "log"), actions);
  }

  @Test
  public void javaAccessIsUnavailable() {
    assertThrows(
        RuntimeException.class,
        () ->
            MacroRuntime.run(
                "java.lang.Runtime.getRuntime().exec('id')",
                1000,
                () -> false,
                (n, a, c, s, d) -> null));
  }

  @Test
  public void scopeDoesNotLeak() {
    MacroRuntime.run("var privateValue=1;", 1000, () -> false, (n, a, c, s, d) -> null);
    MacroRuntime.run(
        "if (typeof privateValue !== 'undefined') throw new Error('leak');",
        1000,
        () -> false,
        (n, a, c, s, d) -> null);
  }

  @Test
  public void selectorRequiresUniqueMatch() throws Exception {
    JSONArray nodes =
        new JSONArray()
            .put(new JSONObject().put("id", "x").put("package", "a"))
            .put(new JSONObject().put("id", "x").put("package", "b"));
    assertThrows(
        IllegalStateException.class,
        () -> Selectors.unique(nodes, new JSONObject().put("id", "x")));
    assertEquals(
        "a",
        Selectors.unique(nodes, new JSONObject().put("id", "x").put("package", "a"))
            .getString("package"));
    assertNull(Selectors.unique(nodes, new JSONObject().put("id", "missing")));
  }

  @Test
  public void emptySelectorIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> Selectors.unique(new JSONArray(), new JSONObject()));
  }
}
