#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == --verify-boot ]]; then
  if ! timeout 45 bash -c '
    until adb logcat -d -s HaierBoot:I | grep -F "Boot check window finished" >/dev/null; do
      sleep 2
    done
  '; then
    adb logcat -d -s HaierBoot:I ActivityManager:W
    exit 1
  fi
  boot_log=$(adb logcat -d -s HaierBoot:I)
  grep -F 'BOOT_COMPLETED received' <<< "$boot_log"
  grep -F 'Boot check 1/3' <<< "$boot_log"
  grep -E 'foreground activity is not the stock Yandex HOME screen|no current foreground activity' <<< "$boot_log"
  if grep -F 'Boot launch requested' <<< "$boot_log"; then
    echo "Boot receiver must not take over on a non-Yandex foreground activity"
    exit 1
  fi
  exit 0
fi

# Wait for the input service on older Android TV images after sys.boot_completed flips.
for attempt in {1..15}; do
  if adb shell input keyevent KEYCODE_WAKEUP; then
    break
  fi
  if [[ "$attempt" == 15 ]]; then exit 1; fi
  sleep 2
done
adb shell wm size 1920x1080
adb install "${1:-app/build/outputs/apk/debug/app-debug.apk}"
home_candidates=$(adb shell cmd package query-activities --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT)
grep -F 'com.haierlite.tv/.MainActivity' <<< "$home_candidates"
if ! grep -E '^[[:space:]]*[^[:space:]/]+/[^[:space:]]+' <<< "$home_candidates" | grep -vF 'com.haierlite.tv/'; then
  echo "System launcher is missing from the HOME candidates"
  exit 1
fi
adb shell am start -W -n com.haierlite.tv/.MainActivity

dump_screen() {
  adb shell uiautomator dump /sdcard/haier-ui.xml >/dev/null
  adb exec-out cat /sdcard/haier-ui.xml
}

home=$(dump_screen)
mkdir -p capture
adb exec-out screencap -p > capture/home.png
python3 - <<'PYPNG'
import struct
from pathlib import Path

with Path('capture/home.png').open('rb') as screenshot:
    assert screenshot.read(8) == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', screenshot.read(16)[8:]) == (1920, 1080)
PYPNG

for label in 'Алиса' 'Wi-Fi' 'Bluetooth' 'Настройки' 'ТВ-каналы' 'Приложения' 'Файлы'; do
  if ! grep -F "content-desc=\"$label\"" <<< "$home" >/dev/null; then
    echo "Home control missing from the accessibility tree: $label"
    exit 1
  fi
done
if grep -E 'content-desc="(Трансляция|Яндекс ТВ|Открыть ТВ-каналы|Все приложения)"' <<< "$home"; then
  exit 1
fi

if ! grep -E 'class="android.widget.HorizontalScrollView"[^>]*bounds="\[83,575\]\[960,805\]"' <<< "$home" >/dev/null; then
  echo "Installed app shelf must end in the middle of the Full HD display"
  exit 1
fi

adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_RIGHT
selected=$(dump_screen)
if ! grep -E 'content-desc="Приложения"[^>]*focused="true"' <<< "$selected" >/dev/null; then
  echo "D-pad did not focus the center Applications control"
  exit 1
fi
adb shell input keyevent KEYCODE_DPAD_CENTER
apps=$(dump_screen)
if ! grep -F 'text="Приложения"' <<< "$apps" >/dev/null; then
  echo "Applications dialog did not open"
  exit 1
fi
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME
home_activity=$(adb shell dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' || true)
if [[ -z "$home_activity" || "$home_activity" == *com.haierlite.tv* ]]; then
  echo "System HOME did not come to the foreground: $home_activity"
  exit 1
fi

# Exercise the real BOOT_COMPLETED delivery on Android TV 11. This image has no Yandex firmware.
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.haierlite.tv
adb shell appops set com.haierlite.tv GET_USAGE_STATS allow
adb shell appops set com.haierlite.tv SYSTEM_ALERT_WINDOW allow
