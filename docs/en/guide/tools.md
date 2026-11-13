# Tools and settings

## Settings categories and help

Settings are grouped into language/appearance, floating gestures, capture/gallery, search/highlight, notebook/input, macros/accessibility, terminal logs and system permissions. Tap ⓘ beside an option for effects, use cases and examples; long press shows its system tooltip. Numeric help also lists the range and default.

The editor and live menu share five semicircle slots from top to bottom. The center edits double tap. Duplicate actions and unassigned slots are supported. Apply floating layout updates button size and opacity immediately.

Long capture enables gallery auto-save by default, publishing each split image under `Pictures/GAndroidTools`. Android 10+ uses MediaStore without gallery read access; Android 8/9 requires storage write permission when enabled. Disabling auto-save retains only internal exports. Gallery failures also retain internal files and report how many images were saved. The gallery and internal copies are managed separately: deleting one does not remove the other.

## Super background

The floating Park action directly moves the currently focused primary-display app without showing a picker. Inside the toolbox, Choose a running task opens a picker with application titles. Tasks are parked on a public virtual display. The app retains a rendering surface for each parked task, with at most four hidden displays. Task parking requires Android 11 or later. Open **Parked task cards** and tap a card to move it back and focus it. Task state is polled while floating tools run. When the system itself returns a task to display 0 through Recents, its parked record is released at the next poll. Some launchers cannot restore a task across displays; use the restore card in that case. The app does not intercept or replace the system Recents screen. Shutdown restores tracked tasks before removing displays when permitted.

## Screenshot and delay wheel

**Screenshot and edit** first captures screen pixels and immediately retains the control tree, then opens a full-screen overlay editor. Editing uses this in-memory snapshot and never rereads the target page. Tap the preview to toggle its smallest control, or use checkboxes in the frozen tree. Control outlines are optional. Hidden bounds become transparent; page archives clear hidden control text and descriptions and do not include the original unmasked image. Closing the editor releases the original snapshot.

Tap Screenshot to use the default delay. Hold its menu row, or hold the floating button and drag onto Screenshot before releasing, to open the secondary delay wheel. Presets are 0, 1, 3, 5 and 10 seconds, plus a custom delay. The floating button displays the countdown. **Cancel delayed screenshot** stops a pending capture.

Settings let you assign any floating action to a double tap, or choose no action. The default is Screenshot. Single taps wait until the configured double-tap interval expires before opening the menu. The long-press threshold is configurable too.

## Long capture

Start a session, then use **＋** for a manual frame, **▶** for automatic frames, **✓** to export, **⇅** to cycle mode, **▣** for the control tree, **⚙** for settings and **×** to cancel. Pause stops automatic sampling without deleting frames.

- Page mode requests accessible forward scrolling, with a one-screen gesture fallback.
- Smooth mode performs a configurable swipe and waits for rendering.
- Manual mode captures only the frame you request. Scroll the target app yourself.
- Capture controls define the union of checked control bounds. No selection means the full screen.
- Ignored control bounds become transparent on every captured frame.
- Use top and bottom cropping to remove fixed headers and footers. An empty region is rejected.
- Frames are spooled to the app cache. Export uses sampled pixel overlap matching. Unreliable matches start a separate image rather than guessing a seam. Configure the height limit, frame count, PNG/JPEG format and JPEG quality.

## Super find

Searches text and descriptions in accessible nodes. A match requests `ACTION_SHOW_ON_SCREEN`, refreshes bounds and displays a non-touchable highlight. If no match exists, it scrolls accessible containers up to the configured limit. Search starts at the current position and cannot inspect data an application has not exposed.

## Accessibility

Shizuku reads installed services and changes the current Android user's secure accessibility setting. Updates retain unrelated enabled services. A per-service keep switch re-enables it when its component disappears from the setting; it is not a generic crash detector. Keep-alive requires the floating service, a live Shizuku connection and system permission. Disabling a service also removes keep-alive.

## Information notebook

Folders nest without a fixed UI depth limit. Each page loads 100 entries with title search and paging. Tap text to fill; long-press a card to view or delete. Add private text or import an `otpauth://totp` URI. MFA supports SHA1/SHA256/SHA512, 6 or 8 digits and a 5–120 second period. Codes refresh live with a countdown. Encrypted content is revealed for at most 30 seconds per biometric verification. Selecting a snippet closes the panel and waits a configurable time for an editable field. Password fields are excluded.

## Logs

The terminal has module/message keyword filtering, level filtering, pause and automatic follow. Disk logs rotate at 2 MiB with one previous file. Exports include both files. Input values, OTP URIs and script text are excluded from normal operation logs. Exports are user-initiated through Android sharing.

## Controls and outlines

Both single capture and long-capture rules open an image-first frozen preview. Tap the smallest control or hold a location to list every nearby control with checkboxes. The control tree uses its own opaque background, expandable hierarchy, readable titles and localized common types. Unknown application text and IDs retain their original spelling.

Long capture lets you switch between include and exclude rules, preview either selection and apply the rules to subsequent frames. Include bounds are combined; no include selection captures the full display. Hidden or excluded pixels are transparent in PNG. This cannot reconstruct content that was occluded in the original flat screenshot. Transparent masks force PNG even when JPEG is selected. If controls cannot be read, selected control rules stop capture rather than exporting an unmasked frame.

## Updates

Home → Check for updates performs a manual check. Settings → Language and appearance controls automatic checking and the interval in hours (default 24). Checks run when the app opens, not as a persistent background service. Downloads require confirmation and show progress with cancellation. The app verifies SHA-256, size, package, increasing version and the original signing certificate. Android handles the installation permission and confirmation. No GitHub token is embedded.
