// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ControlEditorTest {
  @Test
  public void hierarchyIsOpaqueAndHoldingOffersAllOverlappingControls() throws Exception {
    ToolApp app =
        (ToolApp)
            InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    app.config.set("language", "zh");
    Bitmap image = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
    image.eraseColor(0xffdbeee5);
    Canvas canvas = new Canvas(image);
    Paint ink = new Paint(3);
    ink.setColor(0xff2d6552);
    ink.setTextSize(28);
    canvas.drawText("Control preview", 24, 100, ink);
    java.util.ArrayList<TreeNode> nodes = new java.util.ArrayList<>();
    String[] names = {"页面", "操作区", "继续"},
        classes =
            {"android.widget.FrameLayout", "android.widget.LinearLayout", "android.widget.Button"};
    for (int i = 0; i < 3; i++)
      nodes.add(
          new TreeNode(
              new JSONObject()
                  .put("path", i == 0 ? "0" : i == 1 ? "0.0" : "0.0.0")
                  .put("depth", i)
                  .put("class", classes[i])
                  .put("text", names[i])
                  .put(
                      "bounds",
                      new JSONArray(new int[] {i * 20, i * 20, 300 - i * 20, 300 - i * 20}))));
    boolean[] selected = {true, true, true};
    AtomicReference<ControlPreview> previewRef = new AtomicReference<>();
    CountDownLatch offered = new CountDownLatch(1);
    try (ActivityScenario<ControlProbeActivity> scenario =
        ActivityScenario.launch(ControlProbeActivity.class)) {
      scenario.onActivity(
          activity -> {
            LinearLayout root = Ui.column(activity);
            root.setBackgroundColor(Ui.BG);
            root.setPadding(
                Ui.dp(activity, 18), Ui.dp(activity, 40), Ui.dp(activity, 18), Ui.dp(activity, 18));
            root.addView(Ui.title(activity, "轮廓选择 · 长按附近多选"));
            ControlPreview preview =
                new ControlPreview(
                    activity,
                    image,
                    nodes,
                    false,
                    i -> selected[i],
                    (i, v) -> selected[i] = v,
                    hits -> {
                      assertEquals(3, hits.size());
                      LinearLayout panel = Ui.column(activity);
                      panel.setBackgroundColor(Ui.BG);
                      panel.setPadding(
                          Ui.dp(activity, 18),
                          Ui.dp(activity, 40),
                          Ui.dp(activity, 18),
                          Ui.dp(activity, 18));
                      panel.addView(Ui.title(activity, "附近控件 · 可多选"));
                      panel.addView(
                          ControlPicker.create(
                              activity, nodes, hits, i -> selected[i], (i, v) -> selected[i] = v),
                          new LinearLayout.LayoutParams(-1, -1));
                      activity.setContentView(panel);
                      offered.countDown();
                    });
            root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
            activity.setContentView(root);
            previewRef.set(preview);
          });
      awaitFrames();
      scenario.onActivity(
          a -> {
            ControlPreview preview = previewRef.get();
            long now = SystemClock.uptimeMillis();
            MotionEvent down =
                MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_DOWN,
                    preview.getWidth() / 2f,
                    preview.getHeight() / 2f,
                    0);
            preview.dispatchTouchEvent(down);
            down.recycle();
          });
      assertTrue("Nearby chooser did not open", offered.await(3, TimeUnit.SECONDS));
      awaitFrames();
      scenario.onActivity(
          a -> {
            ListView list = findList(a.getWindow().getDecorView());
            assertNotNull(list);
            assertEquals(3, list.getAdapter().getCount());
            assertEquals(
                Color.WHITE,
                ((android.graphics.drawable.ColorDrawable) list.getBackground()).getColor());
            View row = list.getChildAt(2);
            assertNotNull(row);
            row.performClick();
            assertFalse(selected[2]);
          });
      Bitmap shot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
      assertNotNull(shot);
      java.io.File target =
          new java.io.File(app.getExternalFilesDir(null), "previews/control-nearby-tree.png");
      target.getParentFile().mkdirs();
      try (java.io.FileOutputStream output = new java.io.FileOutputStream(target)) {
        shot.compress(Bitmap.CompressFormat.PNG, 100, output);
      }
      shot.recycle();
      try (android.os.ParcelFileDescriptor mkdir =
              InstrumentationRegistry.getInstrumentation()
                  .getUiAutomation()
                  .executeShellCommand("mkdir -p /sdcard/Download/gandroidtools-ui-previews");
          android.os.ParcelFileDescriptor.AutoCloseInputStream input =
              new android.os.ParcelFileDescriptor.AutoCloseInputStream(mkdir)) {
        byte[] buffer = new byte[1024];
        while (input.read(buffer) != -1) {}
      }
      try (android.os.ParcelFileDescriptor fd =
              InstrumentationRegistry.getInstrumentation()
                  .getUiAutomation()
                  .executeShellCommand(
                      "cp "
                          + target
                          + " /sdcard/Download/gandroidtools-ui-previews/control-nearby-tree.png");
          android.os.ParcelFileDescriptor.AutoCloseInputStream input =
              new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
        byte[] buffer = new byte[1024];
        while (input.read(buffer) != -1) {}
      }
    } finally {
      image.recycle();
    }
  }

  private ListView findList(View view) {
    if (view instanceof ListView list) return list;
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        ListView list = findList(group.getChildAt(i));
        if (list != null) return list;
      }
    return null;
  }

  private void awaitFrames() throws Exception {
    CountDownLatch frames = new CountDownLatch(1);
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () ->
                android.view.Choreographer.getInstance()
                    .postFrameCallback(
                        one ->
                            android.view.Choreographer.getInstance()
                                .postFrameCallback(two -> frames.countDown())));
    assertTrue(frames.await(5, TimeUnit.SECONDS));
  }
}
