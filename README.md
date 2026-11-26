# Haier Lite TV v1.7.2 — High School DxD Edition

An Android TV 11 launcher with a Full HD DxD Edition layout, separate wallpaper, a scrollable row of installed app icons on the left half of the screen, and shortcuts for TV channels, applications and files. Open the applications grid or the Applications settings to manage favorites. Use the gear, MENU or SETTINGS to open settings, and Back to close the panel.

The clock uses the device time zone. Yekaterinburg weather uses Open-Meteo, refreshes while the launcher is visible and labels cached readings when they are stale or a request fails. Brightness applies to the launcher window. The sleep timer shows a reminder while the launcher is visible or when it next opens after expiry. The launcher does not send a power-off command.

GitHub Actions validates the merged manifest, the manifest inside the APK, and the HOME candidates on Android TV 11.

## Yandex TV and Alice

Open Haier Lite TV from the Yandex TV apps list. Haier Lite TV is available in the system Home app chooser. Keep the Yandex launcher selected to continue using it for Home. The app leaves remote microphone and assistant keys to the system. Alice continues to use the device's system integration. The APK checks cover the manifest and TV entry points. Verify Home and Alice on the target TV after installing.

## Boot diagnostics

Android sends `BOOT_COMPLETED` after startup. When Haier Lite TV is selected as HOME, it schedules one attempt after eight seconds. If the display is interactive or media is active, it skips the attempt to avoid taking over the screen. Android 11 can also reject background activity launches. Inspect the receiver, scheduling and actual activity entry with `adb logcat -d -s HaierBoot`.

## Updating an existing installation

An update requires the original signing key. GitHub Actions publishes **HaierLiteTV-1.7.2** only when the APK certificate matches the v1.7 build. Configure the original keystore through Actions secrets `HAIER_KEYSTORE_BASE64`, `HAIER_KEYSTORE_PASSWORD`, `HAIER_KEY_ALIAS` and, if different from the keystore password, `HAIER_KEY_PASSWORD`.

Without the original key, Actions publishes **HaierLiteTV-1.7.2-diagnostic** for testing on a clean emulator or device. You can compare an installed APK's certificate with `apksigner verify --print-certs`.
