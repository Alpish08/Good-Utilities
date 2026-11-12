#!/bin/bash
# Copies Samsung's battery health and lifetime cycle count into Good Utilities' Battery page.
# Android doesn't let apps read Samsung's battery service, so this reads it over adb (the phone must be
# connected with USB debugging) and hands the figures to the app. Run it every few weeks to refresh them.
set -e
REPORT=$(adb shell dumpsys battery | tr -d '\r')
val() { printf '%s\n' "$REPORT" | sed -nE "s/.*$1:[[:space:]]*\[?[[:space:]]*([0-9]+).*/\1/p" | head -1; }
ASOC=$(val mSavedBatteryAsoc)
USAGE=$(val mSavedBatteryUsage)
FIRST=$(val "battery FirstUseDate")
MADE=$(val "LLB MAN")
if [ -z "$ASOC" ]; then echo "This phone doesn't report Samsung's battery health (mSavedBatteryAsoc)."; exit 1; fi
ARGS=(--ei asoc "$ASOC")
[ -n "$USAGE" ] && ARGS+=(--ei usage "$USAGE")
[ -n "$FIRST" ] && ARGS+=(--es first "$FIRST")
[ -n "$MADE" ] && ARGS+=(--es made "$MADE")
# -f 32 (FLAG_INCLUDE_STOPPED_PACKAGES) delivers it even when Good Utilities isn't running.
OUT=$(adb shell am broadcast -f 32 -a com.tools.app.SAMSUNG_BATTERY -n com.tools.app/.battery.SamsungBatterySync "${ARGS[@]}")
if printf '%s' "$OUT" | grep -q 'data="saved"'; then
  echo "Good Utilities: health ${ASOC}%${USAGE:+, $((USAGE / 100)) cycles}"
else
  echo "Good Utilities didn't accept the figures. Is it installed and up to date?"; printf '%s\n' "$OUT"; exit 1
fi
