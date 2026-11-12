# 开发与发布

需要 JDK 17、Android SDK 35、Build Tools 35.0.0 与 Node.js 22。仓库包含 Gradle Wrapper 8.13 和分发文件校验值。

```sh
./gradlew :app:spotlessApply
./gradlew :app:spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
npm ci
npm run docs:build
python3 scripts/check_repository.py
```

## 固定签名

在安装 JDK 且 GitHub CLI 已登录的可信电脑上运行 `python3 scripts/setup_signing.py OWNER/REPO`。脚本在用户私有目录创建 RSA-4096 密钥库，不覆盖已有密钥，并设置下面四个仓库 Actions Secrets。妥善保管私有备份，后续版本始终复用同一身份；`--reuse` 上传已有签名。签名材料不得提交到 git 或公开 Release。

| Secret | Content / 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore / 密钥库 Base64 |
| `ANDROID_STORE_PASSWORD` | Store password / 密钥库密码 |
| `ANDROID_KEY_ALIAS` | Key alias / 别名 |
| `ANDROID_KEY_PASSWORD` | Key password / 密钥密码 |

## Actions

所有 push 和 PR 运行格式检查、单元测试、Android Lint、Debug APK 构建、文档检查、工作流语法检查与敏感信息扫描；模拟器冒烟检查覆盖 API 26 和 35。发布需要全部检查通过。main 分支提交默认发布预览版；在 **Run workflow** 中指定 `stable=true` 与数字语义版本 `version` 可发布正式版。Android 版本号使用递增的工作流运行序号。固定签名缺失时 Release 构建直接失败，不使用 Debug 密钥代替。Release 包含签名 APK、校验值与证书信息。

## Wiki

VitePress 从 `docs` 构建，基础路径为 `/GAndroidTools/`，提供中英文入口。在仓库设置中将 GitHub Pages 来源设为 **GitHub Actions**，再设置 Actions 变量 `PAGES_ENABLED=true`。工作流每次文档检查均上传 Pages 构件，启用后自动部署。私有仓库可能需要对应 GitHub 套餐；工作流不修改仓库可见性。

发布前会校验密钥库中的私钥证书是否与 `signing-certificate.sha256` 一致。若 `ANDROID_KEY_ALIAS` 填写错误，但密钥库中存在唯一匹配的私钥，工作流会使用经过证书校验的别名并给出警告；不匹配或有歧义时停止。密钥密码仍必须正确，工作流不会生成或替换签名身份。

项目原创代码采用 **GPL-3.0-or-later**（GNU GPL 第 3 版或更新版本），完整许可见仓库 `LICENSE`，授权声明见 `NOTICE`。应用首页可离线查看许可；发布附件同时提供许可声明。第三方组件保留各自许可，见 `THIRD_PARTY_NOTICES.md`。
