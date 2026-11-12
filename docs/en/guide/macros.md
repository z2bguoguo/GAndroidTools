# JavaScript macro API

Macros run in the complete Rhino JavaScript interpreter with Java class access disabled. Only the `tool` host API is exposed; arbitrary shell commands, network requests and filesystem access are not provided. Interpreted execution has a configurable deadline and instruction checks. Stop is cooperative for already-issued platform commands, which also have bounded timeouts. Run scripts only after reviewing them.

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

Selectors match exact `id`, `text`, `class`, `package` and/or `description` fields. Every supplied field must match. Ambiguous selectors fail instead of choosing an arbitrary control. Controls are resolved from a fresh page tree for each action.

```js
const target = { package: 'com.example.app', id: 'com.example.app:id/search' };
if (tool.find(target)) {
  tool.click(target);
  tool.text(target, 'hello');
  tool.wait(400);
}
```

## Recording

Enable the input accessibility service, start recording explicitly, interact with an application, then use the same floating action to stop and save. Click, long-click, forward-scroll and optional text-change events are recorded with delays and semantic selectors. Password fields and the toolbox's own windows are excluded. Text recording is off by default. Coordinate gestures and custom canvas interactions without accessible metadata require handwritten `tap`/`swipe` scripts. A recording is capped at 1,000 events.
