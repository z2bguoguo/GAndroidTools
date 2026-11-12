// Copyright (c) 2026 GAndroidTools contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.z2bguoguo.gandroidtools;

import java.util.LinkedHashMap;
import java.util.Map;

/** Authored bilingual explanations shared by settings and quick controls. */
public final class SettingsHelp {
  private record Copy(String zh, String en) {}

  private static final Map<String, Copy> TEXT = new LinkedHashMap<>();

  private static void add(String key, String zh, String en) {
    TEXT.put(key, new Copy(zh, en));
  }

  static {
    add(
        "screenshotDelay",
        "点击截图后等待指定秒数，再冻结画面与控件树。等待期间可以切换应用或关闭菜单，悬浮球显示倒计时；取消延迟截图会终止本次等待。\n\n"
            + "例：设置 3 秒后点击截图，有 3 秒时间打开目标弹窗。长按截图功能球可用转盘临时覆盖此设置。",
        "Waits before freezing the image and control tree. During the countdown you can switch apps"
            + " or dismiss menus; the floating button shows seconds remaining. Cancel delayed"
            + " screenshot stops the request.\n\n"
            + "Example: use 3 seconds to open a target dialog. Holding Screenshot opens the delay"
            + " wheel for a one-time override.");
    add(
        "doubleTapWindow",
        "两次点击在此时间内完成时执行中心球的双击动作，单击会等判定窗口结束。时间越大越容易双击，但单击反馈稍晚。\n\n"
            + "例：280 毫秒适合快速双击；手部操作较慢可调到 400 毫秒。第二次按下后拖动会移动悬浮球，不会执行双击动作。",
        "Two completed taps inside this interval execute the center double-tap binding. Single-tap"
            + " feedback waits until the interval ends. A larger interval helps slower tapping but"
            + " delays single-tap feedback.\n\n"
            + "Example: try 280 ms for quick taps or 400 ms for slower taps. Dragging on the second"
            + " press moves the button instead of firing a double tap.");
    add(
        "longPressDelay",
        "按住悬浮球达到此时长才展开半圆菜单；继续拖向功能球方向，释放后触发。未先单击时，短拖不会移动球。\n\n"
            + "例：450 毫秒可减少误触；调到 300 毫秒可以更快展开。长按开始后会取消已准备的位置移动。",
        "Holding for this duration opens the semicircle. Drag toward an action and release to"
            + " trigger it. A short drag without a preceding tap does not move the button.\n\n"
            + "Example: 450 ms reduces accidental activation; 300 ms opens faster. Entering a long"
            + " press cancels move arming.");
    add(
        "collapse",
        "没有触摸操作后等待多久自动贴到最近屏幕边缘，只保留一小段手柄。每次交互重新计时，展开的菜单会关闭。0 表示不自动收起。\n\n"
            + "例：5 秒适合日常使用；连续操作时可设为 0，再用“立即贴边收起”。",
        "After this idle delay, docks to the nearest edge and leaves a visible handle. Interaction"
            + " resets the timer; docking closes the menu. Zero disables automatic docking.\n\n"
            + "Example: use 5 seconds normally, or 0 during repeated actions and dock manually.");
    add(
        "ballSize",
        "控制常驻悬浮球的直径，单位为 dp。更大的球更容易触摸，但遮挡面积更大。更改后点击“应用悬浮布局”。\n\n"
            + "例：52 dp 为默认值，小屏可选 44 dp，手指不易点准时可选 64 dp。功能球会在可用屏幕范围内排列。",
        "Controls the persistent button diameter in dp. Larger buttons are easier to touch but"
            + " cover more content. Use Apply floating layout after changing it.\n\n"
            + "Example: 52 dp is the default; try 44 on compact screens or 64 for easier touch"
            + " targets. Action balls stay within the available screen area.");
    add(
        "opacity",
        "控制常驻悬浮球的不透明度，数值越小越透。菜单中的功能球保持清晰，贴边后仍保留可见手柄。\n\n例：85% 方便辨认；希望减少遮挡可用 50%。透明不会改变触摸区域。",
        "Controls opacity of the persistent button. Lower values are more transparent; action balls"
            + " remain readable and a docked handle stays visible.\n\n"
            + "Example: use 85% for visibility or 50% to reduce obstruction. Transparency does not"
            + " shrink the touch target.");
    add(
        "settle",
        "每次自动滚动后等待页面稳定，再读取控件并截图。过短可能截到动画、空白加载区或未绘制内容，过长则降低速度。\n\n"
            + "例：本地列表可用 300～650 毫秒；含网络加载或复杂动画的页面可用 1200 毫秒。不会等待网络无限加载。",
        "Waits after each automatic scroll before reading controls and capturing. Too short can"
            + " capture animations, loading gaps or incomplete rendering; too long slows the"
            + " session.\n\n"
            + "Example: try 300–650 ms for local lists or 1200 ms for animated or network-loaded"
            + " pages. It never waits indefinitely for networking.");
    add(
        "duration",
        "缓慢滑动模式每次手势持续的毫秒数。仅影响滑动速度，不改变截图分辨率，也不替代滚动后等待。\n\n"
            + "例：420 毫秒较自然；长列表可用 700 毫秒减少惯性。跳屏模式优先使用控件的滚动动作。",
        "Duration of each smooth-mode swipe. It changes gesture speed, not resolution, and does not"
            + " replace the settle delay.\n\n"
            + "Example: 420 ms feels natural; 700 ms can reduce fling momentum. Page-jump mode"
            + " prefers accessible scroll actions.");
    add(
        "overlap",
        "跳屏手势后预留的画面重叠比例，为拼接寻找相同内容。值越大，每次前进越少、帧数更多。控件滚动模式实际距离由目标应用决定。\n\n"
            + "例：25% 通常足够；内容重复或排版复杂时可用 40%。找不到可靠重叠时会另存一张图。",
        "Overlap retained by the page-jump gesture. Higher values advance less and require more"
            + " frames. Accessible scroll actions use the distance chosen by the target app.\n\n"
            + "Example: 25% is a useful starting point; try 40% for repetitive or complex pages."
            + " Unreliable overlaps start another image.");
    add(
        "maxFrames",
        "限制一个长截图会话采集的帧数。帧逐张写入缓存，达到上限会暂停；导出或取消后清理会话缓存。\n\n"
            + "例：40 帧适合一般文章；提高到 100 帧会增加耗时和磁盘占用。已导出的文件不会被此选项删除。",
        "Caps frames collected in a long-capture session. Frames are spooled to cache; reaching the"
            + " cap pauses capture. Export or cancel cleans the session cache.\n\n"
            + "Example: 40 frames suits many articles; 100 uses more time and disk. This option"
            + " never deletes completed exports.");
    add(
        "maxHeight",
        "一张导出图片的目标高度上限。达到上限会拆成多张，另有内存预算保护可能更早分图。数值越大，对内存和相册查看器要求越高。\n\n"
            + "例：12000 像素用于普通长图；低内存设备可以改为 6000。拆分文件会分别保存到相册。",
        "Target height limit of each exported image. Longer captures split into multiple files; the"
            + " memory budget may split earlier. Large images require more memory and stronger"
            + " gallery viewers.\n\n"
            + "Example: use 12000 pixels normally or 6000 on low-memory devices. Every split image"
            + " is saved separately to the gallery.");
    add(
        "topCrop",
        "从选定截图区域顶部裁掉的像素数，在每帧中应用。可去除固定标题栏，减少标题重复。单位是截图像素，不是 dp。\n\n"
            + "例：固定标题高 180 像素时填 180。顶部和底部裁剪合计过大导致区域为空时，会停止并报告错误。",
        "Pixels removed from the top of the chosen region in every frame. Useful for fixed headers;"
            + " units are screenshot pixels, not dp.\n\n"
            + "Example: enter 180 for a 180-pixel header. If top and bottom crops empty the region,"
            + " capture stops with an error.");
    add(
        "bottomCrop",
        "从选定截图区域底部裁掉的像素数。用于去掉固定导航、广告或输入栏；不会滚动或关闭这些控件。\n\n"
            + "例：底栏占 140 像素时填 140；不需要裁剪就保持 0。可与控件排除遮罩同时使用。",
        "Pixels removed from the bottom of the selected region. Removes fixed navigation, banners"
            + " or input bars without interacting with them.\n\n"
            + "Example: enter 140 for a 140-pixel bottom bar, or 0 for no crop. Can be combined"
            + " with control masks.");
    add(
        "jpegQuality",
        "仅在 JPEG 导出开启时生效，控制有损压缩质量。提高质量一般会增加文件体积；PNG 始终无损。\n\n"
            + "例：95% 适合文字截图；80% 更省空间。相册和应用内导出使用同一份编码后的图片，不会重复压缩。",
        "Applies only to JPEG exports and controls lossy compression. Higher values generally"
            + " produce larger files; PNG stays lossless.\n\n"
            + "Example: 95% favors text clarity, while 80% saves space. Gallery copies reuse the"
            + " encoded image without recompression.");
    add(
        "highlight",
        "查找命中后显示边框的时间。高亮层不接收触摸，倒计时结束自动移除；新的结果会替换旧标记。\n\n"
            + "例：1800 毫秒适合快速定位；需要截图记录时可用 5000 毫秒。不会修改目标应用内容。",
        "How long a matched control is outlined. The highlight does not receive touch and"
            + " disappears automatically; a new result replaces it.\n\n"
            + "Example: 1800 ms suits quick navigation; 5000 ms gives more inspection time. Target"
            + " app content is unchanged.");
    add(
        "searchPages",
        "当前控件树未命中时，最多尝试向前滚动多少次。搜索从当前位置开始，0 只查当前可访问控件。\n\n"
            + "例：12 次适合普通列表；整页文字已经加载时设 0 更快。未暴露的自绘文字无法通过此搜索找到。",
        "Maximum forward-scroll attempts after no match in the current tree. Starts at the current"
            + " position; zero searches only the current accessible nodes.\n\n"
            + "Example: use 12 for lists or 0 when the whole page is already exposed. Custom-drawn"
            + " text without accessibility metadata cannot be found.");
    add(
        "inputWait",
        "选中信息后，如果当前没有可填写的焦点输入框，会等待你点击输入框，超过此时长自动取消。新请求会取代旧请求，不使用剪贴板。\n\n"
            + "例：12 秒足以切换输入位置。密码框和工具箱自身输入框会被排除；隐私条目先进行生物识别验证。",
        "After choosing information, waits for you to focus an editable field if none is available."
            + " The request expires after this delay; a new request replaces it. Clipboard fallback"
            + " is not used.\n\n"
            + "Example: 12 seconds allows switching fields. Password fields and toolbox fields are"
            + " excluded; private information requires biometrics first.");
    add(
        "macroTimeout",
        "单个脚本允许运行的最长时间，包括循环、等待与工具操作。超过后由解释器检查终止；已发出的系统命令会在自己的有限超时内结束。\n\n"
            + "例：60 秒适合短操作；较长工作流可设为 180 秒。可随时使用“停止宏”提前终止。",
        "Maximum runtime of one script, including loops, waits and tool calls. Interpreter checks"
            + " stop an overrun; an in-flight system command finishes within its own bounded"
            + " timeout.\n\n"
            + "Example: use 60 seconds for short automation or 180 for longer workflows. Stop macro"
            + " can cancel earlier.");
    add(
        "watchdog",
        "悬浮服务运行时，检查选定无障碍服务与隐藏任务的间隔。只有明确开启保持的服务才会被重新激活，关闭悬浮服务后停止检查。\n\n"
            + "例：15 秒平衡恢复速度与开销；3 秒更及时。它检查启用设置被移除，不保证识别所有服务崩溃。",
        "Polling interval for selected accessibility services and parked tasks while floating tools"
            + " run. Only services explicitly marked Keep are re-enabled; stopping floating tools"
            + " stops polling.\n\n"
            + "Example: 15 seconds balances recovery speed and overhead; 3 seconds responds faster."
            + " It detects removal from enabled settings, not every service crash.");
    add(
        "logLines",
        "终端在内存中保留的最大行数，达到上限时淘汰最早行。与磁盘轮转日志独立，不影响日志导出。\n\n"
            + "例：1200 行适合日常排查；5000 行能看更长历史但占用更多内存。日志不记录输入值与 MFA 密钥。",
        "Maximum terminal lines retained in memory; the oldest lines are discarded at the cap. Disk"
            + " rotation is separate and exports are unaffected.\n\n"
            + "Example: 1200 lines suits everyday debugging; 5000 retains more history at a higher"
            + " memory cost. Input values and MFA secrets are not logged.");
    add(
        "language",
        "选择工具箱界面的中文、英文或跟随系统。切换会立即重建设置页；用户保存的信息、脚本和其他应用文字不会翻译。\n\n"
            + "例：选择 English 后按钮与工具提示使用英文；跟随系统会按设备当前语言选择。",
        "Select Chinese, English or the system language. Switching rebuilds settings immediately;"
            + " saved information, scripts and other apps' content are not translated.\n\n"
            + "Example: English changes labels and help. System follows the device language.");
    add(
        "radial",
        "点击半圆上的任意功能球，为这个固定位置选择操作；点击中心球设置悬浮球的双击动作。上到下共五个位置，可重复绑定或选择不执行。\n\n"
            + "运行时：靠左向右展开，靠右向左展开。长按后拖向方向并释放触发；先单击一次，再按住拖动可以移动位置。左右预览只影响编辑器展示，不固定运行侧边。",
        "Tap an outer ball to assign that fixed slot, or the center to assign the double-tap"
            + " action. Five slots run top to bottom; duplicates and unassigned slots are"
            + " allowed.\n\n"
            + "At runtime the menu opens inward: rightward on the left edge and leftward on the"
            + " right edge. Hold, drag toward an action and release. Tap once, then press and drag"
            + " to move. Preview side does not force the runtime edge.");
    add(
        "applyLayout",
        "重新绘制正在运行的悬浮球，使尺寸、不透明度等外观立即更新；不清除功能绑定或其他设置。\n\n"
            + "例：尺寸从 52 调到 64 dp 后点击应用。悬浮工具未启动时，下次启动会直接使用新设置。",
        "Redraws the running floating button so size and opacity changes apply immediately."
            + " Bindings and other settings are preserved.\n\n"
            + "Example: apply after changing from 52 to 64 dp. If floating tools are off, the next"
            + " start uses the new settings.");
    add(
        "resetRadial",
        "把五个外球恢复为截图、长截图、查找、信息本、后台任务，中心双击恢复为截图。仅重置这些绑定。\n\n例：想重新开始配置半圆布局时使用；不会重置截图参数或删除信息。",
        "Resets the five slots to Screenshot, Long capture, Find, Notebook and Parked tasks; center"
            + " double tap becomes Screenshot. Only these bindings reset.\n\n"
            + "Example: use this to start over with layout assignments; capture settings and saved"
            + " information stay intact.");
    add(
        "jpeg",
        "打开后长截图导出为 JPEG，使用图片质量设置；关闭时导出 PNG。格式选择同时作用于相册副本。\n\n例：文字和线条优先 PNG，照片较多的内容可用 JPEG 减小体积。",
        "Long captures use JPEG and the quality setting when enabled, otherwise PNG. The gallery"
            + " copy uses the same format.\n\n"
            + "Example: PNG favors text and lines; JPEG can reduce photo-heavy file sizes.");
    add(
        "saveGallery",
        "长截图导出完成后，自动把每张分图保存到 Pictures/GAndroidTools，并通知系统相册。应用内导出文件仍保留。\n\n"
            + "Android 10 及以上无需相册读取权限；Android 8/9 需先允许存储写入。空间不足或权限被拒绝时会提示失败，应用内导出不会丢失。关闭后仅保存在应用内。",
        "After long-capture export, copies every split image to Pictures/GAndroidTools for the"
            + " system gallery. App-internal exports remain available.\n\n"
            + "Android 10+ does not require gallery read access. Android 8/9 needs storage write"
            + " permission. Storage or permission failures are reported without losing the internal"
            + " export. Disable to keep exports app-internal only.");
    add(
        "captureMode",
        "跳屏优先调用控件滚动，滑动模式执行缓慢手势，手动模式由你滚动并点按钮采集。工具栏也可临时切换，设置会同步保存。\n\n"
            + "例：普通文章用跳屏，容易惯性滑动的页面用慢滑，自定义画布可用手动。",
        "Page mode prefers accessible scroll actions; Smooth performs a swipe; Manual lets you"
            + " scroll and request frames yourself. The toolbar can change this mode too.\n\n"
            + "Example: use Page for articles, Smooth for gesture-driven pages or Manual for custom"
            + " canvases.");
    add(
        "allWindows",
        "页面快照是否读取所有可访问窗口的控件树。开启可能包含键盘、系统弹窗或其他可见窗口；截图仍是主屏画面。\n\n"
            + "例：需要分析系统对话框时开启；通常保持关闭可减少无关数据。分享页面包前检查内容。",
        "Includes accessible metadata from all windows in a page archive, potentially including"
            + " keyboard and system dialogs. The image is still the primary display.\n\n"
            + "Example: enable when inspecting a system dialog; normally leave off to reduce"
            + " unrelated data. Review archives before sharing.");
    add(
        "caseSensitive",
        "开启后按文字原始大小写匹配；关闭时英文大小写视为相同。中文不受此选项影响。\n\n例：关闭时搜索 settings 也能命中 Settings；开启时仅命中相同大小写。",
        "Matches original letter case when enabled; otherwise letter case is ignored. Chinese"
            + " matching is unaffected.\n\n"
            + "Example: with this off, settings matches Settings. With it on, case must match.");
    add(
        "recordText",
        "主动录制宏期间，是否把非密码输入框的文本变化保存到脚本。默认关闭；开启后脚本可能包含账号、消息等私人内容。\n\n"
            + "例：只录制点击和滚动时保持关闭。需要重复填写模板时开启，并在分享脚本前逐项检查。",
        "Saves non-password text changes into a macro only while explicit recording is active. Off"
            + " by default; enabling can place account names or messages in scripts.\n\n"
            + "Example: leave off for click/scroll recording. Enable for text templates, then"
            + " review the script before sharing.");
    add(
        "logFollow",
        "有新日志时自动滚到底部。关闭后可停留在旧日志位置；暂停显示与关键词、级别筛选仍可使用。\n\n例：正在阅读错误堆栈时关闭，观察实时操作过程时开启。不会改变磁盘日志记录。",
        "Automatically scrolls to the latest log. Turn off to inspect older output; pause, keyword"
            + " and severity filters remain available.\n\n"
            + "Example: disable while reading a stack trace, enable when monitoring actions live."
            + " Disk logging is unaffected.");
    add(
        "overlayPermission",
        "打开安卓系统的“显示在其他应用上层”授权页。只有授权后，悬浮球、控件高亮和截图编辑覆盖页才能显示。\n\n"
            + "例：开启权限后返回首页启动悬浮工具；此权限不会授予 Shizuku 管理能力。",
        "Opens Android's Display over other apps permission. Floating buttons, highlights and"
            + " screenshot editor overlays require it.\n\n"
            + "Example: grant it and start floating tools from Home. This does not grant Shizuku"
            + " privileges.");
    add(
        "accessibilityPermission",
        "打开系统无障碍设置，可启用工具箱的输入与录制服务。页面说明会列出它读取的控件与操作用途。\n\n"
            + "例：需要录制控件点击时开启；不使用录制且 Shizuku 输入可用时可以关闭。第三方服务保持在工具页面单独配置。",
        "Opens system accessibility settings for the toolbox input/recording service, whose"
            + " description explains its control access.\n\n"
            + "Example: enable for recording. It can stay off when recording is unnecessary and"
            + " Shizuku input works. Third-party Keep settings live in Accessibility tools.");
  }

  private SettingsHelp() {}

  public static boolean has(String key) {
    return TEXT.containsKey(key);
  }

  public static String text(String key) {
    return text(key, ToolApp.app.config.english());
  }

  public static String text(String key, boolean english) {
    Copy c = TEXT.get(key);
    if (c == null) throw new IllegalArgumentException("Missing help: " + key);
    return english ? c.en : c.zh;
  }

  public static String option(Config.Option o) {
    return text(o.key())
        + ToolApp.t("\n\n范围：", "\n\nRange: ")
        + o.min()
        + "–"
        + o.max()
        + ToolApp.t("；默认：", "; default: ")
        + o.initial()
        + ".";
  }

  public static String action(String id) {
    return switch (id) {
      case "none" ->
          ToolApp.t(
              "此位置不执行动作。功能球保留在编辑预览中，运行时显示为未分配。",
              "No action in this slot. It remains visible in the editor and inactive at runtime.");
      case "screenshot" ->
          ToolApp.t(
              "先冻结主屏图片和控件树，再编辑显示哪些控件。长按此功能球打开延迟转盘。",
              "Freeze the image and control tree, then edit visibility. Hold this ball to open the"
                  + " delay wheel.");
      case "cancelScreenshot" ->
          ToolApp.t(
              "取消正在等待的单张截图倒计时，不删除已经保存的图片。",
              "Cancel a pending screenshot countdown without deleting saved images.");
      case "capture" ->
          ToolApp.t(
              "启动长截图会话并展开采集工具栏；可以手动或自动采集。",
              "Start a long-capture session and its manual/automatic capture toolbar.");
      case "frame" ->
          ToolApp.t(
              "把当前画面追加为长截图的一帧；尚无会话时自动创建。",
              "Append the current screen as a frame, creating a session if needed.");
      case "auto" ->
          ToolApp.t(
              "按已设置的滚动模式连续采集，达到上限或重复页面后暂停。",
              "Capture repeatedly in the selected mode, pausing at the cap or repeated frames.");
      case "finish" ->
          ToolApp.t(
              "结束采集并拼接导出；根据设置自动把各分图保存到相册。",
              "Stitch and export collected frames, saving split images to the gallery when"
                  + " enabled.");
      case "cancelCapture" ->
          ToolApp.t(
              "取消当前长截图并清理本次缓存，不影响以前导出的文件。",
              "Cancel the current session and its temporary frames; existing exports stay intact.");
      case "tree" ->
          ToolApp.t(
              "在当前可访问控件树中勾选长截图区域或切换排除控件。",
              "Choose long-capture regions or exclusions from the current accessible control"
                  + " tree.");
      case "captureSettings" ->
          ToolApp.t(
              "展开截图快捷滑条和开关，用于调整等待、裁剪和图片格式。",
              "Open quick sliders and switches for waits, cropping and format.");
      case "mode" ->
          ToolApp.t(
              "在跳屏、慢滑和手动采集之间循环切换，并更新工具栏。",
              "Cycle Page, Smooth and Manual capture modes and update the toolbar.");
      case "snapshot" ->
          ToolApp.t(
              "直接导出屏幕图片和控件元数据为 .gatpage 页面包，不打开编辑器。",
              "Export screenshot and metadata directly as a .gatpage archive without opening the"
                  + " editor.");
      case "find" ->
          ToolApp.t(
              "输入文字，在可访问控件中搜索、滚动定位并短暂高亮。",
              "Search accessible text, scroll to matches and briefly highlight them.");
      case "park" ->
          ToolApp.t(
              "直接把当前主屏前台焦点应用的任务移到隐藏屏幕；不弹出选择列表。",
              "Move the currently focused primary-display app to a hidden display directly, without"
                  + " a picker.");
      case "tasks" ->
          ToolApp.t(
              "显示已移入隐藏屏幕的应用卡片，点击恢复到主屏。",
              "Show parked app cards and restore one to the primary display.");
      case "info" ->
          ToolApp.t(
              "展开信息本分类列表，选择条目后填写当前或稍后获得焦点的输入框。",
              "Open notebook folders and fill the current or next focused editable field.");
      case "infoAdd" ->
          ToolApp.t(
              "快速添加普通、隐私或 MFA 条目到信息本根分类。",
              "Quick-add text, private information or an MFA account to the notebook root.");
      case "record" ->
          ToolApp.t(
              "首次触发开始录制控件操作，再次触发停止并保存脚本。需要无障碍服务。",
              "Start explicit control recording; trigger again to stop and save. Requires"
                  + " accessibility.");
      case "macro" ->
          ToolApp.t("从已保存脚本中选择一个，确认后运行。", "Choose a saved script and confirm its execution.");
      case "stopMacro" ->
          ToolApp.t(
              "请求停止正在运行的脚本；已发出的系统操作在有限超时内结束。",
              "Request script cancellation; an in-flight system operation finishes within its"
                  + " timeout.");
      case "services" ->
          ToolApp.t(
              "查看第三方无障碍服务并控制启用和保持状态。",
              "Manage installed accessibility services and their Keep settings.");
      case "back" ->
          ToolApp.t("向安卓发送一次返回操作，作用于当前前台应用。", "Send Android Back to the current foreground app.");
      case "dock" ->
          ToolApp.t(
              "立即把悬浮球收到最近边缘并关闭菜单。", "Immediately dock to the nearest edge and close the menu.");
      case "home" ->
          ToolApp.t(
              "打开工具箱首页，不改变已保存的信息或脚本。",
              "Open toolbox Home without modifying stored information or scripts.");
      case "logs" ->
          ToolApp.t(
              "打开终端日志页，查看操作状态、错误和耗时。",
              "Open terminal logs for operation status, errors and timings.");
      case "stop" ->
          ToolApp.t(
              "关闭悬浮工具，停止保持检查并尝试恢复隐藏任务。",
              "Stop floating tools and monitoring, and attempt to restore parked tasks.");
      default -> throw new IllegalArgumentException("Missing action help: " + id);
    };
  }
}
