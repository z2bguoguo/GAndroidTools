# GAndroidTools

Native Android toolbox. Java 17, minSdk 26, compile/target SDK 35. Work only in this repository. Preserve existing changes. Public copy must be product-facing, bilingual and accurate about unverified compatibility.

## Source map

`app/src/main/java/io/github/z2bguoguo/gandroidtools` contains native UI and platform code:
- ToolApp, Config, EventLog: application state, bilingual preference metadata, bounded rotating logs.
- UpdateClient, UpdatePolicy: public Pages metadata, bounded downloads, pinned APK verification and Android installation. Never embed credentials, accept arbitrary download hosts or bypass signature/version checks.
- ControlTree, ControlPicker, ControlPreview: provider/session-bound node caches, opaque expandable hierarchy, nearby selection and PNG alpha masks. UiAutomation conflicts must not disconnect other tools. Native accessibility fallback is user enabled.
- Bridge, IRemoteTools AIDL, RemoteService: Shizuku connection and shell-side typed operations.
- SettingsUi, SettingsHelp: categorized native preferences and authored bilingual tooltips. Every setting and configurable action requires semantic help, with examples for numeric parameters.
- SemiCircleLayout, RadialBindings, MoveGesture: shared inward-facing five-slot geometry and tap-then-drag movement. Preserve center double tap.
- GallerySaver: MediaStore pending/publish transactions on API 29+, legacy write permission limited to API 28. Never discard internal exports on gallery failure.
- CallIdentity: clear/restore incoming Binder identity at every Shizuku entry point before local privileged calls.
- OverlayService, Actions, Ui: floating gestures, docking, capture toolbar and reusable native cards.
- FrozenCapture, DelayWheel: image-first frozen screenshot editor and secondary delay wheel. Preserve metadata in memory; never include an unmasked original in an edited export.
- LayerSnapshot, PageArchive: bounded optional API-34 window buffers, honest timing/capability metadata, sanitized version-2 page packs. Never label a composite crop as an independent surface; omit a window buffer if a hidden-control mask cannot be mapped safely.
- ScrollAnalysis, LongComposer: disk-backed two-pass alignment, fixed-edge deduplication, real-pixel history repair and sanitized .gatlong archives. Never invent unseen pixels or stitch ambiguous motion; keep headers once at group start and footers once at group end.
- CaptureSession, OverlapMatcher, Exports: disk-spooled capture, region masks, overlap matching, split exports, .gatpage archives.
- MacroEngine, InputService: Rhino host API, explicit accessible control recording and deferred input.
- InfoStore, InfoUi, AuthActivity, Totp: indexed nested notebook, authenticated AES-GCM and TOTP.
- MainActivity, ServiceUi: native pages, terminal and accessibility settings.
`docs` is bilingual VitePress; `scripts` holds validation, signing setup and CI helpers.

## Invariants

1. Never commit or log credentials, OTP URIs, private snippets, signing material or unreviewed page exports. Do not embed GitHub tokens. No analytics or page-content upload.
2. Keep application ID and signing identity stable. Release packaging must fail without persistent secrets; no debug-key fallback. Version codes strictly increase.
3. Binder payloads, tree size, text, process output and command timeouts remain bounded. Stream screenshots through file descriptors. No arbitrary shell method may be exposed.
4. Preserve enabled accessibility services unrelated to a requested change. Keep-alive only acts on explicitly selected services and stops when the user disables that service.
5. Do not promise immortal processes or universal OEM support. Restore tracked tasks before releasing displays whenever possible; expose operation failures.
6. Private values and OTP secrets require per-operation strong biometric CryptoObject authorization. Never reset an unreadable key. Exclude password values from metadata, input and recording.
7. Rhino denies Java classes and only exposes the documented tool API. Keep instruction deadlines, cancellable waits and platform timeouts. Ambiguous selectors must fail.
8. Keep expensive work off the UI thread. Cancel background operations and listeners with their owning UI/service. Avoid unbounded image accumulation.
9. Floating windows must not be captured in tool screenshots. Input overlays close before deferred insertion. No clipboard-based fallback for private data.
10. README is a polished product introduction with Chinese/English links. Build instructions, compatibility matrices and API details belong in the wiki.

## Validation

Run `./gradlew :app:spotlessApply`, then `./gradlew :app:spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
Run `npm ci && npm run docs:build`, `python3 scripts/check_repository.py`, Python tests and `git diff --check`.
CI runs gitleaks, actionlint and emulator smoke checks on API 26/35. A UI emulator pass is not evidence of Shizuku/OEM compatibility. Record physical device results before expanding the compatibility claim.

## Release

Pushes to main publish prereleases after quality jobs. A manually requested stable release needs an explicit numeric semantic version. Persistent signing uses four ANDROID_* Actions secrets; use scripts/setup_signing.py from a trusted authenticated host. Wiki and public update metadata deploy together after the verified signed release when PAGES_ENABLED=true and Pages uses Actions. Do not change repository visibility automatically.
