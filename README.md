# Haier Lite TV v1.6 Minimal Edition

A Java launcher for Android TV 11 with a graphite background and one centered row of original app icons. Use the plus tile or the Applications settings to manage favorites. Open settings with the gear, MENU or SETTINGS, and close the panel with Back.

The clock uses the device time zone. Yekaterinburg weather uses Open-Meteo, refreshes while the launcher is visible and labels cached readings when they are stale or a request fails. Brightness applies to the launcher window. The sleep timer shows a reminder while the launcher is visible or when it next opens after expiry. The launcher does not send a power-off command.

GitHub Actions publishes a signed `app-debug.apk` under **Artifacts → HaierLiteTV-debug-APK**. The workflow validates the Android TV entry point, API level, version and native ABI compatibility. No native code is needed.

## Updating an active launcher

The GitHub runner uses its generated debug key. An existing installation can be updated in place only with a compatible signing key. Compare certificates with `apksigner verify --print-certs` before installing. Keep the current APK and confirm a working backup HOME is available before considering any clean reinstall. The app does not change the selected HOME.
