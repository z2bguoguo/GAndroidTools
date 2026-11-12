# Page archive format

`.gatpage` is a standard ZIP container with a custom extension. Rename it to `.zip` or open it directly in a ZIP tool. It contains UTF-8 JSON and an ordinary PNG image. No executable objects are serialized.

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

Each node includes `key`, `depth`, `text`, `description`, `id`, `class`, `package`, `bounds` (`[left,top,right,bottom]`), `clickable`, `editable`, `scrollable`, `enabled` and `password`. Password text and descriptions are blank. Individual strings, depth, node count and IPC payload are bounded. Very large pages may produce a partial tree; the screenshot remains complete. Snapshots and control metadata are collected sequentially and can differ on animated pages. Long-capture masks are separate from a full page archive. Review both images and JSON before sharing.
