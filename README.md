# Haier Lite TV v1.4 Pink Edition

Android TV 11 launcher with a pastel pink mountain backdrop and one centered horizontal row of 164 dp square favorites. Use the plus tile to choose favorites. All apps and settings are available from the top menu.

The sleep timer offers 10, 20, 30, 40, 60, 90, 120 and 240 minutes. It displays a reminder while the launcher process is alive. It does not power off the TV.

GitHub Actions builds and verifies `app-debug.apk`. Download the ZIP from the successful run under **Artifacts → HaierLiteTV-debug-APK** and extract the APK.

```bat
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r app-debug.apk
adb -s TV_IP:5555 shell am start -n com.haierlite.tv/.MainActivity
```

Replace `TV_IP` with the TV's IP address. Updating an installed copy in place requires the same signing key. GitHub-hosted runners generate a debug key unless a fixed signing key is configured. No system launcher changes are required.
