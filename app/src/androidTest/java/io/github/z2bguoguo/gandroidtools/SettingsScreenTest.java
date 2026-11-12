package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;

public class SettingsScreenTest {
  @Test
  public void categorizedSettingsExposeMirroredSemicircles() throws Exception {
    ToolApp.app.config.set("language", "zh");
    if (android.os.Build.VERSION.SDK_INT >= 33)
      InstrumentationRegistry.getInstrumentation()
          .getUiAutomation()
          .grantRuntimePermission(
              InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName(),
              android.Manifest.permission.POST_NOTIFICATIONS);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            TextView settings = find(a.getWindow().getDecorView(), "⚙\n设置");
            assertNotNull(settings);
            settings.performClick();
          });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      capture("settings-categories.png");
      scenario.onActivity(
          a -> {
            TextView floating = find(a.getWindow().getDecorView(), "悬浮球与手势");
            assertNotNull(floating);
            clickRow(floating);
          });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      capture("semicircle-left.png");
      scenario.onActivity(
          a -> {
            TextView right = find(a.getWindow().getDecorView(), "预览右侧");
            assertNotNull(right);
            right.performClick();
          });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      capture("semicircle-right.png");
    }
  }

  private void capture(String name) throws Exception {
    Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);
    File dir =
        new File(
            InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getExternalFilesDir(null),
            "previews");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
    runShell("mkdir -p /sdcard/Download/gandroidtools-ui-previews");
    runShell(
        "cp "
            + new File(dir, name).getAbsolutePath()
            + " /sdcard/Download/gandroidtools-ui-previews/"
            + name);
  }

  private void runShell(String command) throws Exception {
    try (android.os.ParcelFileDescriptor fd =
            InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(command);
        android.os.ParcelFileDescriptor.AutoCloseInputStream in =
            new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
      byte[] bytes = new byte[1024];
      while (in.read(bytes) != -1) {}
    }
  }

  private void clickRow(View view) {
    while (!view.isClickable() && view.getParent() instanceof View parent) view = parent;
    assertTrue(view.performClick());
  }

  private TextView find(View view, String text) {
    if (view instanceof TextView label && label.getText().toString().equals(text)) return label;
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        TextView found = find(group.getChildAt(i), text);
        if (found != null) return found;
      }
    return null;
  }
}
