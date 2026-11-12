// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public class UiSmokeTest {
  @Test
  public void bilingualNavigationAndNotebookStorage() {
    ToolApp app =
        (ToolApp)
            InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    app.config.set("language", "en");
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            TextView settings = find(a.getWindow().getDecorView(), "⚙\nSettings");
            assertNotNull(settings);
            settings.performClick();
            TextView appearance = find(a.getWindow().getDecorView(), "Language and appearance");
            assertNotNull(appearance);
            View click = appearance;
            while (!click.isClickable() && click.getParent() instanceof View parent) click = parent;
            assertTrue(click.performClick());
            TextView change = find(a.getWindow().getDecorView(), "切换到中文");
            assertNotNull(change);
            change.performClick();
            assertNotNull(find(a.getWindow().getDecorView(), "⚙\n设置"));
            TextView notebook = find(a.getWindow().getDecorView(), "▤\n信息本");
            assertNotNull(notebook);
            notebook.performClick();
            assertNotNull(find(a.getWindow().getDecorView(), "信息本"));
          });
    }
    for (Actions.Action action : Actions.ALL)
      assertFalse(SettingsHelp.action(action.id()).isBlank());
    long folder = app.info.add(0, "test-folder-" + System.nanoTime(), "folder", "");
    assertFalse(app.info.list(0, "test-folder-", 0).isEmpty());
    long child = app.info.add(folder, "nested", "folder", "");
    long item = app.info.add(child, "a%_", "text", "value");
    assertEquals("value", app.info.get(item).value());
    assertEquals(1, app.info.list(child, "%_", 0).size());
    app.info.delete(folder);
    assertThrows(IllegalArgumentException.class, () -> app.info.get(item));
  }

  private TextView find(View v, String text) {
    if (v instanceof TextView t && t.getText().toString().equals(text)) return t;
    if (v instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        TextView found = find(group.getChildAt(i), text);
        if (found != null) return found;
      }
    return null;
  }
}
