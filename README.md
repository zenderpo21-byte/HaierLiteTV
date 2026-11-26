# Haier Lite TV v1.7.4 — High School DxD Edition

An Android TV 11 launcher with a Full HD DxD Edition layout, separate wallpaper, a scrollable row of installed app icons on the left half of the screen, and shortcuts for TV channels, applications and files. Open the applications grid or the Applications settings to manage favorites. Use the gear, MENU or SETTINGS to open settings, and Back to close the panel.

The clock uses the device time zone. Yekaterinburg weather uses Open-Meteo, refreshes while the launcher is visible and labels cached readings when they are stale or a request fails. Brightness applies to the launcher window. The sleep timer shows a reminder while the launcher is visible or when it next opens after expiry. The launcher does not send a power-off command.

GitHub Actions validates the merged manifest, the manifest inside the APK, and the HOME candidates on Android TV 11.

## Yandex TV and Alice

Open Haier Lite TV from the Yandex TV apps list. Haier Lite TV is available in the system Home app chooser. Keep the Yandex launcher selected to continue using it for Home. The app leaves remote microphone and assistant keys to the system. Alice continues to use the device's system integration. The APK checks cover the manifest and TV entry points. Verify Home and Alice on the target TV after installing.

## Boot diagnostics

After `BOOT_COMPLETED`, the app checks at 2, 5 and 8 seconds while it is selected as HOME. It requests launch only when it can verify that the stock Yandex Home activity is in front, with no active playback or microphone capture. After the first request it checks at +5, +10, +20 and +30 seconds and retries only if the stock Yandex Home activity returned. Interacting with Haier Lite TV or detecting media, microphone capture or another foreground activity ends the checks. It does not draw any overlays. Android 11 firmware can reject the request.

On the target TV, grant the optional special access using system settings if available, or through authorized ADB without root:

```sh
adb shell appops set com.haierlite.tv GET_USAGE_STATS allow
adb shell appops set com.haierlite.tv SYSTEM_ALERT_WINDOW allow
adb reboot
adb logcat -d -s HaierBoot
```

A `MainActivity received the boot launch` entry confirms that the request reached the activity. Without the optional access, the normal HOME button continues to work.

## Signing and updates

v1.7.4 uses the same permanent signing certificate as the published v1.7.3 APK. Keep an offline backup of the private keystore and configure it through Actions secrets `HAIER_KEYSTORE_BASE64`, `HAIER_KEYSTORE_PASSWORD`, `HAIER_KEY_ALIAS` and, if different from the keystore password, `HAIER_KEY_PASSWORD`. The workflow checks the permanent certificate fingerprint before uploading a release APK.
