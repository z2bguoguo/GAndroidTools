# Get started

## Requirements

Android 8.0 or later, Shizuku API 13, and an active Shizuku service started with ADB or root. Availability of hidden system APIs depends on firmware.

1. Install an APK from Releases. The preview channel is published after quality checks; a stable build is a manual release.
2. Start Shizuku, open Home and select **Connect / authorize Shizuku**.
3. Grant **Display over other apps**, then start floating tools. A visible notification provides a stop action.
4. Switch to the target application. Hold the floating button, drag toward an action and release.
5. For control recording, enable **GAndroidTools · Input and macro recording** in system accessibility settings. Information filling can use either this bridge or Shizuku automation.

## Floating gestures

Five action balls open on the inward-facing semicircle: rightward on the left edge, leftward on the right edge. Hold until the threshold, drag toward an action and release. Tap once, then press and drag to move; an unarmed short drag does not move the button. Double tap runs the center binding.

Under **Settings → Floating button and gestures**, tap outer balls to assign fixed positions and the center to set double tap. Preview sides demonstrate mirroring; the live side follows button position. Zero disables automatic docking; Dock now remains available.

## Privacy

Captured pages and scripts can contain information from other applications. Review exports before sharing. Password-field text is excluded from control metadata and recording, but visible screenshot pixels may still include sensitive material. Private notebook entries require strong biometrics; changing enrolled biometrics may invalidate their encryption key. Uninstalling the app removes its local data and key.
