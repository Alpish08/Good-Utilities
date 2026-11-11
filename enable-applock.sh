#!/bin/bash
# Re-enables Tools' App lock after an adb reinstall (Android 16 flags sideloaded apps as
# "restricted" and switches their accessibility service off on every install).
set -e
SVC=com.tools.app/com.tools.app.applock.AppLockService
adb shell appops set com.tools.app ACCESS_RESTRICTED_SETTINGS allow
# Keep any other enabled accessibility services; drop ours, then add it back to force a rebind.
OTHERS=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r' | sed 's/^null$//' | tr ':' '\n' | grep -v "AppLockService" | grep . | paste -sd: - || true)
adb shell settings put secure enabled_accessibility_services "${OTHERS:-$SVC}"
sleep 1
adb shell settings put secure enabled_accessibility_services "${OTHERS:+$OTHERS:}$SVC"
adb shell settings put secure accessibility_enabled 1
sleep 1
adb shell dumpsys accessibility | grep -q "label=Good Utilities app lock" && echo "App lock is on" || echo "Not bound yet. Toggle Good Utilities app lock in Settings › Accessibility"
