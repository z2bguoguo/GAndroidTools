# 页面快照格式

`.gatpage` 使用标准 ZIP 容器和自定义后缀。可直接通过 ZIP 工具打开，或改名为 `.zip` 后解压；内容为 UTF-8 JSON 与普通 PNG 图片，不包含序列化可执行对象。

| File | Content / 内容 |
| --- | --- |
| `manifest.json` | Format ID, schema version, timestamp, dimensions and paths / 格式、版本、时间、尺寸和文件路径 |
| `controls.json` | Flat node tree with depth and stable path in this snapshot / 带深度与本次快照路径的平铺控件树 |
| `screen.png` | Primary-display screenshot / 主屏截图 |

```json
{
  "format": "gandroidtools.page",
  "version": 1,
  "image": "screen.png",
  "controls": "controls.json",
  "passwordTextExcluded": true
}
```

节点包含 `key`、`depth`、`text`、`description`、`id`、`class`、`package`、`bounds`（`[左,上,右,下]`）、`clickable`、`editable`、`scrollable`、`enabled`、`password`。密码文本及描述为空。单字段长度、树深度、节点数与 IPC 数据量均有上限，极大页面可能只返回部分控件树，截图仍保留整屏。截图和元数据顺序获取，动态页面的两者可能不完全同步。长截图的遮罩设置不作用于完整页面快照；分享前请同时检查图片和 JSON。

## 第 2 版图层包

第 2 版保留 `screen.png` 和 `controls.json`，新增 `layers.json`。图层类型为 `display-composite`（合成图）、`window-surface`（真实窗口缓冲图像）和 `composite-fragment`（合成图控件裁剪）。窗口记录包含 Android 窗口 ID、层级 Z、屏幕范围、状态、缓冲尺寸及可用平台时间戳。仅取得独立缓冲且能安全遮罩时，才写入 `layers/window-<id>.png`。片段记录裁剪范围和来源，不代表独立底层 Surface。图像数量与累计像素预算可能省略图片但保留元数据。

`atomic:false` 明确说明主图、控件遍历和窗口回调不保证属于完全相同的渲染帧；elapsed-time 纳秒范围及平台截图时间戳用于描述捕获跨度。控件的 `windowId` 关联窗口记录。隐藏控件的文本会清空，对应窗口图像也会遮罩；坐标不能安全对应时省略该窗口图像。不会附带未经遮罩的主图原件。

`.gatlong` 是可用 ZIP 工具打开的 `gandroidtools.long-capture` 第 1 版容器。`frames/frame-N.png` 是已处理帧，旁边 JSON 记录有限元数据与画面区域坐标；`analysis.json` 记录正文位移、图像分组、固定顶底范围、遮挡范围及已补全／未观察到的像素数量。策略为每组开头保留顶栏、末尾保留底栏，仅使用真实观察像素补全。历史包不保留原始窗口缓冲图像。
