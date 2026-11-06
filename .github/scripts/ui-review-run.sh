#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner (emulator already booted).
# Usage: ui-review-run.sh <profile-name>
# Deliberately no `set -e`: every phase must run even if an earlier one failed.
set -u

PROFILE="${1:?profile name}"
PKG=nz.durbs.quizzer.debug
ACTIVITY=com.example.MainActivity
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"
MEDIA="/sdcard/Android/media/$PKG"
OUT="ui-review-out/$PROFILE"
mkdir -p "$OUT/shots" "$OUT/logs" "$OUT/monkey"

APK=$(find apks -name '*.apk' ! -name '*androidTest*' | head -1)
TEST_APK=$(find apks -name '*androidTest*.apk' | head -1)
echo "app apk: $APK  test apk: $TEST_APK"

{
  echo "profile=$PROFILE"
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.sdk
  adb shell wm size
  adb shell wm density
} > "$OUT/device.txt" 2>&1

adb shell settings put system font_scale 1.0
adb shell cmd uimode night no
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
adb uninstall "$PKG" > /dev/null 2>&1
adb uninstall "$PKG.test" > /dev/null 2>&1
adb install -r -t "$APK" > "$OUT/logs/install.txt" 2>&1
adb install -r -t "$TEST_APK" >> "$OUT/logs/install.txt" 2>&1
cat "$OUT/logs/install.txt"

# ---------------------------------------------------------------- startup timing
# First launch right after install: this is the one that seeds the 14.5k-question bank into Room.
sleep 20   # let the freshly booted emulator settle so the number is not dominated by boot noise
adb logcat -c
adb shell am start -W -n "$PKG/$ACTIVITY" > "$OUT/startup_first.txt" 2>&1
sleep 5
adb shell run-as "$PKG" ls -l databases > "$OUT/db_after_5s.txt" 2>&1
sleep 30   # give the background seed time to finish before the "warm" launch
adb shell run-as "$PKG" ls -l databases > "$OUT/db_after_35s.txt" 2>&1
adb shell am force-stop "$PKG"
sleep 2
adb shell am start -W -n "$PKG/$ACTIVITY" > "$OUT/startup_second.txt" 2>&1
sleep 5
adb shell am force-stop "$PKG"
sleep 2
adb shell am start -W -n "$PKG/$ACTIVITY" > "$OUT/startup_third.txt" 2>&1
sleep 3
adb shell am force-stop "$PKG"
adb logcat -d > "$OUT/logs/startup_logcat.txt" 2>&1
{
  for n in first second third; do
    echo "== $n launch =="
    grep -E 'LaunchState|ThisTime|TotalTime|WaitTime' "$OUT/startup_$n.txt"
  done
} > "$OUT/startup_summary.txt"
cat "$OUT/startup_summary.txt"

# ---------------------------------------------------------------- screenshot passes
set_mode() { # $1 = night yes/no, $2 = font scale
  adb shell cmd uimode night "$1"
  adb shell settings put system font_scale "$2"
  sleep 3
}

run_pass() { # $1 = prefix, $2 = night, $3 = font scale
  local prefix="$1"
  echo "===== pass $prefix ====="
  set_mode "$2" "$3"
  adb shell am force-stop "$PKG"
  adb shell pm clear "$PKG" > /dev/null 2>&1       # fresh install state for every pass
  adb shell rm -rf "$MEDIA"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
  adb logcat -c
  timeout 1500 adb shell am instrument -w -r \
    -e class com.example.UiReviewTest -e prefix "$prefix" "$RUNNER" \
    > "$OUT/instrumentation_${prefix}raw.txt" 2>&1
  local rc=$?
  echo "instrument exit=$rc" >> "$OUT/instrumentation_${prefix}raw.txt"
  adb shell am force-stop "$PKG.test" > /dev/null 2>&1
  adb logcat -d > "$OUT/logs/${prefix}logcat.txt" 2>&1
  if grep -qE 'FAILURES|Process crashed|INSTRUMENTATION_FAILED|shortMsg' "$OUT/instrumentation_${prefix}raw.txt"; then
    adb exec-out screencap -p > "$OUT/shots/${prefix}INSTRUMENTATION_FAILURE_screen.png" 2>/dev/null
  fi
  adb pull "$MEDIA/." "$OUT/shots/" > "$OUT/logs/${prefix}pull.txt" 2>&1
  tail -3 "$OUT/logs/${prefix}pull.txt"
  ls "$OUT/shots" | grep -c "^${prefix}" || true
}

run_pass light_  no  1.0
run_pass dark_   yes 1.0
run_pass font15_ no  1.5

# ---------------------------------------------------------------- monkey stress test
set_mode no 1.0
adb shell pm clear "$PKG" > /dev/null 2>&1
for seed in 101 202 303; do
  echo "===== monkey seed $seed ====="
  adb shell am force-stop "$PKG"
  adb logcat -c
  timeout 900 adb shell monkey -p "$PKG" --pct-syskeys 0 --throttle 40 -s "$seed" -v 4000 \
    > "$OUT/monkey/monkey_$seed.txt" 2>&1
  echo "monkey exit=$?" >> "$OUT/monkey/monkey_$seed.txt"
  adb logcat -d -v threadtime > "$OUT/monkey/logcat_$seed.txt" 2>&1
  # Excerpt: crashes, ANRs, AndroidRuntime, and error lines that mention the app, with context.
  grep -n -B3 -A15 -E 'FATAL EXCEPTION|ANR in|AndroidRuntime| E .*(nz\.durbs|com\.example)' \
    "$OUT/monkey/logcat_$seed.txt" > "$OUT/monkey/excerpt_$seed.txt" 2>&1
  tail -5 "$OUT/monkey/monkey_$seed.txt"
  wc -l "$OUT/monkey/excerpt_$seed.txt"
done

# Same filter over the screenshot passes' logcat, so crashes during the walk-throughs are not missed.
for f in "$OUT"/logs/*logcat.txt; do
  grep -n -B3 -A15 -E 'FATAL EXCEPTION|ANR in|AndroidRuntime' "$f" > "${f%.txt}_excerpt.txt" 2>&1
done

gzip -f "$OUT"/logs/*logcat.txt "$OUT"/monkey/logcat_*.txt 2>/dev/null
echo "done"
exit 0
