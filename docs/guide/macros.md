# JavaScript 宏 API

宏使用完整的 Rhino JavaScript 解释器，禁止访问 Java 类，只暴露 `tool` 宿主 API，不提供任意 Shell、网络和文件系统访问。解释执行具有可设置的期限与指令检查。已经发出的系统操作需要在其有限超时内结束，再响应停止。运行前请检查脚本。

| API | Result / 作用 |
| --- | --- |
| `tool.find(selector)` | Node summary or `null` / 控件摘要或空值 |
| `tool.click(selector)` | Accessible click / 控件点击 |
| `tool.longClick(selector)` | Accessible long click / 控件长按 |
| `tool.text(selector, value)` | Replace non-password editable text / 设置非密码文本 |
| `tool.scroll(selector)` | Scroll forward / 向前滚动 |
| `tool.tap(x, y)` | Tap screen coordinates / 点击屏幕坐标 |
| `tool.swipe({x1,y1,x2,y2,duration})` | Swipe gesture / 滑动手势 |
| `tool.wait(ms)` | Cancellable wait, at most 60 seconds per call / 可取消等待 |
| `tool.back()` | Android Back / 系统返回 |
| `tool.log()` | Content-free checkpoint in logs / 无正文日志标记 |

选择器精确匹配 `id`、`text`、`class`、`package` 和/或 `description`，提供的所有字段都必须匹配。匹配不唯一时报错，不随机选择控件；每次操作会重新读取控件树。

```js
const target = { package: 'com.example.app', id: 'com.example.app:id/search' };
if (tool.find(target)) {
  tool.click(target);
  tool.text(target, 'hello');
  tool.wait(400);
}
```

## 录制

启用输入无障碍服务，主动开始录制，操作应用，再用同一悬浮按钮停止并保存。录制保存点击、长按、向前滚动及可选的文本变化事件，附带延时和语义选择器。排除密码框与工具箱自身窗口；文本录制默认关闭。不暴露可访问控件的画布操作和坐标手势，需要自行编写 `tap`/`swipe` 脚本。单次录制最多 1,000 个事件。
