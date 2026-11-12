package io.github.z2bguoguo.gandroidtools;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.function.Consumer;

public final class SettingsUi {
  public static final int GALLERY_PERMISSION = 84;
  private final Activity activity;
  private final Consumer<View> show;
  private final Runnable rebuild;
  private final Config config = ToolApp.app.config;
  private boolean previewLeft = true;

  public SettingsUi(Activity activity, Consumer<View> show, Runnable rebuild) {
    this.activity = activity;
    this.show = show;
    this.rebuild = rebuild;
  }

  public void open() {
    LinearLayout root = screen(ToolApp.t("设置", "Settings"));
    root.addView(
        Ui.body(
            activity,
            ToolApp.t(
                "按类别调整。点击选项左侧的 ⓘ 查看详细说明与示例。",
                "Browse categories. Tap ⓘ beside an option for details and examples.")));
    String[][] groups = {
      {
        "appearance",
        "语言与外观",
        "Language and appearance",
        "中文、英文与系统语言",
        "Chinese, English and system language"
      },
      {
        "floating",
        "悬浮球与手势",
        "Floating button and gestures",
        "半圆功能球、双击、位置与贴边",
        "Semicircle slots, double tap, movement and docking"
      },
      {
        "capture",
        "截图与相册",
        "Capture and gallery",
        "采集、拼接、控件、图片与自动保存",
        "Capture, stitching, controls, format and auto-save"
      },
      {
        "search",
        "查找与高亮",
        "Search and highlight",
        "文字匹配、滚动范围和标记时间",
        "Text matching, scroll limits and highlight timing"
      },
      {
        "notebook",
        "信息本与输入",
        "Notebook and input",
        "等待输入焦点与隐私信息使用",
        "Deferred input and private information"
      },
      {
        "automation",
        "宏与无障碍",
        "Macros and accessibility",
        "脚本时限、文本录制和保持检查",
        "Script limits, text recording and keep-alive"
      },
      {"logs", "终端日志", "Terminal logs", "保留行数与自动滚动", "History limits and follow mode"},
      {
        "permissions",
        "权限与系统入口",
        "Permissions and system settings",
        "悬浮窗、无障碍与相册授权",
        "Overlay, accessibility and gallery access"
      }
    };
    for (String[] g : groups) {
      LinearLayout card = Ui.card(root);
      Ui.entry(card, ToolApp.t(g[1], g[2]), ToolApp.t(g[3], g[4]), () -> category(g[0]));
    }
    display(root);
  }

  private LinearLayout screen(String title) {
    LinearLayout root = Ui.column(activity);
    root.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 22));
    root.addView(Ui.title(activity, title));
    return root;
  }

  private void display(LinearLayout root) {
    show.accept(Ui.scroll(activity, root));
  }

  public void category(String id) {
    String title =
        switch (id) {
          case "appearance" -> ToolApp.t("语言与外观", "Language and appearance");
          case "floating" -> ToolApp.t("悬浮球与手势", "Floating button and gestures");
          case "capture" -> ToolApp.t("截图与相册", "Capture and gallery");
          case "search" -> ToolApp.t("查找与高亮", "Search and highlight");
          case "notebook" -> ToolApp.t("信息本与输入", "Notebook and input");
          case "automation" -> ToolApp.t("宏与无障碍", "Macros and accessibility");
          case "logs" -> ToolApp.t("终端日志", "Terminal logs");
          default -> ToolApp.t("权限与系统入口", "Permissions and system settings");
        };
    LinearLayout root = screen(title);
    Ui.button(root, ToolApp.t("‹ 所有设置", "‹ All settings"), this::open);
    switch (id) {
      case "appearance" -> {
        LinearLayout card = Ui.card(root);
        Ui.helpHeading(
            card, ToolApp.t("界面语言", "Interface language"), SettingsHelp.text("language"));
        Ui.helpButton(
            card,
            ToolApp.t("切换到 English", "切换到中文"),
            SettingsHelp.text("language"),
            () -> {
              config.set("language", config.english() ? "zh" : "en");
              rebuild.run();
            });
        Ui.helpButton(
            card,
            ToolApp.t("跟随系统语言", "Use system language"),
            SettingsHelp.text("language"),
            () -> {
              config.set("language", "system");
              rebuild.run();
            });
      }
      case "floating" -> {
        editor(root);
        options(
            root, List.of("collapse", "ballSize", "opacity", "doubleTapWindow", "longPressDelay"));
        Ui.helpButton(
            Ui.card(root),
            ToolApp.t("应用悬浮布局", "Apply floating layout"),
            SettingsHelp.text("applyLayout"),
            () -> {
              if (OverlayService.instance != null) OverlayService.start(activity, "refresh");
              else
                Ui.toast(
                    activity,
                    ToolApp.t("已保存，下次启动悬浮工具时生效", "Saved; applies when floating tools start"));
            });
      }
      case "capture" -> {
        LinearLayout card = Ui.card(root);
        toggle(
            card,
            "saveGallery",
            ToolApp.t("长截图自动保存到相册", "Auto-save long captures to gallery"),
            config.flag("saveGallery", true) && GallerySaver.permitted(activity),
            value -> {
              if (value && !GallerySaver.permitted(activity)) {
                config.set("saveGallery", false);
                activity.requestPermissions(
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, GALLERY_PERMISSION);
                category("capture");
              } else config.set("saveGallery", value);
            });
        toggle(
            card,
            "jpeg",
            ToolApp.t("使用 JPEG 格式", "Use JPEG format"),
            config.flag("jpeg", false),
            value -> config.set("jpeg", value));
        toggle(
            card,
            "allWindows",
            ToolApp.t("页面包包含所有窗口", "Include all windows in page archives"),
            config.flag("allWindows", false),
            value -> config.set("allWindows", value));
        Ui.helpHeading(
            card, ToolApp.t("默认滚动模式", "Default scrolling mode"), SettingsHelp.text("captureMode"));
        android.widget.Spinner modes = new android.widget.Spinner(activity);
        List<String> ids = List.of("page", "smooth", "manual");
        modes.setAdapter(
            new android.widget.ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                List.of(
                    ToolApp.t("跳屏", "Page"),
                    ToolApp.t("缓慢滑动", "Smooth"),
                    ToolApp.t("手动", "Manual"))));
        modes.setSelection(Math.max(0, ids.indexOf(config.text("captureMode", "page"))));
        modes.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
              @Override
              public void onItemSelected(
                  android.widget.AdapterView<?> p, View v, int position, long itemId) {
                config.set("captureMode", ids.get(position));
              }

              @Override
              public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
        card.addView(modes);
        options(
            root,
            List.of(
                "screenshotDelay",
                "settle",
                "duration",
                "overlap",
                "maxFrames",
                "maxHeight",
                "topCrop",
                "bottomCrop",
                "jpegQuality"));
      }
      case "search" -> {
        LinearLayout card = Ui.card(root);
        toggle(
            card,
            "caseSensitive",
            ToolApp.t("区分大小写", "Case-sensitive search"),
            config.flag("caseSensitive", false),
            value -> config.set("caseSensitive", value));
        options(root, List.of("highlight", "searchPages"));
      }
      case "notebook" -> options(root, List.of("inputWait"));
      case "automation" -> {
        LinearLayout card = Ui.card(root);
        toggle(
            card,
            "recordText",
            ToolApp.t("录制非密码字段文本", "Record non-password field text"),
            config.flag("recordText", false),
            value -> config.set("recordText", value));
        options(root, List.of("macroTimeout", "watchdog"));
      }
      case "logs" -> {
        LinearLayout card = Ui.card(root);
        toggle(
            card,
            "logFollow",
            ToolApp.t("日志自动滚动", "Follow terminal logs"),
            config.flag("logFollow", true),
            value -> config.set("logFollow", value));
        options(root, List.of("logLines"));
      }
      default -> {
        LinearLayout card = Ui.card(root);
        Ui.helpButton(
            card,
            ToolApp.t("系统悬浮窗权限", "System overlay permission"),
            SettingsHelp.text("overlayPermission"),
            () ->
                activity.startActivity(
                    new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + activity.getPackageName()))));
        Ui.helpButton(
            card,
            ToolApp.t("系统无障碍设置", "System accessibility settings"),
            SettingsHelp.text("accessibilityPermission"),
            () -> activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Ui.helpButton(
            card,
            ToolApp.t("检查相册保存权限", "Check gallery save permission"),
            SettingsHelp.text("saveGallery"),
            () -> {
              if (GallerySaver.permitted(activity))
                Ui.toast(
                    activity,
                    ToolApp.t(
                        "当前系统允许保存自己的截图到相册",
                        "This system permits saving the app's images to the gallery"));
              else
                activity.requestPermissions(
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, GALLERY_PERMISSION);
            });
      }
    }
    display(root);
  }

  private void options(LinearLayout root, List<String> keys) {
    LinearLayout card = Ui.card(root);
    for (String key : keys)
      for (Config.Option option : Config.OPTIONS)
        if (option.key().equals(key)) Ui.slider(card, option);
  }

  private void toggle(
      LinearLayout parent, String key, String title, boolean initial, Consumer<Boolean> change) {
    Ui.toggle(parent, title, SettingsHelp.text(key), initial, change);
  }

  private void editor(LinearLayout root) {
    LinearLayout card = Ui.card(root);
    Ui.helpHeading(
        card, ToolApp.t("半圆功能球布局", "Semicircle action layout"), SettingsHelp.text("radial"));
    card.addView(
        Ui.body(
            activity,
            ToolApp.t(
                "点击外球设置对应位置，中心球设置双击动作。",
                "Tap an outer ball to assign its position; tap the center for double tap.")));
    LinearLayout sides = Ui.row(activity);
    Ui.button(
        sides,
        ToolApp.t("预览左侧", "Preview left"),
        () -> {
          previewLeft = true;
          category("floating");
        });
    Ui.button(
        sides,
        ToolApp.t("预览右侧", "Preview right"),
        () -> {
          previewLeft = false;
          category("floating");
        });
    card.addView(sides);
    SemiCircleLayout graph = new SemiCircleLayout(activity, previewLeft);
    List<String> slots = Actions.slots();
    for (int i = 0; i < 5; i++) {
      int slot = i;
      TextView ball = Actions.ball(activity, slots.get(i), Actions.position(i, previewLeft));
      ball.setOnClickListener(
          v ->
              pick(
                  Actions.position(slot, previewLeft),
                  slots.get(slot),
                  action -> {
                    config.set("radialSlots", RadialBindings.assign(Actions.slots(), slot, action));
                    category("floating");
                  }));
      graph.addView(ball);
    }
    TextView center =
        Actions.ball(
            activity,
            config.text("doubleTapAction", "screenshot"),
            ToolApp.t("中心 · 双击", "Center · double tap"));
    center.setText(ToolApp.t("双击\n", "Double\n") + center.getText());
    center.setBackground(Ui.round(Ui.ACCENT, Ui.dp(activity, 100)));
    center.setTextColor(android.graphics.Color.WHITE);
    center.setOnClickListener(
        v ->
            pick(
                ToolApp.t("中心球 · 双击动作", "Center · double-tap action"),
                config.text("doubleTapAction", "screenshot"),
                action -> {
                  config.set("doubleTapAction", action);
                  category("floating");
                }));
    graph.addView(center);
    card.addView(graph, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 340)));
    Ui.helpButton(
        card,
        ToolApp.t("恢复默认功能球", "Reset action balls"),
        SettingsHelp.text("resetRadial"),
        () -> {
          config.set("radialSlots", RadialBindings.DEFAULT);
          config.set("doubleTapAction", "screenshot");
          category("floating");
        });
  }

  private void pick(String position, String current, Consumer<String> choose) {
    LinearLayout root = Ui.column(activity);
    androidx.appcompat.app.AlertDialog dialog =
        Ui.dialog(activity)
            .setTitle(position)
            .setView(Ui.scroll(activity, root))
            .setNegativeButton(ToolApp.t("取消", "Cancel"), null)
            .create();
    List<Actions.Action> all = new java.util.ArrayList<>();
    all.add(new Actions.Action("none", "不执行动作", "No action"));
    all.addAll(Actions.ALL);
    for (Actions.Action action : all) {
      String marker = action.id().equals(current) ? "✓ " : "";
      Ui.entry(
          root,
          marker + action.label(),
          SettingsHelp.action(action.id()),
          () -> {
            choose.accept(action.id());
            dialog.dismiss();
          });
    }
    dialog.show();
  }
}
