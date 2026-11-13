# Compatibility and validation

| Capability | Boundary |
| --- | --- |
| App UI | Android 8.0+, Chinese and English |
| Shizuku user service | Shizuku API 13, live authorization |
| Virtual displays | Hidden task APIs and multi-display support; firmware-dependent |
| Recents return | Detects an actual system move to display 0; cannot force launcher support |
| UI tree and input | UiAutomation connection allowed by firmware; optional accessibility input bridge |
| Find / control macros | Accessible nodes only; Canvas, games and some WebViews may expose little metadata |
| Capture | Capturable primary display; protected content may be blank or refused |
| Private notebook | Android Keystore and enrolled strong biometrics |
| Accessibility keep-alive | Restores removed enabled settings, not every possible service crash |

The CI device matrix covers API 26 and 35 for app launch, navigation, language changes and notebook operations. Unit tests cover overlap matching and RFC TOTP vectors, plus macro deadline/cancellation and selector ambiguity. A passing emulator UI run does not validate Shizuku authorization, OEM display migration, Recents restoration or third-party app behavior. Those require physical-device verification.

Virtual displays improve the opportunity for apps to remain rendering but do not override low-memory policy, battery restrictions, OEM process management or application lifecycle choices. No “never killed” guarantee is made.

The shell user service reuses the Android runtime provided by Shizuku. Context creation and UiAutomation connection fail independently. Initialization logs include the failing stage and underlying exception stack, with a 30-second connection retry cooldown. Screenshots first use UiAutomation, falling back to the shell `screencap` command with bounded output and a five-second timeout. A frozen capture remains editable as a plain image if its controls are unavailable; the preview and archive metadata explicitly mark this condition. Long captures can use full-screen frames and gesture scrolling, but stop if selected include/exclude control rules cannot be applied.

CI additionally launches the debug-only shell probe through `adb shell app_process` on both API levels to check runtime reuse, a UID-2000 UiAutomation connection, frozen image/tree transfer, and the independent screenshot fallback. This is not a test of the Shizuku authorization handshake or OEM firmware.

UiAutomation is a platform-wide exclusive connection. An “already registered” error is reported as BUSY and never causes another tool to be disconnected. Enable Control fallback on Home to explicitly enable this app’s accessibility service through Shizuku. With that service connected, control trees, frozen control metadata, find, node macros and input can use the native provider while shell capture continues independently. A competing tool suppressing all accessibility services must release its connection first. Retry control connection reattempts registration without destroying parked tasks. Node keys are bound to their provider and snapshot generation; stale keys cannot silently act through another provider.

Layered pages use public per-window screenshot APIs only on Android 14+ with the connected accessibility bridge. Earlier versions retain bounded metadata and composite crops. A window is not an individual View or CSS layer. Capture timing is reported rather than claiming one atomic frame. Protected or unreadable windows are omitted. Fixed-edge and control detection is conservative; animated, repeated, inaccessible or independently scrolling content can require manual masks/crops or separate images. History cannot recover pixels never observed in a valid donor frame.
