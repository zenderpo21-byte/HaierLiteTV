# Haier Lite TV v1.2 — Android TV 11

Lightweight Java Android TV launcher. Package `com.haierlite.tv`. Designed for remote D-pad input; no root required. The workflow builds and verifies a signed debug APK.

## Build without Android Studio

1. Push to `main` or run **Actions → Build Haier Lite TV APK → Run workflow**.
2. Open the successful run and download `HaierLiteTV-debug-APK` under **Artifacts**.
3. Extract `app-debug.apk` and rename it to `HaierLiteTV.apk` if desired.

## Install on Android TV

Replace `TV_IP` with your television’s IP address.

```bat
adb -s TV_IP:5555 install -r HaierLiteTV.apk
adb -s TV_IP:5555 shell am start -n com.haierlite.tv/.MainActivity
```

Test D-pad, app launch, Android settings, and remote Home **before** assigning default:

```bat
adb -s TV_IP:5555 shell cmd package set-home-activity com.haierlite.tv
adb -s TV_IP:5555 shell input keyevent 3
adb -s TV_IP:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Restore Google TV launcher:

```bat
adb -s TV_IP:5555 shell cmd package set-home-activity com.google.android.tvlauncher
adb -s TV_IP:5555 shell input keyevent 3
```

**Limits:** Home selection is controlled by Android, not guaranteed by OEM firmware. This is not kiosk/Device Owner and does not lock users into the app. The internal Exit menu opens HOME settings if supported; closing an actual default HOME may just relaunch it. Do not disable any OEM/system packages. For power-on behavior, Android typically launches the default HOME when it boots to the home screen, but TVs may resume the last input/app instead.
