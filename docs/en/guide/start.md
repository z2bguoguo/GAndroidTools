# Get started

## Requirements

Android 8.0 or later, Shizuku API 13, and an active Shizuku service started with ADB or root. Availability of hidden system APIs depends on firmware.

1. Install an APK from Releases. The preview channel is published after quality checks; a stable build is a manual release.
2. Start Shizuku, open Home and select **Connect / authorize Shizuku**.
3. Grant **Display over other apps**, then start floating tools. A visible notification provides a stop action.
4. Switch to the target application. Tap the floating button, or hold it and drag onto an action.
5. For control recording, enable **GAndroidTools · Input and macro recording** in system accessibility settings. Information filling can use either this bridge or Shizuku automation.

## Floating gestures

A short drag repositions the button. A tap opens a menu. Hold until the menu appears, drag onto a row and release to activate it. Settings control button size, opacity, delay and the selected action list. Set auto-dock delay to zero to disable it. **Dock now** immediately leaves a small handle at the nearest edge.

## Privacy

Captured pages and scripts can contain information from other applications. Review exports before sharing. Password-field text is excluded from control metadata and recording, but visible screenshot pixels may still include sensitive material. Private notebook entries require strong biometrics; changing enrolled biometrics may invalidate their encryption key. Uninstalling the app removes its local data and key.
