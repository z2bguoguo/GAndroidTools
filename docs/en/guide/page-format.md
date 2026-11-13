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

## Version 2 layered pages

Version 2 keeps `screen.png` and `controls.json` and adds `layers.json`. The layer kinds are `display-composite`, `window-surface` and `composite-fragment`. A window record has its Android ID, Z order, bounds, status, buffer dimensions and available platform timestamp. `layers/window-<id>.png` is present only when that independent buffer was captured and can be masked safely. A fragment has a crop rectangle and source reference; it is not a separate backing surface. Count and cumulative pixel budgets can omit images while retaining metadata.

`atomic:false` explicitly means that the primary image, control traversal and window callbacks are not guaranteed to be the exact same rendered frame. Nanosecond elapsed-time bounds and platform screenshot timestamps describe the capture span. `windowId` links controls to window records. Hidden controls have their content removed and their corresponding window pixels masked; uncertain mask mappings omit that buffer entirely. No unmasked primary original is included.

`.gatlong` is a ZIP-readable `gandroidtools.long-capture` version 1 container. `frames/frame-N.png` contains a processed frame; its JSON retains bounded metadata and coordinate-region mapping. `analysis.json` records document offsets, image groups, fixed top/bottom extents, occlusion masks and repaired/unobserved pixel counts. Header policy is `first-in-group`, footer policy is `last-in-group`, and fill policy is `observed-pixels-only`. Raw original window buffers are not retained in this history format.
