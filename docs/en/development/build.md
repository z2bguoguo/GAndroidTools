# Build and release

JDK 17, Android SDK 35, Build Tools 35.0.0 and Node.js 22 are required. Gradle Wrapper 8.13 is checked in with its distribution checksum.

```sh
./gradlew :app:spotlessApply
./gradlew :app:spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
npm ci
npm run docs:build
python3 scripts/check_repository.py
```

## Persistent signing

Run `python3 scripts/setup_signing.py OWNER/REPO` on a trusted machine with JDK and authenticated GitHub CLI. It creates an RSA-4096 keystore in a private user directory, refuses to overwrite an existing key and uploads the four repository Actions secrets below. Keep its private backup; reuse it for every release. `--reuse` uploads the existing identity. Signing material must never enter git or a public release.

| Secret | Content / 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore / 密钥库 Base64 |
| `ANDROID_STORE_PASSWORD` | Store password / 密钥库密码 |
| `ANDROID_KEY_ALIAS` | Key alias / 别名 |
| `ANDROID_KEY_PASSWORD` | Key password / 密钥密码 |

## Actions

All pushes and pull requests run formatting, unit tests, Android Lint, debug APK builds, documentation validation, workflow syntax checks and secret scanning. Emulator smoke checks cover API 26 and 35. Releases require all jobs to pass. Main-branch commits publish a prerelease by default; **Run workflow** with `stable=true` and an explicit numeric `version` publishes a stable release. Version codes use the monotonically increasing workflow run number. Release builds fail if persistent signing is absent; they never fall back to a debug key. Releases contain a signed APK, checksums and certificate information.

## Wiki

VitePress builds from `docs`, with the base path `/GAndroidTools/` and Chinese/English locales. Enable GitHub Pages with the **GitHub Actions** source, then set repository Actions variable `PAGES_ENABLED=true`. The workflow uploads a Pages artifact on every documentation check and deploys it when enabled. Private repositories may require a qualifying GitHub plan; no visibility change is performed by the workflow.
