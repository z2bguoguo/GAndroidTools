package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.IntConsumer;

/** A local radial delay picker; no device state changes until a sector is selected. */
public final class DelayWheel extends View {
  private final Paint paint = new Paint(3);
  private final RectF oval = new RectF();
  private final int[] seconds = {0, 1, 3, 5, 10, -1};
  private final IntConsumer choose;
  private int selected = -1;

  public DelayWheel(Context context, IntConsumer choose) {
    super(context);
    this.choose = choose;
    setContentDescription(
        ToolApp.t(
            "截图延迟转盘，选择 0、1、3、5、10 秒或自定义",
            "Screenshot delay wheel: 0, 1, 3, 5, 10 seconds or custom"));
    setFocusable(true);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - 8;
    oval.set(cx - r, cy - r, cx + r, cy + r);
    for (int i = 0; i < seconds.length; i++) {
      paint.setColor(i == selected ? Ui.ACCENT : i % 2 == 0 ? 0xffdceee6 : 0xffe5eef7);
      canvas.drawArc(oval, -90 + i * 60, 58, true, paint);
      double angle = Math.toRadians(-60 + i * 60);
      paint.setColor(i == selected ? Color.WHITE : Ui.INK);
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTextSize(Ui.dp(getContext(), 17));
      String label = seconds[i] < 0 ? ToolApp.t("自定义", "Custom") : seconds[i] + " s";
      canvas.drawText(
          label,
          cx + (float) Math.cos(angle) * r * .68f,
          cy + (float) Math.sin(angle) * r * .68f + 6,
          paint);
    }
    paint.setColor(Color.WHITE);
    canvas.drawCircle(cx, cy, r * .32f, paint);
    paint.setColor(Ui.ACCENT);
    paint.setTextSize(Ui.dp(getContext(), 15));
    paint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(ToolApp.t("延迟", "Delay"), cx, cy + 6, paint);
  }

  private int sector(float x, float y) {
    double dx = x - getWidth() / 2d, dy = y - getHeight() / 2d;
    double r = Math.min(getWidth(), getHeight()) / 2d;
    if (Math.hypot(dx, dy) < r * .32 || Math.hypot(dx, dy) > r) return -1;
    double angle = (Math.toDegrees(Math.atan2(dy, dx)) + 450) % 360;
    return Math.min(5, (int) (angle / 60));
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    selected = sector(event.getX(), event.getY());
    invalidate();
    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
      performClick();
      if (selected >= 0) choose.accept(seconds[selected]);
      selected = -1;
      invalidate();
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
