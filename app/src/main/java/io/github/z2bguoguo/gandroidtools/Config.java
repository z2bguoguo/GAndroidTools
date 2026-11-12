package io.github.z2bguoguo.gandroidtools;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import java.util.Locale;

public final class Config {
  public record Option(
      String key,
      String zh,
      String en,
      int min,
      int max,
      int initial,
      String hintZh,
      String hintEn) {}

  public static final List<Option> OPTIONS =
      List.of(
          new Option(
              "screenshotDelay",
              "默认单张截图延迟（秒）",
              "Default screenshot delay (seconds)",
              0,
              30,
              0,
              "长按截图按钮可用转盘临时选择。",
              "Hold the screenshot action to select a delay for one capture."),
          new Option(
              "doubleTapWindow",
              "双击判定窗口（毫秒）",
              "Double-tap interval (ms)",
              180,
              500,
              280,
              "单击会等待此窗口，避免与双击同时触发。",
              "Single taps wait for this interval to avoid firing alongside double taps."),
          new Option(
              "longPressDelay",
              "长按判定时间（毫秒）",
              "Long-press delay (ms)",
              250,
              1200,
              450,
              "长按悬浮球后拖到截图选项，可打开延迟转盘。",
              "Hold the floating button and drag to Screenshot to open its delay wheel."),
          new Option(
              "collapse",
              "自动贴边等待（秒）",
              "Auto-dock delay (seconds)",
              0,
              120,
              5,
              "0 关闭自动贴边；交互后重新计时。",
              "0 disables automatic docking; interaction resets the timer."),
          new Option(
              "ballSize",
              "悬浮球尺寸（dp）",
              "Floating button size (dp)",
              36,
              80,
              52,
              "尺寸影响触摸范围。",
              "Controls the touch target size."),
          new Option(
              "opacity",
              "悬浮球不透明度（%）",
              "Floating button opacity (%)",
              20,
              100,
              85,
              "贴边后保留一部分可见区域。",
              "A visible handle remains after docking."),
          new Option(
              "settle",
              "滚动后等待（毫秒）",
              "Post-scroll settle time (ms)",
              100,
              4000,
              650,
              "复杂页面可增大，减少未完成渲染。",
              "Increase for pages with delayed rendering."),
          new Option(
              "duration",
              "滑动时长（毫秒）",
              "Swipe duration (ms)",
              80,
              2000,
              420,
              "缓慢滑动模式的手势持续时间。",
              "Gesture duration in smooth scrolling mode."),
          new Option(
              "overlap",
              "跳屏保留重叠（%）",
              "Page jump overlap (%)",
              10,
              70,
              25,
              "拼接通过像素匹配寻找重叠。",
              "Stitching finds overlap with pixel matching."),
          new Option(
              "maxFrames",
              "最大截图帧数",
              "Maximum capture frames",
              2,
              200,
              40,
              "帧写入磁盘；导出长图按高度拆分。",
              "Frames are spooled to disk; tall exports are split."),
          new Option(
              "maxHeight",
              "单张导出高度（像素）",
              "Maximum exported image height (px)",
              2048,
              24000,
              12000,
              "限制峰值内存，超过后拆成多张图片。",
              "Limits peak memory; longer captures become multiple images."),
          new Option(
              "topCrop",
              "裁掉顶部（像素）",
              "Crop top (px)",
              0,
              700,
              0,
              "从最终选择的截图区域继续裁剪。",
              "Applied inside the selected capture region."),
          new Option(
              "bottomCrop",
              "裁掉底部（像素）",
              "Crop bottom (px)",
              0,
              700,
              0,
              "可移除固定底栏。",
              "Can remove a fixed bottom bar."),
          new Option(
              "jpegQuality",
              "图片质量（%）",
              "Image quality (%)",
              40,
              100,
              95,
              "JPEG 导出质量；PNG 为无损。",
              "JPEG quality; PNG remains lossless."),
          new Option(
              "highlight",
              "查找高亮（毫秒）",
              "Search highlight duration (ms)",
              300,
              10000,
              1800,
              "用不接收触摸的边框标记结果。",
              "Highlights matches with a non-touchable outline."),
          new Option(
              "searchPages",
              "查找最大滚动次数",
              "Maximum search scrolls",
              0,
              100,
              12,
              "0 只搜索当前可访问控件。",
              "0 searches only the current accessible tree."),
          new Option(
              "inputWait",
              "等待输入框（秒）",
              "Wait for input field (seconds)",
              1,
              60,
              12,
              "没有焦点时等待你点击输入框；到时自动取消。",
              "Waits for you to focus a field, then cancels on timeout."),
          new Option(
              "macroTimeout",
              "宏最长运行（秒）",
              "Macro time limit (seconds)",
              1,
              600,
              60,
              "同时约束脚本指令及 API 等待；可随时停止。",
              "Bounds script execution and API waits; stop at any time."),
          new Option(
              "watchdog",
              "无障碍检查间隔（秒）",
              "Accessibility check interval (seconds)",
              3,
              120,
              15,
              "仅监控明确勾选的服务，需持续 Shizuku 授权。",
              "Monitors only explicitly selected services; Shizuku must stay available."),
          new Option(
              "logLines",
              "日志内存保留行数",
              "In-memory log lines",
              200,
              5000,
              1200,
              "磁盘另有轮转日志；不记录输入内容和密钥。",
              "Disk logs rotate separately; input values and secrets are excluded."));
  private final SharedPreferences prefs;

  public Config(Context c) {
    prefs = c.getSharedPreferences("preferences", Context.MODE_PRIVATE);
  }

  public boolean english() {
    String v = text("language", "system");
    return v.equals("en")
        || (v.equals("system") && !Locale.getDefault().getLanguage().equals("zh"));
  }

  public int number(String key) {
    for (Option o : OPTIONS)
      if (o.key.equals(key)) return Math.max(o.min, Math.min(o.max, prefs.getInt(key, o.initial)));
    throw new IllegalArgumentException("Unknown option: " + key);
  }

  public void set(String key, int value) {
    prefs.edit().putInt(key, value).apply();
  }

  public boolean flag(String key, boolean fallback) {
    return prefs.getBoolean(key, fallback);
  }

  public void set(String key, boolean value) {
    prefs.edit().putBoolean(key, value).apply();
  }

  public String text(String key, String fallback) {
    return prefs.getString(key, fallback);
  }

  public void set(String key, String value) {
    prefs.edit().putString(key, value).apply();
  }
}
