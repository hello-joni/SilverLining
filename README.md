# SilverLining

Android app wrapper for [SilverBullet.md](https://github.com/silverbulletmd/silverbullet) so that it
can be used offline.

## Build

Enter the dev shell, then build a debug APK:

```
nix develop
android/gradlew -p android assembleDebug
```

The build downloads the pinned SilverBullet release archive, verifies its SHA256, and packages the
binary into the APK. Output:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

## Install

With the phone plugged in and USB debugging enabled:

```
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Changing SilverBullet version

In `android/gradle.properties`, change `silverbulletVersion` to the desired upstream tag and clear
`silverbulletSha256`. Run a build. It will fail and print the new hash. Paste that into
`silverbulletSha256` and run again.
