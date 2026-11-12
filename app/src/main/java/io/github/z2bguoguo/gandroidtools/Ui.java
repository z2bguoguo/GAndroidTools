package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public final class Ui {
  public static final int INK = Color.rgb(34, 57, 53),
      ACCENT = Color.rgb(64, 127, 117),
      BG = Color.rgb(244, 247, 245),
      MUTED = Color.rgb(105, 123, 117);

  private Ui() {}

  public static int dp(Context c, float v) {
    return Math.round(c.getResources().getDisplayMetrics().density * v);
  }

  public static LinearLayout column(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  public static LinearLayout row(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(android.view.Gravity.CENTER_VERTICAL);
    return v;
  }

  public static TextView text(Context c, String value, int size, int color) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 6));
    return t;
  }

  public static TextView title(Context c, String value) {
    TextView t = text(c, value, 26, INK);
    t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  public static TextView body(Context c, String value) {
    return text(c, value, 14, MUTED);
  }

  public static LinearLayout card(LinearLayout parent) {
    Context c = parent.getContext();
    MaterialCardView card = new MaterialCardView(c);
    card.setRadius(dp(c, 24));
    card.setCardElevation(0);
    card.setStrokeWidth(dp(c, 1));
    card.setStrokeColor(Color.rgb(223, 232, 226));
    card.setCardBackgroundColor(Color.WHITE);
    LinearLayout inside = column(c);
    inside.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
    card.addView(inside);
    LinearLayout.LayoutParams p =
        parent.getOrientation() == LinearLayout.HORIZONTAL
            ? new LinearLayout.LayoutParams(0, -2, 1)
            : new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(c, 6), 0, dp(c, 6));
    parent.addView(card, p);
    return inside;
  }

  public static MaterialButton button(LinearLayout parent, String label, Runnable click) {
    Context c = parent.getContext();
    MaterialButton b = new MaterialButton(c);
    b.setText(label);
    b.setAllCaps(false);
    b.setCornerRadius(dp(c, 18));
    b.setMinHeight(dp(c, 48));
    b.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
    b.setOnClickListener(v -> click.run());
    parent.addView(
        b,
        parent.getOrientation() == LinearLayout.HORIZONTAL
            ? new LinearLayout.LayoutParams(0, -2, 1)
            : new LinearLayout.LayoutParams(-1, -2));
    return b;
  }

  public static void entry(LinearLayout parent, String title, String subtitle, Runnable click) {
    Context c = parent.getContext();
    LinearLayout row = row(c);
    row.setPadding(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 6));
    LinearLayout copy = column(c);
    copy.addView(text(c, title, 17, INK));
    copy.addView(body(c, subtitle));
    row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(text(c, "›", 26, ACCENT));
    row.setClickable(true);
    row.setFocusable(true);
    row.setBackgroundResource(android.R.drawable.list_selector_background);
    row.setOnClickListener(v -> click.run());
    parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
  }

  public static EditText input(LinearLayout parent, String hint, String value, boolean multiline) {
    Context c = parent.getContext();
    EditText e = new EditText(c);
    e.setTextColor(INK);
    e.setHintTextColor(MUTED);
    e.setTextSize(15);
    e.setHint(hint);
    e.setText(value);
    e.setSingleLine(!multiline);
    if (multiline) {
      e.setMinLines(4);
      e.setGravity(android.view.Gravity.TOP);
    }
    parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
    return e;
  }

  public static void toggle(
      LinearLayout parent,
      String title,
      String hint,
      boolean initial,
      java.util.function.Consumer<Boolean> change) {
    Context c = parent.getContext();
    LinearLayout r = row(c);
    LinearLayout copy = column(c);
    copy.addView(text(c, title, 16, INK));
    if (!hint.isBlank()) copy.addView(body(c, hint));
    r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    SwitchMaterial s = new SwitchMaterial(c);
    s.setUseMaterialThemeColors(true);
    s.setContentDescription(title);
    s.setChecked(initial);
    s.setOnCheckedChangeListener((b, v) -> change.accept(v));
    r.addView(s);
    parent.addView(r);
    r.setOnClickListener(v -> s.toggle());
  }

  public static void slider(LinearLayout parent, Config.Option option) {
    Context c = parent.getContext();
    Config config = ToolApp.app.config;
    String name = config.english() ? option.en() : option.zh();
    TextView title = text(c, name + " · " + config.number(option.key()), 15, INK);
    parent.addView(title);
    Slider s = new Slider(c);
    s.setValueFrom(option.min());
    s.setValueTo(option.max());
    s.setStepSize(1);
    s.setValue(config.number(option.key()));
    s.setContentDescription(name);
    s.addOnChangeListener(
        (v, n, user) -> {
          title.setText(name + " · " + (int) n);
          if (user) config.set(option.key(), (int) n);
        });
    parent.addView(s, new LinearLayout.LayoutParams(-1, -2));
    parent.addView(body(c, config.english() ? option.hintEn() : option.hintZh()));
  }

  public static ScrollView scroll(Context c, View view) {
    ScrollView s = new ScrollView(c);
    s.setFillViewport(false);
    s.addView(view, new ViewGroup.LayoutParams(-1, -2));
    return s;
  }

  public static GradientDrawable round(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  public static com.google.android.material.dialog.MaterialAlertDialogBuilder dialog(Context c) {
    return new com.google.android.material.dialog.MaterialAlertDialogBuilder(c) {
      @Override
      public androidx.appcompat.app.AlertDialog create() {
        androidx.appcompat.app.AlertDialog d = super.create();
        if (!(c instanceof android.app.Activity))
          d.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        return d;
      }
    };
  }

  public static void toast(Context c, String message) {
    ToolApp.app.main.post(
        () ->
            android.widget.Toast.makeText(
                    c,
                    message == null
                        ? ToolApp.t("操作失败，请查看日志", "Operation failed; inspect logs")
                        : message,
                    android.widget.Toast.LENGTH_LONG)
                .show());
  }
}
