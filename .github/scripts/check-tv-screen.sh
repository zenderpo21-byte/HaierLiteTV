#!/usr/bin/env bash
set -euo pipefail

adb shell wm size 1920x1080
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.haierlite.tv/.MainActivity

dump_screen() {
  adb shell uiautomator dump /sdcard/haier-ui.xml >/dev/null
  adb exec-out cat /sdcard/haier-ui.xml
}

home=$(dump_screen)
for label in 'Алиса' 'Wi-Fi' 'Bluetooth' 'Настройки' 'ТВ-каналы' 'Приложения' 'Файлы'; do
  grep -F "content-desc=\"$label\"" <<< "$home" >/dev/null
done
if grep -E 'content-desc="(Трансляция|Яндекс ТВ)"' <<< "$home"; then
  exit 1
fi

mkdir -p capture
adb exec-out screencap -p > capture/home.png
python3 - <<'PYPNG'
import struct
from pathlib import Path

with Path('capture/home.png').open('rb') as screenshot:
    assert screenshot.read(8) == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', screenshot.read(16)[8:]) == (1920, 1080)
PYPNG

adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_CENTER
apps=$(dump_screen)
grep -F 'text="Закрыть"' <<< "$apps" >/dev/null
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME
home_activity=$(adb shell dumpsys activity activities | grep -m1 'mResumedActivity' || true)
if [[ -z "$home_activity" || "$home_activity" == *com.haierlite.tv* ]]; then
  echo "System HOME did not come to the foreground: $home_activity"
  exit 1
fi
