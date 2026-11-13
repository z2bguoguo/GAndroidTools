// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

final class ControlPreview extends View {
  private final Bitmap image;
  private final List<TreeNode> nodes;
  private final IntPredicate checked;
  private final boolean masks;
  private final RectF frame = new RectF();
  private final Paint paint = new Paint(3);
  private final GestureDetector gestures;
  boolean outlines = true;

  ControlPreview(
      Context context,
      Bitmap image,
      List<TreeNode> nodes,
      boolean masks,
      IntPredicate checked,
      ControlPicker.Change change,
      Consumer<List<Integer>> nearby) {
    super(context);
    this.image = image;
    this.nodes = nodes;
    this.masks = masks;
    this.checked = checked;
    setContentDescription(
        ToolApp.t("控件轮廓预览；长按选择附近控件", "Control outlines; hold to select nearby controls"));
    gestures =
        new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(MotionEvent event) {
                return true;
              }

              @Override
              public boolean onSingleTapUp(MotionEvent event) {
                performClick();
                List<Integer> hits = hit(event.getX(), event.getY(), 0);
                if (!hits.isEmpty()) {
                  int i = hits.get(0);
                  change.set(i, !checked.test(i));
                  invalidate();
                }
                return true;
              }

              @Override
              public void onLongPress(MotionEvent event) {
                List<Integer> hits = hit(event.getX(), event.getY(), Ui.dp(context, 16));
                if (!hits.isEmpty()) nearby.accept(hits);
              }
            });
  }

  private List<Integer> hit(float x, float y, int radius) {
    ArrayList<Integer> result = new ArrayList<>();
    if (!frame.contains(x, y) || image.isRecycled()) return result;
    float scale = frame.width() / image.getWidth();
    float sx = (x - frame.left) / scale, sy = (y - frame.top) / scale, margin = radius / scale;
    for (int i = 0; i < nodes.size(); i++) {
      Rect b = nodes.get(i).bounds;
      if (!b.isEmpty()
          && b.left <= sx + margin
          && b.right >= sx - margin
          && b.top <= sy + margin
          && b.bottom >= sy - margin) result.add(i);
    }
    result.sort(
        Comparator.comparingLong(
            i -> (long) nodes.get(i).bounds.width() * nodes.get(i).bounds.height()));
    return result;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (image.isRecycled()) return;
    int tile = Ui.dp(getContext(), 14);
    paint.setStyle(Paint.Style.FILL);
    for (int y = 0; y < getHeight(); y += tile)
      for (int x = 0; x < getWidth(); x += tile) {
        paint.setColor(((x / tile + y / tile) & 1) == 0 ? 0xffeef2f0 : 0xffdfe7e3);
        canvas.drawRect(x, y, x + tile, y + tile, paint);
      }
    float scale =
        Math.min((float) getWidth() / image.getWidth(), (float) getHeight() / image.getHeight());
    float left = (getWidth() - image.getWidth() * scale) / 2,
        top = (getHeight() - image.getHeight() * scale) / 2;
    frame.set(left, top, left + image.getWidth() * scale, top + image.getHeight() * scale);
    canvas.save();
    canvas.translate(left, top);
    canvas.scale(scale, scale);
    drawImage(canvas, image, nodes, masks ? checked : i -> true);
    if (outlines) {
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(1.5f / scale);
      for (int i = 0; i < nodes.size(); i++) {
        paint.setColor(checked.test(i) ? Ui.ACCENT : 0x88727f79);
        canvas.drawRect(nodes.get(i).bounds, paint);
      }
    }
    canvas.restore();
  }

  static void drawImage(Canvas canvas, Bitmap image, List<TreeNode> nodes, IntPredicate visible) {
    int layer = canvas.saveLayer(0, 0, image.getWidth(), image.getHeight(), null);
    canvas.drawBitmap(image, 0, 0, null);
    Paint clear = new Paint();
    clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    for (int i = 0; i < nodes.size(); i++)
      if (!visible.test(i)) canvas.drawRect(nodes.get(i).bounds, clear);
    canvas.restoreToCount(layer);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return gestures.onTouchEvent(event);
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
