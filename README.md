# RIO Agent

Android-native Java project for an on-device RIO RISE farming assistant.

## Current baseline
- Native Java activity and local start/stop session state.
- Gradle Android application configuration.
- GitHub Actions workflow for building and uploading a debug APK.

**Important:** Start/stop currently changes only the app's local session state. It does not capture the screen, analyze game markers, or send touch input.

## Build
Requires JDK 17 and Gradle 8.9:
```bash
gradle assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Planned modules
1. User-approved MediaProjection capture.
2. Red marker detection and path estimation.
3. Tractor position and heading estimation.
4. User-enabled Accessibility control.
5. Decision loop with safe stop conditions.
6. On-device testing and performance tuning.
