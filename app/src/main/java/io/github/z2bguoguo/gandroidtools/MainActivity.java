package io.github.z2bguoguo.gandroidtools;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import java.io.File;
import java.util.Arrays;
import org.json.JSONObject;

public final class MainActivity extends FragmentActivity {
  private LinearLayout shell, content, nav;
  private String page = "home";
  private EditText scriptEditor, scriptName;
  private final ToolApp app = ToolApp.app;
  private Runnable logListener;
  private final android.os.Handler handler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private boolean logPaused;
  private String logQuery = "", logLevel = "ALL";
  private boolean resumed;
  private final Runnable pulse =
      new Runnable() {
        @Override
        public void run() {
          if (resumed) {
            if (page.equals("home")) home();
            handler.postDelayed(this, 2000);
          }
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (saved != null) page = saved.getString("page", "home");
    else if (getIntent().hasExtra("page")) page = getIntent().getStringExtra("page");
    build();
    if (android.os.Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 5);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (intent.hasExtra("page")) navigate(intent.getStringExtra("page"));
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putString("page", page);
    super.onSaveInstanceState(out);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    handler.removeCallbacks(pulse);
    handler.postDelayed(pulse, 1000);
  }

  @Override
  protected void onPause() {
    resumed = false;
    handler.removeCallbacks(pulse);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (logListener != null) app.log.unlisten(logListener);
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  private void build() {
    shell = Ui.column(this);
    shell.setBackgroundColor(Ui.BG);
    shell.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
    content = Ui.column(this);
    shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    nav = Ui.row(this);
    nav.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
    shell.addView(nav);
    setContentView(shell);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        shell,
        (v, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.ime());
          v.setPadding(
              Ui.dp(this, 18) + bars.left, bars.top, Ui.dp(this, 18) + bars.right, bars.bottom);
          return insets;
        });
    navigate(page);
  }

  private void navigate(String target) {
    if (logListener != null) {
      app.log.unlisten(logListener);
      logListener = null;
    }
    page = target;
    content.removeAllViews();
    nav.removeAllViews();
    String[] ids = {"home", "tools", "notebook", "macros", "settings"};
    String[] zh = {"首页", "工具", "信息本", "宏", "设置"},
        en = {"Home", "Tools", "Notes", "Macros", "Settings"};
    String[] icons = {"◈", "▦", "▤", "▷", "⚙"};
    for (int i = 0; i < ids.length; i++) {
      String id = ids[i];
      TextView tab =
          Ui.text(
              this,
              icons[i] + "\n" + ToolApp.t(zh[i], en[i]),
              12,
              page.equals(id) ? Ui.ACCENT : Ui.MUTED);
      tab.setGravity(android.view.Gravity.CENTER);
      tab.setMinHeight(Ui.dp(this, 56));
      if (page.equals(id)) tab.setBackground(Ui.round(Color.rgb(221, 238, 231), Ui.dp(this, 18)));
      tab.setOnClickListener(v -> navigate(id));
      nav.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
    }
    switch (page) {
      case "home" -> home();
      case "tools" -> tools();
      case "notebook" -> new InfoUi(this, this::replaceContent, this::input).open();
      case "macros" -> macros();
      case "settings" -> settings();
      case "logs" -> logs();
      case "exports" -> exports();
      case "accessibility" -> accessibility();
      default -> home();
    }
  }

  private void replaceContent(View view) {
    content.removeAllViews();
    content.addView(view, new LinearLayout.LayoutParams(-1, -1));
  }

  private LinearLayout screen(String title, String subtitle) {
    LinearLayout root = Ui.column(this);
    root.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 20));
    root.addView(Ui.title(this, title));
    if (!subtitle.isBlank()) root.addView(Ui.body(this, subtitle));
    replaceContent(Ui.scroll(this, root));
    return root;
  }

  private void home() {
    if (!page.equals("home")) return;
    LinearLayout root =
        screen(
            "GAndroidTools",
            ToolApp.t("让日常操作，轻一点。", "Make everyday interactions a little lighter."));
    LinearLayout hero = Ui.card(root);
    hero.setBackground(Ui.round(Color.rgb(221, 238, 231), Ui.dp(this, 22)));
    hero.addView(
        Ui.text(this, "✦  " + ToolApp.t("你的屏幕工具箱", "Your on-screen toolkit"), 22, Ui.ACCENT));
    hero.addView(
        Ui.body(
            this,
            ToolApp.t("截图、查找、自动化与信息，一触即达。", "Capture, find, automate and fill — within reach.")));
    LinearLayout status = Ui.card(root);
    status.addView(
        Ui.text(
            this,
            "Shizuku · "
                + (app.bridge.ready()
                    ? ToolApp.t("已连接", "Connected")
                    : app.bridge.authorized()
                        ? ToolApp.t("连接中", "Connecting")
                        : ToolApp.t("等待授权", "Authorization required")),
            17,
            Ui.INK));
    status.addView(
        Ui.body(
            this,
            ToolApp.t("悬浮工具：", "Floating tools: ")
                + (OverlayService.instance == null
                    ? ToolApp.t("未开启", "Off")
                    : ToolApp.t("运行中", "Active"))));
    Ui.button(
        status, ToolApp.t("连接 / 授权 Shizuku", "Connect / authorize Shizuku"), app.bridge::authorize);
    Ui.button(
        status,
        OverlayService.instance == null
            ? ToolApp.t("开启悬浮工具", "Start floating tools")
            : ToolApp.t("关闭悬浮工具", "Stop floating tools"),
        () -> {
          if (OverlayService.instance == null) OverlayService.start(this, "show");
          else stopService(new Intent(this, OverlayService.class));
          home();
        });
    LinearLayout entries = Ui.card(root);
    Ui.entry(
        entries,
        ToolApp.t("终端日志", "Terminal logs"),
        ToolApp.t("实时筛选、自动滚动和导出", "Live filters, follow mode and export"),
        () -> navigate("logs"));
    Ui.entry(
        entries,
        ToolApp.t("导出文件", "Exports"),
        ToolApp.t("长截图、页面快照与日志", "Long captures, page archives and logs"),
        () -> navigate("exports"));
    Ui.entry(
        entries,
        ToolApp.t("使用文档", "Documentation"),
        ToolApp.t("功能指南与兼容性说明", "Guides and compatibility"),
        () -> link("https://z2bguoguo.github.io/GAndroidTools/"));
    root.addView(Ui.body(this, BuildConfig.VERSION_NAME + " · Android 8.0+ · Shizuku"));
  }

  private void tools() {
    LinearLayout root =
        screen(
            ToolApp.t("工具", "Tools"),
            ToolApp.t(
                "先切到目标应用，再通过悬浮菜单操作。", "Switch to the target app, then use the floating menu."));
    String[][] items = {
      {
        "screenshot",
        "截图",
        "Screenshot",
        "冻结画面和控件树，选择显示区域后保存",
        "Freeze image and controls, adjust visibility, then save"
      },
      {
        "capture",
        "超级长截图",
        "Super long capture",
        "跳屏 / 滑动 / 手动采集，控件选择与遮罩",
        "Page jumps, smooth or manual capture, control regions and masks"
      },
      {
        "snapshot",
        "页面快照",
        "Page snapshot",
        "截图与可访问控件信息，导出 .gatpage",
        "Screenshot and accessible controls in a .gatpage archive"
      },
      {
        "find",
        "超级查找",
        "Super find",
        "跨滚动页面查找文字并短暂高亮",
        "Find text across scrolls and highlight matches"
      },
      {"tasks", "超级后台", "Super background", "隐藏屏幕与任务恢复卡片", "Hidden display and task restore cards"},
      {
        "services",
        "无障碍管理",
        "Accessibility",
        "控制服务开关与保持状态",
        "Enable services and keep selected services enabled"
      }
    };
    for (String[] item : items) {
      LinearLayout card = Ui.card(root);
      Ui.entry(
          card,
          ToolApp.t(item[1], item[2]),
          ToolApp.t(item[3], item[4]),
          () -> {
            if (item[0].equals("services")) navigate("accessibility");
            else OverlayService.start(this, item[0]);
          });
    }
    Ui.button(
        root,
        ToolApp.t("选择运行中任务并移入隐藏屏幕", "Choose a running task to park"),
        () -> OverlayService.start(this, "choosePark"));
  }

  private void input(String text) {
    OverlayService.start(this, "show");
    handler.postDelayed(
        () -> {
          if (OverlayService.instance != null) {
            OverlayService.instance.queueInput(text);
            moveTaskToBack(true);
          } else
            Ui.toast(this, ToolApp.t("请开启悬浮窗权限后重试", "Enable overlay permission and try again"));
        },
        300);
  }

  private void settings() {
    new SettingsUi(this, this::replaceContent, this::build).open();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == SettingsUi.GALLERY_PERMISSION) {
      boolean granted =
          results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
      app.config.set("saveGallery", granted);
      Ui.toast(
          this,
          granted
              ? ToolApp.t("已允许，长截图将自动保存到相册", "Allowed; long captures will auto-save to gallery")
              : ToolApp.t("未授权；仍会保留应用内导出", "Permission denied; internal exports remain available"));
      if (page.equals("settings"))
        new SettingsUi(this, this::replaceContent, this::build).category("capture");
    }
  }

  private void macros() {
    LinearLayout root =
        screen(
            ToolApp.t("超级宏", "Super macros"),
            ToolApp.t(
                "录制控件操作，或使用 JavaScript 编写自动化。",
                "Record control interactions or write automation in JavaScript."));
    LinearLayout card = Ui.card(root);
    Ui.button(
        card,
        app.macros.recording()
            ? ToolApp.t("停止并保存录制", "Stop and save recording")
            : ToolApp.t("开始录制控件操作", "Record control actions"),
        () -> OverlayService.start(this, "record"));
    Ui.button(card, ToolApp.t("停止当前运行", "Stop current macro"), app.macros::stop);
    scriptName =
        Ui.input(
            card, ToolApp.t("脚本文件名（无需 .js）", "Script filename (without .js)"), "my-macro", false);
    scriptEditor =
        Ui.input(
            card,
            ToolApp.t("JavaScript 脚本", "JavaScript source"),
            "// tool API: find, click, text, scroll, tap, swipe, wait, back\n"
                + "const item = tool.find({text: 'Settings'});\n"
                + "if (item) tool.click({text: 'Settings'});\n",
            true);
    scriptEditor.setTypeface(Typeface.MONOSPACE);
    scriptEditor.setMinLines(10);
    Ui.button(
        card,
        ToolApp.t("保存脚本", "Save script"),
        () -> {
          try {
            File f = macroFile();
            FileText.write(f.toPath(), scriptEditor.getText().toString());
            Ui.toast(this, ToolApp.t("已保存", "Saved"));
          } catch (Exception e) {
            Ui.toast(this, e.getMessage());
          }
        });
    Ui.button(
        card,
        ToolApp.t("运行当前脚本", "Run current script"),
        () ->
            Ui.dialog(this)
                .setMessage(
                    ToolApp.t(
                        "脚本将操作屏幕。确认运行？", "This script will interact with the screen. Run it?"))
                .setPositiveButton(
                    ToolApp.t("运行", "Run"),
                    (d, n) -> {
                      try {
                        String source = scriptEditor.getText().toString();
                        OverlayService.start(this, "show");
                        app.macros.run(
                            source,
                            e ->
                                Ui.toast(
                                    this,
                                    e == null
                                        ? ToolApp.t("运行完成", "Completed")
                                        : ToolApp.t("宏已停止，详见日志", "Macro stopped; inspect logs")));
                        moveTaskToBack(true);
                      } catch (Exception e) {
                        Ui.toast(this, e.getMessage());
                      }
                    })
                .setNegativeButton(ToolApp.t("取消", "Cancel"), null)
                .show());
    Ui.button(
        card,
        ToolApp.t("从文件导入 JavaScript", "Import JavaScript file"),
        () ->
            startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE),
                72));
    Ui.button(
        card,
        ToolApp.t("导出当前脚本", "Export current script"),
        () -> {
          try {
            File f = Exports.file(macroFile().getName());
            FileText.write(f.toPath(), scriptEditor.getText().toString());
            Exports.share(this, f);
          } catch (Exception e) {
            Ui.toast(this, e.getMessage());
          }
        });
    LinearLayout saved = Ui.card(root);
    saved.addView(Ui.text(this, ToolApp.t("已保存脚本", "Saved scripts"), 18, Ui.INK));
    File[] files = app.macros.directory().listFiles((dir, n) -> n.endsWith(".js"));
    if (files != null)
      for (File f : files)
        Ui.entry(
            saved,
            f.getName(),
            ToolApp.t("点击加载到编辑器", "Load into editor"),
            () -> {
              try {
                scriptName.setText(f.getName().replaceFirst("\\.js$", ""));
                scriptEditor.setText(FileText.read(f.toPath()));
              } catch (Exception e) {
                Ui.toast(this, e.getMessage());
              }
            });
    Ui.button(
        saved,
        ToolApp.t("脚本 API 文档", "Script API documentation"),
        () -> link("https://z2bguoguo.github.io/GAndroidTools/guide/macros"));
  }

  private File macroFile() {
    String name = scriptName.getText().toString();
    if (!name.matches("[a-zA-Z0-9_-]{1,80}"))
      throw new IllegalArgumentException(
          ToolApp.t(
              "文件名仅支持字母、数字、下划线和短横线", "Use letters, digits, underscores or hyphens in filenames"));
    return new File(app.macros.directory(), name + ".js");
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 72 && result == RESULT_OK && data != null) {
      app.worker.execute(
          () -> {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
              if (in == null) throw new IllegalStateException("Cannot read script");
              java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
              byte[] bytes = new byte[4096];
              int n;
              while ((n = in.read(bytes)) != -1) {
                if (out.size() + n > 200000)
                  throw new IllegalArgumentException("Script exceeds 200 KB");
                out.write(bytes, 0, n);
              }
              String source =
                  new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
              runOnUiThread(
                  () -> {
                    if (scriptEditor != null) scriptEditor.setText(source);
                  });
            } catch (Exception e) {
              Ui.toast(this, e.getMessage());
            }
          });
    }
  }

  private void logs() {
    LinearLayout root = Ui.column(this);
    root.setPadding(0, 16, 0, 12);
    root.addView(Ui.title(this, ToolApp.t("终端日志", "Terminal logs")));
    EditText query =
        Ui.input(root, ToolApp.t("筛选模块或消息", "Filter module or message"), logQuery, false);
    android.widget.Spinner levels = new android.widget.Spinner(this);
    String[] severity = {"ALL", "DEBUG", "INFO", "WARN", "ERROR"};
    levels.setAdapter(
        new android.widget.ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, severity));
    levels.setSelection(Arrays.asList(severity).indexOf(logLevel));
    root.addView(levels);
    LinearLayout actions = Ui.row(this);
    Ui.button(
        actions,
        ToolApp.t("暂停 / 继续", "Pause / resume"),
        () -> {
          logPaused = !logPaused;
          if (logListener != null) logListener.run();
        });
    Ui.button(
        actions,
        ToolApp.t("导出", "Export"),
        () ->
            app.worker.execute(
                () -> {
                  try {
                    File f = Exports.file("logs-" + System.currentTimeMillis() + ".txt");
                    FileText.write(f.toPath(), app.log.export());
                    runOnUiThread(() -> Exports.share(this, f));
                  } catch (Exception e) {
                    Ui.toast(this, e.getMessage());
                  }
                }));
    root.addView(actions);
    TextView terminal = Ui.text(this, "", 12, Color.rgb(205, 226, 214));
    terminal.setTypeface(Typeface.MONOSPACE);
    terminal.setTextIsSelectable(true);
    terminal.setPadding(20, 20, 20, 20);
    terminal.setBackground(Ui.round(Color.rgb(24, 38, 35), Ui.dp(this, 20)));
    ScrollView scroll = Ui.scroll(this, terminal);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    replaceContent(root);
    logListener =
        () -> {
          if (logPaused) return;
          StringBuilder text = new StringBuilder();
          for (String line : app.log.snapshot())
            if ((logLevel.equals("ALL") || line.contains("[" + logLevel + "]"))
                && line.toLowerCase(java.util.Locale.ROOT)
                    .contains(logQuery.toLowerCase(java.util.Locale.ROOT)))
              text.append(line).append('\n');
          android.text.SpannableString colored = new android.text.SpannableString(text);
          int start = 0;
          for (String line : text.toString().split("\n")) {
            int color =
                line.contains("[ERROR]")
                    ? 0xfff2aaaa
                    : line.contains("[WARN]")
                        ? 0xffeed59e
                        : line.contains("[DEBUG]") ? 0xff95b7ac : 0xffcde2d6;
            colored.setSpan(
                new android.text.style.ForegroundColorSpan(color), start, start + line.length(), 0);
            start += line.length() + 1;
          }
          terminal.setText(colored);
          if (app.config.flag("logFollow", true))
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        };
    query.addTextChangedListener(
        new android.text.TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          @Override
          public void onTextChanged(CharSequence s, int st, int before, int count) {
            logQuery = s.toString();
            if (logListener != null) logListener.run();
          }

          @Override
          public void afterTextChanged(android.text.Editable e) {}
        });
    levels.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(
              android.widget.AdapterView<?> p, View v, int position, long id) {
            logLevel = severity[position];
            if (logListener != null) logListener.run();
          }

          @Override
          public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    app.log.listen(logListener);
    logListener.run();
  }

  private void exports() {
    LinearLayout root =
        screen(
            ToolApp.t("导出文件", "Exports"),
            ToolApp.t(
                "文件保存在应用内，可通过系统分享另存。",
                "Files stay in app storage; use system sharing to save them elsewhere."));
    File[] files = Exports.directory().listFiles();
    if (files == null || files.length == 0)
      root.addView(Ui.body(this, ToolApp.t("还没有导出文件", "No exports yet")));
    else {
      Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
      for (File f : files) {
        LinearLayout card = Ui.card(root);
        Ui.entry(
            card,
            f.getName(),
            String.format(java.util.Locale.ROOT, "%.1f KB", f.length() / 1024d),
            () -> Exports.share(this, f));
        Ui.button(
            card,
            ToolApp.t("删除", "Delete"),
            () ->
                Ui.dialog(this)
                    .setMessage(ToolApp.t("删除这个导出文件？", "Delete this export?"))
                    .setPositiveButton(
                        ToolApp.t("删除", "Delete"),
                        (d, n) -> {
                          if (!f.delete()) Ui.toast(this, ToolApp.t("删除失败", "Delete failed"));
                          exports();
                        })
                    .setNegativeButton(ToolApp.t("取消", "Cancel"), null)
                    .show());
      }
    }
  }

  private void accessibility() {
    LinearLayout root =
        screen(
            ToolApp.t("无障碍管理", "Accessibility"), ToolApp.t("正在读取服务状态…", "Reading service status…"));
    app.worker.execute(
        () -> {
          try {
            JSONObject data = app.bridge.call("services");
            runOnUiThread(
                () -> {
                  if (page.equals("accessibility"))
                    replaceContent(
                        Ui.scroll(this, ServiceUi.build(this, data, this::accessibility)));
                });
          } catch (Exception e) {
            app.log.error("accessibility", e);
            runOnUiThread(
                () -> {
                  if (page.equals("accessibility")) root.addView(Ui.body(this, e.getMessage()));
                });
          }
        });
  }

  private void link(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
    } catch (Exception e) {
      Ui.toast(this, e.getMessage());
    }
  }
}
