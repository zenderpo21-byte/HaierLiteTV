# Haier Lite TV v1.7 — High School DxD Edition

A Java launcher for Android TV 11 with a burgundy theme with an optional graphite background and one centered row of original app icons. Use the plus tile or the Applications settings to manage favorites. Open settings with the gear, MENU or SETTINGS, and close the panel with Back.

The clock uses the device time zone. Yekaterinburg weather uses Open-Meteo, refreshes while the launcher is visible and labels cached readings when they are stale or a request fails. Brightness applies to the launcher window. The sleep timer shows a reminder while the launcher is visible or when it next opens after expiry. The launcher does not send a power-off command.

GitHub Actions publishes a signed `app-debug.apk` under **Artifacts → HaierLiteTV-debug-APK**. The workflow validates the Android TV entry point, API level, version and native ABI compatibility. No native code is needed.

## Yandex TV and Alice

Open Haier Lite TV from the Yandex TV apps list. The Home button is handled by the system launcher. The app does not register as a HOME activity or intercept remote microphone and assistant keys. Alice continues to use the device's system integration. The APK checks cover the manifest and TV entry points. Verify Home and Alice on the target TV after installing.

## Updating an existing installation

The GitHub runner uses its generated debug key. An existing installation can be updated in place only with a compatible signing key. Compare certificates with `apksigner verify --print-certs` before installing. If the signing keys differ, keep a copy of the current APK before reinstalling. A clean reinstall clears local settings and favorites.
