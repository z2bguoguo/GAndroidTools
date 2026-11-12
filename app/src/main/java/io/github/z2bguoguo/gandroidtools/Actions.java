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

  public static List<Action> enabled() {
    String selected =
        ToolApp.app.config.text(
            "actions", "screenshot,capture,snapshot,find,park,tasks,info,record,macro,dock,home");
    List<String> ids = java.util.Arrays.asList(selected.split(","));
    return ALL.stream().filter(a -> ids.contains(a.id)).toList();
  }
}
