// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.util.List;

public final class Actions {
  public record Action(String id, String zh, String en) {
    public String label() {
      return ToolApp.t(zh, en);
    }
  }

  public static final List<Action> ALL =
      List.of(
          new Action("screenshot", "截图（冻结后编辑）", "Screenshot and edit"),
          new Action("cancelScreenshot", "取消延迟截图", "Cancel delayed screenshot"),
          new Action("capture", "长截图工具栏", "Long capture toolbar"),
          new Action("frame", "采集一帧", "Capture a frame"),
          new Action("auto", "自动采集", "Automatic capture"),
          new Action("finish", "导出长截图", "Export long capture"),
          new Action("cancelCapture", "取消截图", "Cancel capture"),
          new Action("tree", "选择截图控件", "Select capture controls"),
          new Action("captureSettings", "截图快捷设置", "Capture quick settings"),
          new Action("mode", "切换滚动模式", "Switch scrolling mode"),
          new Action("snapshot", "页面快照", "Page snapshot"),
          new Action("find", "超级查找", "Super find"),
          new Action("park", "移入隐藏屏幕", "Park on hidden display"),
          new Action("tasks", "后台任务视图", "Parked task cards"),
          new Action("info", "信息本", "Information notebook"),
          new Action("infoAdd", "快速添加信息", "Quick add information"),
          new Action("record", "开始/停止录制宏", "Start / stop recording"),
          new Action("macro", "运行脚本宏", "Run a script macro"),
          new Action("stopMacro", "停止宏", "Stop macro"),
          new Action("services", "无障碍管理", "Accessibility manager"),
          new Action("back", "返回", "Back"),
          new Action("dock", "立即贴边收起", "Dock now"),
          new Action("home", "打开工具箱", "Open toolbox"),
          new Action("logs", "查看日志", "View logs"),
          new Action("stop", "关闭悬浮工具", "Stop floating tools"));

  public static String label(String id) {
    return ALL.stream().filter(a -> a.id.equals(id)).findFirst().map(Action::label).orElse(id);
  }

  private Actions() {}

  public static List<String> slots() {
    java.util.Set<String> allowed =
        ALL.stream().map(Action::id).collect(java.util.stream.Collectors.toSet());
    String stored = ToolApp.app.config.text("radialSlots", "");
    if (stored.isEmpty()) {
      String legacy = ToolApp.app.config.text("actions", "");
      stored = legacy.isEmpty() ? RadialBindings.DEFAULT : legacy;
      stored = String.join(",", RadialBindings.parse(stored, allowed));
      ToolApp.app.config.set("radialSlots", stored);
    }
    return RadialBindings.parse(stored, allowed);
  }

  public static String position(int i, boolean left) {
    String[] zh =
        left ? new String[] {"上", "右上", "右", "右下", "下"} : new String[] {"上", "左上", "左", "左下", "下"};
    String[] en =
        left
            ? new String[] {"Top", "Upper right", "Right", "Lower right", "Bottom"}
            : new String[] {"Top", "Upper left", "Left", "Lower left", "Bottom"};
    return ToolApp.t(zh[i], en[i]);
  }

  public static String shortLabel(String id) {
    return switch (id) {
      case "screenshot" -> ToolApp.t("截图", "Shot");
      case "cancelScreenshot" -> ToolApp.t("取消延迟", "Cancel");
      case "capture" -> ToolApp.t("长截图", "Capture");
      case "frame" -> ToolApp.t("采集", "Frame");
      case "auto" -> ToolApp.t("自动", "Auto");
      case "finish" -> ToolApp.t("导出", "Export");
      case "cancelCapture" -> ToolApp.t("取消截图", "Cancel");
      case "tree" -> ToolApp.t("控件", "Controls");
      case "captureSettings" -> ToolApp.t("截图设置", "Options");
      case "mode" -> ToolApp.t("模式", "Mode");
      case "snapshot" -> ToolApp.t("页面包", "Archive");
      case "find" -> ToolApp.t("查找", "Find");
      case "park" -> ToolApp.t("隐藏前台", "Park app");
      case "tasks" -> ToolApp.t("隐藏任务", "Tasks");
      case "info" -> ToolApp.t("信息本", "Notes");
      case "infoAdd" -> ToolApp.t("添加信息", "Add note");
      case "record" -> ToolApp.t("录制", "Record");
      case "macro" -> ToolApp.t("运行宏", "Run");
      case "stopMacro" -> ToolApp.t("停止宏", "Stop");
      case "services" -> ToolApp.t("无障碍", "Access");
      case "back" -> ToolApp.t("返回", "Back");
      case "dock" -> ToolApp.t("贴边", "Dock");
      case "home" -> ToolApp.t("首页", "Home");
      case "logs" -> ToolApp.t("日志", "Logs");
      case "stop" -> ToolApp.t("关闭", "Close");
      default -> ToolApp.t("未分配", "Empty");
    };
  }

  public static android.widget.TextView ball(
      android.content.Context c, String id, String position) {
    String label = id.equals("none") ? ToolApp.t("未分配", "Empty") : label(id);
    android.widget.TextView v =
        Ui.text(c, shortLabel(id), 11, id.equals("none") ? Ui.MUTED : Ui.INK);
    v.setGravity(android.view.Gravity.CENTER);
    v.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
    v.setMaxLines(3);
    v.setEllipsize(android.text.TextUtils.TruncateAt.END);
    v.setBackground(Ui.round(id.equals("none") ? 0xffe5eae7 : 0xffdceee6, Ui.dp(c, 100)));
    v.setElevation(Ui.dp(c, 2));
    v.setContentDescription(position + " · " + label);
    v.setTooltipText(position + " · " + label + "\n" + SettingsHelp.action(id));
    return v;
  }
}
