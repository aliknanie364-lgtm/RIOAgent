# RIOAgent 1.3.0

Android Java application for:
- User-approved MediaProjection screen capture.
- On-device generic ML Kit object detection.
- Live display of capture and detection status in the activity.
- User-triggered Accessibility tap and swipe tests.
- A user-started, user-configurable repeated macro: joystick drag, punch tap, jump tap.
- Immediate stop control; automation stops when the app activity leaves the foreground.

## Important limitations
The repeated macro is NOT autonomous game AI. It does not understand RIO RISE objectives, enemies, missions, health, obstacles, or success states. The ML Kit detector is generic and is not trained for RIO RISE. Coordinates must be calibrated manually for the device orientation and game UI. Test in a safe area and keep the emergency stop available.

## Build
JDK 17 and Gradle 8.9:
`gradle assembleDebug`

GitHub Actions builds a debug APK artifact on pushes to `main`.
