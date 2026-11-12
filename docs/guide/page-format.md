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
