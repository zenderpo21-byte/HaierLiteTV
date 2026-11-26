"""Verify the same entry points in merged XML and in the decoded APK manifest."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "com.haierlite.tv"
MAIN = "android.intent.action.MAIN"
CATEGORY = "android.intent.category."
EXPECTED = {
    frozenset({CATEGORY + "HOME", CATEGORY + "DEFAULT"}),
    frozenset({CATEGORY + "LEANBACK_LAUNCHER"}),
    frozenset({CATEGORY + "LAUNCHER"}),
}

for filename in sys.argv[1:]:
    root = ET.parse(filename).getroot()
    if (root.get("package"), root.get(ANDROID + "versionCode"), root.get(ANDROID + "versionName")) != (
        PACKAGE, "19", "1.7.2"
    ):
        sys.exit(f"{filename}: unexpected package, versionCode or versionName")

    activity = next(
        (node for node in root.findall("./application/activity")
         if node.get(ANDROID + "name") in {".MainActivity", PACKAGE + ".MainActivity"}),
        None,
    )
    if activity is None or activity.get(ANDROID + "exported") != "true":
        sys.exit(f"{filename}: MainActivity must be exported")

    main_filters = {
        frozenset(node.get(ANDROID + "name") for node in intent.findall("category"))
        for intent in activity.findall("intent-filter")
        if {node.get(ANDROID + "name") for node in intent.findall("action")} == {MAIN}
    }
    if not EXPECTED.issubset(main_filters):
        sys.exit(f"{filename}: missing separate MAIN filters: {EXPECTED - main_filters}")
    permissions = {node.get(ANDROID + "name") for node in root.findall("uses-permission")}
    receiver = next(
        (node for node in root.findall("./application/receiver")
         if node.get(ANDROID + "name") in {".BootReceiver", PACKAGE + ".BootReceiver"}),
        None,
    )
    if ("android.permission.RECEIVE_BOOT_COMPLETED" not in permissions
        or receiver is None
        or receiver.get(ANDROID + "exported") != "false"
        or "android.intent.action.BOOT_COMPLETED" not in {
            node.get(ANDROID + "name") for node in receiver.findall("./intent-filter/action")
        }):
        sys.exit(f"{filename}: missing protected BOOT_COMPLETED registration")

    print(f"Verified {Path(filename).name}: v1.7.2 (19), exported MainActivity, HOME/DEFAULT both launcher filters and BOOT_COMPLETED")
