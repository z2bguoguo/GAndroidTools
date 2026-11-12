<p align="center"><a href="README.md">简体中文</a> · <a href="README.en.md">English</a></p>

![GAndroidTools](assets/banner-zh.svg)

<p align="center">
  <a href="https://github.com/z2bguoguo/GAndroidTools/actions/workflows/quality.yml"><img alt="Quality" src="https://github.com/z2bguoguo/GAndroidTools/actions/workflows/quality.yml/badge.svg" /></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-407F75?style=flat-square" />
  <img alt="Preview" src="https://img.shields.io/badge/channel-preview-A6C9E8?style=flat-square" />
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/license-MIT-407F75?style=flat-square" /></a>
</p>

<p align="center"><b>一个清爽、可配置、基于 Shizuku 的安卓工具箱。</b><br />把常用操作放到屏幕边缘，让截图、查找、自动化和信息填写随手可达。</p>

<p align="center"><a href="https://github.com/z2bguoguo/GAndroidTools/releases">下载预览版</a> · <a href="https://z2bguoguo.github.io/GAndroidTools/">使用文档</a> · <a href="docs/guide/start.md">快速开始</a> · <a href="https://github.com/z2bguoguo/GAndroidTools/issues">问题反馈</a></p>

### 随手可达的工具

| 工具 | 用途 |
| --- | --- |
| **超级后台** | 将运行中任务移到独立虚拟屏幕，在任务卡片中恢复到主屏。 |
| **单张截图** | 先冻结画面与控件树，再在覆盖编辑页勾选可见控件；支持延迟转盘。 |
| **超级长截图** | 跳屏、缓慢滑动与手动采集；控件区域选择、忽略遮罩、重叠匹配及长图分段导出。 |
| **超级查找** | 在可访问控件中查找文字，滚动定位并短暂高亮。 |
| **无障碍管理** | 管理已安装无障碍服务，并监测、重新开启被关闭的选定服务。 |
| **超级宏** | 主动录制控件操作，在编辑器中编写、导入和运行 JavaScript 宏。 |
| **页面快照** | 将屏幕图片与控件元数据导出为可用 ZIP 工具解压的 `.gatpage` 文件。 |
| **信息本** | 多层分类、快捷填写、等待输入焦点、加密隐私信息与自动刷新的 MFA 动态码。 |
| **终端日志** | 实时显示、级别和关键词筛选、暂停、自动滚动及轮转日志导出。 |

### 为日常操作设计

- 圆角卡片、薄荷绿与雾蓝配色，支持中文与英文。
- 悬浮菜单支持点击选择、长按拖拽选择、拖动位置、定时贴边与手动收起，并可配置双击动作。
- 可视化勾选悬浮按钮，截图工具栏带快捷滑条与开关。
- 隐私条目与 MFA 密钥使用 Android Keystore 加密，每次查看或使用需强生物识别验证。
- 信息与脚本保存在本地；不包含分析 SDK，也不上传页面内容。

### 开始使用

安装预览版后，启动 [Shizuku](https://shizuku.rikka.app/)，在首页授权连接并开启悬浮窗权限。输入与控件录制还可启用工具箱的无障碍服务。先切到目标应用，再打开悬浮菜单选择工具。

本项目处于预览阶段。任务移屏和控件自动化受 Android 版本、厂商系统及目标应用影响；虚拟屏幕不能保证进程永不终止。受保护画面与未暴露的自绘控件可能无法捕获或定位。兼容性与验证范围见 [功能说明](docs/guide/compatibility.md)。

### 文档与贡献

[使用指南](docs/guide/start.md) · [脚本 API](docs/guide/macros.md) · [页面文件格式](docs/guide/page-format.md) · [开发与发布](docs/development/build.md) · [贡献指南](CONTRIBUTING.md) · [安全说明](SECURITY.md)

采用 [MIT License](LICENSE)。第三方组件许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
