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
        PACKAGE, "18", "1.7.1"
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
    print(f"Verified {Path(filename).name}: v1.7.1 (18), exported MainActivity, HOME/DEFAULT and both launcher filters")
