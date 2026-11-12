package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

/** The same geometry renders the settings editor and the live overlay. */
public final class SemiCircleLayout extends ViewGroup {
  private final boolean left;
  private final Paint paint = new Paint(3);
  private final RectF arc = new RectF();
  private float cx, cy, radius;
  private int diameter;

  public SemiCircleLayout(Context c, boolean left) {
    super(c);
    this.left = left;
    setWillNotDraw(false);
    setClipChildren(false);
  }

  @Override
  protected void onMeasure(int w, int h) {
    int width = MeasureSpec.getSize(w), height = MeasureSpec.getSize(h);
    if (MeasureSpec.getMode(h) != MeasureSpec.EXACTLY) height = Ui.dp(getContext(), 340);
    setMeasuredDimension(width, height);
    diameter =
        Math.min(
            Ui.dp(getContext(), ToolApp.app.config.number("ballSize")),
            Math.max(Ui.dp(getContext(), 36), (height - Ui.dp(getContext(), 16)) / 5));
    for (int i = 0; i < getChildCount(); i++)
      getChildAt(i)
          .measure(
              MeasureSpec.makeMeasureSpec(diameter, MeasureSpec.EXACTLY),
              MeasureSpec.makeMeasureSpec(diameter, MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    float pad = Ui.dp(getContext(), 8);
    radius =
        Math.max(
            1, Math.min((getHeight() - diameter - 2 * pad) / 2f, getWidth() - diameter - 2 * pad));
    cx = left ? diameter / 2f : getWidth() - diameter / 2f;
    cy = getHeight() / 2f;
    for (int i = 0; i < getChildCount(); i++) {
      double angle = Math.toRadians(-90 + i * 45);
      float x = i == 5 ? cx : cx + (left ? 1 : -1) * (float) Math.cos(angle) * radius,
          y = i == 5 ? cy : cy + (float) Math.sin(angle) * radius;
      View v = getChildAt(i);
      int x0 = Math.round(x - diameter / 2f), y0 = Math.round(y - diameter / 2f);
      v.layout(x0, y0, x0 + diameter, y0 + diameter);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Ui.dp(getContext(), 2));
    paint.setColor(0xffc5ddd3);
    arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
    canvas.drawArc(arc, left ? -90 : 90, 180, false, paint);
    paint.setStyle(Paint.Style.FILL);
  }

  public int direction(float screenX, float screenY) {
    int[] origin = new int[2];
    getLocationOnScreen(origin);
    return RadialBindings.direction(
        screenX - origin[0] - cx, screenY - origin[1] - cy, radius, left);
  }
}
