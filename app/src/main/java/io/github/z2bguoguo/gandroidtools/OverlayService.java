package io.github.z2bguoguo.gandroidtools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OverlayService extends Service {
  public static volatile OverlayService instance;
  private WindowManager wm;
  private android.content.Context ui;
  private TextView bubble;
  private WindowManager.LayoutParams bubbleParams;
  private View menu, panel, toolbar, highlight;
  private final List<View> menuRows = new ArrayList<>();
  private List<Actions.Action> menuActions = List.of();
  private final android.os.Handler handler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private CaptureSession capture;
  private FrozenCapture frozen;
  private long shotGeneration;
  private final TapGate taps = new TapGate();
  private final Runnable singleTap =
      () -> {
        if (taps.consumeSingle(android.os.SystemClock.elapsedRealtime()) && bubble != null)
          bubble.performClick();
      };
  private final AtomicBoolean searching = new AtomicBoolean();
  private volatile boolean stopped;
  private int hidden;
  private long inputGeneration;
  private int watchdogTicks;
  private final Runnable dockTimer = this::dock;

  public static void start(android.content.Context c, String action) {
    if (!Settings.canDrawOverlays(c)) {
      c.startActivity(
          new Intent(
                  Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  android.net.Uri.parse("package:" + c.getPackageName()))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return;
    }
    Intent i = new Intent(c, OverlayService.class).putExtra("action", action);
    androidx.core.content.ContextCompat.startForegroundService(c, i);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    ui = new android.view.ContextThemeWrapper(this, R.style.Theme_GAndroidTools);
    wm = getSystemService(WindowManager.class);
    capture = new CaptureSession(this);
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel(
            "tools", ToolApp.t("悬浮工具运行", "Floating tools"), NotificationManager.IMPORTANCE_LOW));
    PendingIntent open =
        PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, OverlayService.class).putExtra("action", "stop"),
            PendingIntent.FLAG_IMMUTABLE);
    Notification n =
        new Notification.Builder(this, "tools")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("GAndroidTools")
            .setContentText(
                ToolApp.t("悬浮工具已开启 · 可随时停止", "Floating tools active · stop at any time"))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, ToolApp.t("停止", "Stop"), stop).build())
            .build();
    startForeground(41, n);
    createBubble();
    handler.post(watchdog);
    ToolApp.app.log.add("INFO", "overlay", "Service started");
  }

  @Override
  public int onStartCommand(Intent i, int flags, int startId) {
    if (i != null) {
      String action = i.getStringExtra("action");
      if (action != null && !action.equals("show")) handler.post(() -> perform(action));
    }
    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private WindowManager.LayoutParams params(int width, int height, boolean focus) {
    WindowManager.LayoutParams p =
        new WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | (focus ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            PixelFormat.TRANSLUCENT);
    p.gravity = Gravity.TOP | Gravity.START;
    return p;
  }

  private void createBubble() {
    int size = Ui.dp(this, ToolApp.app.config.number("ballSize"));
    bubble = Ui.text(ui, "✦", 25, Color.WHITE);
    bubble.setGravity(Gravity.CENTER);
    bubble.setPadding(0, 0, 0, 0);
    bubble.setElevation(Ui.dp(this, 6));
    bubble.setBackground(Ui.round(Ui.ACCENT, size));
    bubble.setAlpha(ToolApp.app.config.number("opacity") / 100f);
    bubble.setContentDescription(
        ToolApp.t("GAndroidTools 悬浮菜单；长按拖选", "GAndroidTools menu; hold and drag to select"));
    bubbleParams = params(size, size, false);
    bubbleParams.x = 0;
    bubbleParams.y = getResources().getDisplayMetrics().heightPixels / 3;
    wm.addView(bubble, bubbleParams);
    bubble.setOnClickListener(
        v -> {
          if (menu == null) showMenu();
          else closeMenu();
        });
    bubble.setOnTouchListener(
        new View.OnTouchListener() {
          float x, y;
          int oldX, oldY;
          boolean moved, longPress;
          final Runnable hold =
              () -> {
                longPress = true;
                taps.cancel();
                handler.removeCallbacks(singleTap);
                showMenu();
              };

          @Override
          public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
              case MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(dockTimer);
                bubble.setTranslationX(0);
                x = e.getRawX();
                y = e.getRawY();
                oldX = bubbleParams.x;
                oldY = bubbleParams.y;
                moved = false;
                longPress = false;
                handler.postDelayed(hold, ToolApp.app.config.number("longPressDelay"));
              }
              case MotionEvent.ACTION_MOVE -> {
                float dx = e.getRawX() - x, dy = e.getRawY() - y;
                if (longPress) {
                  select(e.getRawX(), e.getRawY(), false);
                } else if (Math.hypot(dx, dy) > Ui.dp(OverlayService.this, 10)) {
                  moved = true;
                  handler.removeCallbacks(hold);
                  bubbleParams.x = clamp(oldX + (int) dx, 0, screenWidth() - bubble.getWidth());
                  bubbleParams.y = clamp(oldY + (int) dy, 0, screenHeight() - bubble.getHeight());
                  wm.updateViewLayout(bubble, bubbleParams);
                }
              }
              case MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(hold);
                if (longPress) select(e.getRawX(), e.getRawY(), true);
                else if (!moved) {
                  long now = android.os.SystemClock.elapsedRealtime();
                  int interval = ToolApp.app.config.number("doubleTapWindow");
                  if (taps.tap(now, interval) == TapGate.Result.DOUBLE) {
                    handler.removeCallbacks(singleTap);
                    taps.cancel();
                    String selected = ToolApp.app.config.text("doubleTapAction", "screenshot");
                    if (!selected.equals("none")) perform(selected);
                  } else {
                    handler.removeCallbacks(singleTap);
                    handler.postDelayed(singleTap, interval);
                  }
                }
                scheduleDock();
              }
              case MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(hold);
                closeMenu();
                scheduleDock();
              }
              default -> {}
            }
            return true;
          }
        });
    scheduleDock();
  }

  private int screenWidth() {
    return getResources().getDisplayMetrics().widthPixels;
  }

  private int screenHeight() {
    return getResources().getDisplayMetrics().heightPixels;
  }

  private static int clamp(int v, int low, int high) {
    return Math.max(low, Math.min(high, v));
  }

  private void scheduleDock() {
    handler.removeCallbacks(dockTimer);
    int delay = ToolApp.app.config.number("collapse");
    if (delay > 0) handler.postDelayed(dockTimer, delay * 1000L);
  }

  private void dock() {
    closeMenu();
    boolean left = bubbleParams.x < screenWidth() / 2;
    bubbleParams.x = left ? 0 : screenWidth() - bubble.getWidth();
    wm.updateViewLayout(bubble, bubbleParams);
    bubble
        .animate()
        .translationX((left ? -1 : 1) * bubble.getWidth() * 0.55f)
        .setDuration(220)
        .start();
  }

  private void showMenu() {
    closeMenu();
    menuActions = Actions.enabled();
    menuRows.clear();
    LinearLayout root = Ui.column(ui);
    root.setPadding(12, 12, 12, 12);
    root.setBackground(Ui.round(Color.WHITE, Ui.dp(this, 24)));
    root.setElevation(Ui.dp(this, 8));
    for (Actions.Action action : menuActions) {
      TextView row = Ui.text(ui, action.label(), 15, Ui.INK);
      row.setMinHeight(Ui.dp(this, 44));
      row.setPadding(24, 8, 24, 8);
      row.setOnLongClickListener(
          v -> {
            if (action.id().equals("screenshot")) {
              closeMenu();
              delayWheel();
              return true;
            }
            return false;
          });
      row.setOnClickListener(
          v -> {
            closeMenu();
            perform(action.id());
          });
      root.addView(row);
      menuRows.add(row);
    }
    menu = Ui.scroll(ui, root);
    int width = Ui.dp(this, 240);
    WindowManager.LayoutParams p =
        params(
            Math.min(width, screenWidth() - Ui.dp(this, 70)),
            Math.min(Ui.dp(this, 480), screenHeight() - Ui.dp(this, 80)),
            false);
    p.x =
        bubbleParams.x < screenWidth() / 2
            ? bubble.getWidth() + Ui.dp(this, 8)
            : Math.max(0, bubbleParams.x - width - Ui.dp(this, 8));
    p.y =
        clamp(
            bubbleParams.y - Ui.dp(this, 100),
            Ui.dp(this, 24),
            screenHeight() - p.height - Ui.dp(this, 24));
    wm.addView(menu, p);
  }

  private void select(float x, float y, boolean activate) {
    String chosen = null;
    for (int i = 0; i < menuRows.size(); i++) {
      View row = menuRows.get(i);
      int[] pos = new int[2];
      row.getLocationOnScreen(pos);
      boolean inside =
          x >= pos[0] && x < pos[0] + row.getWidth() && y >= pos[1] && y < pos[1] + row.getHeight();
      row.setBackgroundColor(inside ? Color.rgb(221, 238, 231) : Color.TRANSPARENT);
      if (inside) chosen = menuActions.get(i).id();
    }
    if (activate) {
      closeMenu();
      if (chosen != null) {
        if (chosen.equals("screenshot")) delayWheel();
        else perform(chosen);
      }
    }
  }

  private void remove(View v) {
    if (v != null)
      try {
        wm.removeView(v);
      } catch (IllegalArgumentException ignored) {
        /* Already removed. */
      }
  }

  private void closeMenu() {
    remove(menu);
    menu = null;
    menuRows.clear();
  }

  private void closePanel() {
    remove(panel);
    panel = null;
  }

  public void showPanel(View content) {
    closeFrozen();
    LinearLayout root = Ui.column(ui);
    root.setBackground(Ui.round(Ui.BG, Ui.dp(this, 28)));
    root.setPadding(16, 12, 16, 12);
    Ui.button(root, ToolApp.t("关闭面板", "Close panel"), this::closePanel);
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    panel = root;
    WindowManager.LayoutParams p =
        params(
            Math.min(screenWidth() - Ui.dp(this, 24), Ui.dp(this, 480)),
            screenHeight() * 3 / 4,
            true);
    p.x = (screenWidth() - p.width) / 2;
    p.y = screenHeight() / 8;
    p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
    wm.addView(panel, p);
  }

  public void showFrozenPanel(View content) {
    closePanel();
    panel = content;
    WindowManager.LayoutParams p = params(screenWidth(), screenHeight(), true);
    p.x = 0;
    p.y = 0;
    wm.addView(panel, p);
  }

  public void closeFrozen() {
    closePanel();
    if (frozen != null) {
      frozen.dispose();
      frozen = null;
    }
  }

  private void delayWheel() {
    LinearLayout root = Ui.column(ui);
    root.addView(Ui.title(ui, ToolApp.t("单张截图延迟", "Screenshot delay")));
    DelayWheel wheel =
        new DelayWheel(
            ui,
            seconds -> {
              if (seconds >= 0) {
                closePanel();
                screenshotAfter(seconds);
              } else {
                LinearLayout custom = Ui.column(ui);
                for (Config.Option option : Config.OPTIONS)
                  if (option.key().equals("screenshotDelay")) Ui.slider(custom, option);
                Ui.button(
                    custom,
                    ToolApp.t("按此延迟截图", "Capture after this delay"),
                    () -> {
                      closePanel();
                      screenshotAfter(ToolApp.app.config.number("screenshotDelay"));
                    });
                showPanel(Ui.scroll(ui, custom));
              }
            });
    root.addView(wheel, new LinearLayout.LayoutParams(-1, Ui.dp(this, 300)));
    Ui.button(
        root,
        ToolApp.t("取消延迟截图", "Cancel delayed screenshot"),
        () -> {
          shotGeneration++;
          bubble.setText("✦");
          closePanel();
        });
    showPanel(Ui.scroll(ui, root));
  }

  private void screenshotAfter(int seconds) {
    closeMenu();
    closeFrozen();
    long generation = ++shotGeneration;
    long end = android.os.SystemClock.elapsedRealtime() + seconds * 1000L;
    Runnable tick =
        new Runnable() {
          @Override
          public void run() {
            if (stopped || generation != shotGeneration) return;
            long left = end - android.os.SystemClock.elapsedRealtime();
            if (left > 0) {
              bubble.setText("" + (left + 999) / 1000);
              handler.postDelayed(this, Math.min(1000, left));
            } else {
              bubble.setText("✦");
              frozen = new FrozenCapture(OverlayService.this, ui);
              frozen.capture();
            }
          }
        };
    tick.run();
  }

  public void perform(String action) {
    ToolApp.app.log.add("INFO", "action", action);
    closeMenu();
    switch (action) {
      case "screenshot" -> screenshotAfter(ToolApp.app.config.number("screenshotDelay"));
      case "cancelScreenshot" -> {
        shotGeneration++;
        bubble.setText("✦");
        Ui.toast(this, ToolApp.t("延迟截图已取消", "Delayed screenshot cancelled"));
      }
      case "refresh" -> {
        remove(bubble);
        createBubble();
      }
      case "capture" -> {
        capture.start();
        showToolbar();
      }
      case "frame" -> {
        capture.frame();
        showToolbar();
      }
      case "auto" -> {
        capture.automatic();
        showToolbar();
      }
      case "finish" -> capture.finish();
      case "cancelCapture" -> {
        capture.cancel();
        remove(toolbar);
        toolbar = null;
      }
      case "tree" -> chooseTree(false);
      case "captureSettings" -> captureSettings();
      case "mode" -> {
        List<String> modes = List.of("page", "smooth", "manual");
        int next = (modes.indexOf(ToolApp.app.config.text("captureMode", "page")) + 1) % 3;
        ToolApp.app.config.set("captureMode", modes.get(next));
        showToolbar();
      }
      case "snapshot" -> capture.snapshot();
      case "find" -> findDialog();
      case "park" -> taskList(false);
      case "tasks" -> taskList(true);
      case "info" -> new InfoUi(ui, this::showPanel, this::queueInput).open();
      case "infoAdd" ->
          new InfoUi(ui, this::showPanel, this::queueInput)
              .add(0, () -> Ui.toast(this, ToolApp.t("已保存", "Saved")));
      case "record" -> record();
      case "macro" -> macroPicker();
      case "stopMacro" -> ToolApp.app.macros.stop();
      case "services" -> accessibility();
      case "back" -> background(() -> ToolApp.app.bridge.call("back"));
      case "dock" -> dock();
      case "home", "logs" -> {
        startActivity(
            new Intent(this, MainActivity.class)
                .putExtra("page", action.equals("logs") ? "logs" : "home")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      }
      case "stop" -> stopSelf();
      default -> Ui.toast(this, "Unknown action");
    }
  }

  private void background(Work work) {
    ToolApp.app.worker.execute(
        () -> {
          try {
            work.run();
          } catch (Exception e) {
            ToolApp.app.log.error("tool", e);
            Ui.toast(this, e.getMessage());
          }
        });
  }

  private interface Work {
    void run() throws Exception;
  }

  private void showToolbar() {
    remove(toolbar);
    LinearLayout root = Ui.column(ui);
    root.setBackground(Ui.round(Color.WHITE, Ui.dp(this, 20)));
    LinearLayout actions = Ui.row(ui);
    String mode = ToolApp.app.config.text("captureMode", "page");
    root.addView(
        Ui.text(
            ui,
            ToolApp.t("长截图 · ", "Long capture · ")
                + ToolApp.t(mode.equals("page") ? "跳屏" : mode.equals("smooth") ? "滑动" : "手动", mode),
            14,
            Ui.ACCENT));
    for (String id :
        List.of("frame", "auto", "finish", "mode", "tree", "captureSettings", "cancelCapture")) {
      TextView b =
          Ui.text(
              ui,
              switch (id) {
                case "frame" -> "＋";
                case "auto" -> "▶";
                case "finish" -> "✓";
                case "mode" -> "⇅";
                case "tree" -> "▣";
                case "captureSettings" -> "⚙";
                default -> "×";
              },
              22,
              Ui.ACCENT);
      b.setGravity(Gravity.CENTER);
      b.setMinHeight(Ui.dp(this, 48));
      b.setContentDescription(Actions.label(id));
      b.setOnClickListener(v -> perform(id));
      b.setOnLongClickListener(
          v -> {
            Ui.toast(this, Actions.label(id));
            return true;
          });
      actions.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    root.addView(actions);
    Ui.button(root, ToolApp.t("暂停自动采集", "Pause automatic capture"), capture::pause);
    toolbar = root;
    WindowManager.LayoutParams p =
        params(Math.min(screenWidth() - Ui.dp(this, 24), Ui.dp(this, 440)), -2, false);
    p.x = Ui.dp(this, 12);
    p.y = screenHeight() - Ui.dp(this, 200);
    wm.addView(toolbar, p);
  }

  private void captureSettings() {
    LinearLayout root = Ui.column(ui);
    root.addView(Ui.title(ui, ToolApp.t("截图快捷设置", "Capture quick settings")));
    for (Config.Option o : Config.OPTIONS)
      if (List.of("settle", "duration", "overlap", "topCrop", "bottomCrop").contains(o.key()))
        Ui.slider(root, o);
    Ui.toggle(
        root,
        "JPEG",
        ToolApp.t("关闭使用无损 PNG", "Off uses lossless PNG"),
        ToolApp.app.config.flag("jpeg", false),
        v -> ToolApp.app.config.set("jpeg", v));
    Ui.button(root, ToolApp.t("忽略控件…", "Ignore controls…"), () -> chooseTree(true));
    showPanel(Ui.scroll(ui, root));
  }

  private void chooseTree(boolean ignored) {
    closePanel();
    background(
        () -> {
          List<TreeNode> nodes = TreeNode.parse(ToolApp.app.bridge.call("tree"));
          handler.post(
              () -> {
                LinearLayout root = Ui.column(ui);
                root.addView(
                    Ui.title(
                        ui,
                        ignored
                            ? ToolApp.t("忽略控件", "Ignore controls")
                            : ToolApp.t("截图控件", "Capture controls")));
                root.addView(
                    Ui.body(
                        ui,
                        ToolApp.t(
                            "勾选区域取并集；忽略控件填充为白色。未勾选截图控件时截全屏。",
                            "Selected capture bounds are combined. Ignored controls are painted"
                                + " white. No capture selection means full screen.")));
                List<TreeNode> selected = new ArrayList<>();
                List<String> prior = capture.ruleKeys(ignored);
                android.widget.ListView controls = new android.widget.ListView(ui);
                controls.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
                List<String> labels = new ArrayList<>();
                for (TreeNode n : nodes) labels.add("  ".repeat(Math.min(n.depth, 6)) + n.label());
                controls.setAdapter(
                    new android.widget.ArrayAdapter<>(
                        ui, android.R.layout.simple_list_item_multiple_choice, labels));
                for (int i = 0; i < nodes.size(); i++)
                  if (prior.contains(nodes.get(i).path)) {
                    controls.setItemChecked(i, true);
                    selected.add(nodes.get(i));
                  }
                controls.setOnItemClickListener(
                    (parent, view, index, id) -> {
                      TreeNode n = nodes.get(index);
                      if (controls.isItemChecked(index)) selected.add(n);
                      else selected.remove(n);
                    });
                root.addView(controls, new LinearLayout.LayoutParams(-1, 0, 1));
                Ui.button(
                    root,
                    ToolApp.t("应用选择", "Apply selection"),
                    () -> {
                      capture.rules(selected, ignored);
                      closePanel();
                    });
                Ui.button(
                    root,
                    ignored
                        ? ToolApp.t("改选截图控件", "Choose capture controls")
                        : ToolApp.t("选择忽略控件", "Choose ignored controls"),
                    () -> {
                      capture.rules(selected, ignored);
                      chooseTree(!ignored);
                    });
                showPanel(root);
              });
        });
  }

  private void findDialog() {
    LinearLayout root = Ui.column(ui);
    root.addView(Ui.title(ui, ToolApp.t("超级查找", "Super find")));
    EditText q = Ui.input(root, ToolApp.t("输入要查找的文字", "Text to find"), "", false);
    Ui.button(
        root,
        ToolApp.t("查找并标记", "Find and highlight"),
        () -> {
          String text = q.getText().toString();
          if (text.isBlank()) return;
          closePanel();
          search(text);
        });
    Ui.button(root, ToolApp.t("停止查找", "Stop search"), () -> searching.set(false));
    showPanel(root);
  }

  private void search(String text) {
    if (!searching.compareAndSet(false, true)) return;
    background(
        () -> {
          try {
            int limit = ToolApp.app.config.number("searchPages");
            for (int attempt = 0; attempt <= limit && searching.get() && !stopped; attempt++) {
              List<TreeNode> nodes = TreeNode.parse(ToolApp.app.bridge.call("tree"));
              for (TreeNode n : nodes) {
                String value = n.text + " " + n.description;
                boolean found =
                    ToolApp.app.config.flag("caseSensitive", false)
                        ? value.contains(text)
                        : value
                            .toLowerCase(java.util.Locale.ROOT)
                            .contains(text.toLowerCase(java.util.Locale.ROOT));
                if (found && !n.password) {
                  ToolApp.app.bridge.call(
                      "node", new JSONObject().put("key", n.key).put("operation", "show"));
                  Thread.sleep(180);
                  List<TreeNode> refreshed = TreeNode.parse(ToolApp.app.bridge.call("tree"));
                  Rect rect = n.bounds;
                  for (TreeNode after : refreshed)
                    if (after.path.equals(n.path)) rect = after.bounds;
                  Rect finalRect = rect;
                  handler.post(() -> highlight(finalRect));
                  return;
                }
              }
              if (attempt == limit) break;
              boolean scrolled = false;
              for (TreeNode n : nodes)
                if (n.scrollable) {
                  scrolled =
                      ToolApp.app
                          .bridge
                          .call(
                              "node", new JSONObject().put("key", n.key).put("operation", "scroll"))
                          .optBoolean("performed");
                  if (scrolled) break;
                }
              if (!scrolled) break;
              Thread.sleep(ToolApp.app.config.number("settle"));
            }
            Ui.toast(this, ToolApp.t("没有找到可访问的匹配控件", "No matching accessible control found"));
          } finally {
            searching.set(false);
          }
        });
  }

  private void highlight(Rect r) {
    remove(highlight);
    if (r.isEmpty()) return;
    View v = new View(ui);
    android.graphics.drawable.GradientDrawable d =
        Ui.round(Color.argb(35, 64, 127, 117), Ui.dp(this, 10));
    d.setStroke(Ui.dp(this, 3), Ui.ACCENT);
    v.setBackground(d);
    WindowManager.LayoutParams p = params(Math.max(1, r.width()), Math.max(1, r.height()), false);
    p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    p.x = r.left;
    p.y = r.top;
    highlight = v;
    wm.addView(v, p);
    handler.postDelayed(
        () -> {
          remove(v);
          if (highlight == v) highlight = null;
        },
        ToolApp.app.config.number("highlight"));
  }

  public void queueInput(String value) {
    closePanel();
    long generation = ++inputGeneration;
    long end =
        android.os.SystemClock.elapsedRealtime() + ToolApp.app.config.number("inputWait") * 1000L;
    Ui.toast(this, ToolApp.t("请点击要填写的输入框", "Focus the field to fill"));
    background(
        () -> {
          try {
            while (generation == inputGeneration
                && !stopped
                && android.os.SystemClock.elapsedRealtime() < end) {
              boolean done = false;
              InputService service = InputService.instance;
              if (service != null) done = service.input(value);
              else if (ToolApp.app.bridge.ready())
                done =
                    ToolApp.app
                        .bridge
                        .call("input", new JSONObject().put("value", value))
                        .optBoolean("performed");
              if (done) {
                Ui.toast(this, ToolApp.t("已填写", "Filled"));
                ToolApp.app.log.add("INFO", "input", "Selected information inserted");
                return;
              }
              Thread.sleep(250);
            }
            Ui.toast(this, ToolApp.t("等待输入框已取消或超时", "Input request cancelled or timed out"));
          } finally {
            /* Pending value becomes unreachable when this request completes. */
          }
        });
  }

  private void taskList(boolean parked) {
    closePanel();
    background(
        () -> {
          JSONArray tasks =
              ToolApp.app.bridge.call(parked ? "parked" : "tasks").getJSONArray("tasks");
          handler.post(
              () -> {
                LinearLayout root = Ui.column(ui);
                root.addView(
                    Ui.title(
                        ui,
                        parked
                            ? ToolApp.t("后台任务", "Parked tasks")
                            : ToolApp.t("选择运行中任务", "Select a running task")));
                root.addView(
                    Ui.body(
                        ui,
                        ToolApp.t(
                            "任务移到独立屏幕后可继续呈现；系统仍可能终止进程。点击卡片恢复主屏。",
                            "Tasks can keep rendering on a separate display. The system can still"
                                + " terminate processes. Tap a card to restore.")));
                LinearLayout gridRow = null;
                int shown = 0;
                for (int i = 0; i < tasks.length(); i++) {
                  JSONObject task = tasks.optJSONObject(i);
                  if (!parked && task.optInt("displayId") != 0) continue;
                  if (shown % 2 == 0) {
                    gridRow = Ui.row(ui);
                    gridRow.setGravity(Gravity.TOP);
                    root.addView(gridRow, new LinearLayout.LayoutParams(-1, -2));
                  }
                  shown++;
                  LinearLayout card = Ui.card(gridRow);
                  String component = task.optString("component");
                  if (parked) {
                    android.widget.ImageView preview = new android.widget.ImageView(ui);
                    preview.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    preview.setContentDescription(component);
                    card.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(this, 160)));
                    ToolApp.app.worker.execute(
                        () -> {
                          try {
                            android.graphics.Bitmap image =
                                ToolApp.app.bridge.screenshot(task.optInt("displayId"));
                            handler.post(
                                () -> {
                                  if (preview.isAttachedToWindow()) preview.setImageBitmap(image);
                                  else image.recycle();
                                });
                          } catch (Exception error) {
                            ToolApp.app.log.add("DEBUG", "task.preview", "Preview unavailable");
                          }
                        });
                  }
                  Ui.entry(
                      card,
                      component.split("/")[0],
                      "Task " + task.optInt("taskId") + " · Display " + task.optInt("displayId"),
                      () -> {
                        closePanel();
                        background(
                            () -> {
                              ToolApp.app.bridge.call(
                                  parked ? "restore" : "park",
                                  new JSONObject().put("taskId", task.getInt("taskId")));
                              Ui.toast(this, ToolApp.t("任务操作完成", "Task operation completed"));
                            });
                      });
                }
                if (tasks.length() == 0)
                  root.addView(Ui.body(ui, ToolApp.t("没有可用任务", "No tasks available")));
                showPanel(Ui.scroll(ui, root));
              });
        });
  }

  private void record() {
    try {
      if (ToolApp.app.macros.recording()) {
        String source = ToolApp.app.macros.stopRecording();
        File f =
            new File(
                ToolApp.app.macros.directory(), "recorded-" + System.currentTimeMillis() + ".js");
        FileText.write(f.toPath(), source);
        Ui.toast(this, ToolApp.t("录制已保存，可在宏页面编辑", "Recording saved; edit it on the Macros page"));
      } else {
        if (InputService.instance == null)
          throw new IllegalStateException(
              ToolApp.t(
                  "请先启用工具箱的输入与宏录制无障碍服务",
                  "Enable the toolbox accessibility input and recording service first"));
        ToolApp.app.macros.startRecording();
        Ui.toast(
            this, ToolApp.t("已开始录制，重复此按钮停止", "Recording started; use this action again to stop"));
      }
    } catch (Exception e) {
      ToolApp.app.log.error("macro", e);
      Ui.toast(this, e.getMessage());
    }
  }

  private void macroPicker() {
    File[] scripts = ToolApp.app.macros.directory().listFiles((f, n) -> n.endsWith(".js"));
    LinearLayout root = Ui.column(ui);
    root.addView(Ui.title(ui, ToolApp.t("选择脚本宏", "Select a macro")));
    if (scripts != null)
      for (File f : scripts)
        Ui.button(
            root,
            f.getName(),
            () -> {
              closePanel();
              Ui.dialog(ui)
                  .setTitle(f.getName())
                  .setMessage(
                      ToolApp.t(
                          "运行此脚本？脚本可以操作当前屏幕。",
                          "Run this script? It can interact with the current screen."))
                  .setPositiveButton(
                      ToolApp.t("运行", "Run"),
                      (d, n) ->
                          background(
                              () ->
                                  ToolApp.app.macros.run(
                                      FileText.read(f.toPath()),
                                      e ->
                                          Ui.toast(
                                              this,
                                              e == null
                                                  ? ToolApp.t("运行完成", "Completed")
                                                  : e.getMessage()))))
                  .setNegativeButton(ToolApp.t("取消", "Cancel"), null)
                  .show();
            });
    showPanel(Ui.scroll(ui, root));
  }

  private void accessibility() {
    closePanel();
    background(
        () -> {
          JSONObject data = ToolApp.app.bridge.call("services");
          handler.post(
              () -> showPanel(Ui.scroll(ui, ServiceUi.build(ui, data, this::accessibility))));
        });
  }

  public void showFiles(List<File> files) {
    LinearLayout root = Ui.column(ui);
    root.addView(Ui.title(ui, ToolApp.t("导出完成", "Export complete")));
    for (File f : files) Ui.button(root, f.getName(), () -> Exports.share(this, f));
    showPanel(Ui.scroll(ui, root));
  }

  public void hideForCapture(boolean hide) {
    CountDownLatch latch = new CountDownLatch(1);
    handler.post(
        () -> {
          hidden += hide ? 1 : -1;
          hidden = Math.max(0, hidden);
          for (View v : new View[] {bubble, menu, panel, toolbar, highlight})
            if (v != null) v.setVisibility(hidden > 0 ? View.INVISIBLE : View.VISIBLE);
          latch.countDown();
        });
    try {
      if (!latch.await(3, TimeUnit.SECONDS))
        throw new IllegalStateException("Overlay did not update");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private final Runnable watchdog =
      new Runnable() {
        @Override
        public void run() {
          if (stopped) return;
          watchdogTicks++;
          if (ToolApp.app.bridge.ready()) {
            String kept = ToolApp.app.config.text("keepServices", "");
            background(
                () -> {
                  ToolApp.app.bridge.call("parked");
                  if (!kept.isBlank()) {
                    String enabled = ToolApp.app.bridge.call("services").optString("enabled");
                    for (String component : kept.split(":"))
                      if (!component.isBlank()
                          && !Arrays.asList(enabled.split(":")).contains(component)) {
                        ToolApp.app.bridge.call(
                            "service",
                            new JSONObject().put("component", component).put("enabled", true));
                        ToolApp.app.log.add(
                            "INFO", "accessibility", "Selected service re-enabled: " + component);
                      }
                  }
                });
          }
          handler.postDelayed(this, ToolApp.app.config.number("watchdog") * 1000L);
        }
      };

  @Override
  public void onDestroy() {
    stopped = true;
    searching.set(false);
    inputGeneration++;
    handler.removeCallbacksAndMessages(null);
    ToolApp.app.macros.stop();
    if (ToolApp.app.macros.recording()) record();
    if (frozen != null) {
      frozen.dispose();
      frozen = null;
    }
    shotGeneration++;
    capture.close();
    ToolApp.app.worker.execute(
        () -> {
          try {
            JSONArray tracked = ToolApp.app.bridge.call("parked").getJSONArray("tasks");
            for (int i = 0; i < tracked.length(); i++)
              try {
                ToolApp.app.bridge.call(
                    "restore",
                    new JSONObject().put("taskId", tracked.getJSONObject(i).getInt("taskId")));
              } catch (Exception error) {
                ToolApp.app.log.error("task.restore", error);
              }
          } catch (Exception error) {
            ToolApp.app.log.error("task.restore", error);
          }
        });
    closeMenu();
    closePanel();
    remove(toolbar);
    remove(highlight);
    remove(bubble);
    instance = null;
    ToolApp.app.log.add("INFO", "overlay", "Service stopped");
    super.onDestroy();
  }
}
