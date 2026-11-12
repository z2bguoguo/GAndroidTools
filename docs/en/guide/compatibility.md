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
