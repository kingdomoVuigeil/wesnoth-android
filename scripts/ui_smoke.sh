#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
capture_diagnostics() {
  adb pull /sdcard/Android/data/io.github.kingdomovuigeil.wesnothmodern/files/screenshots/. screenshots/ || true
  adb logcat -d -t 2000 > screenshots/logcat.txt || true
  adb exec-out screencap -p > screenshots/last-frame.png || true
}
trap capture_diagnostics EXIT
# A compact phone display keeps software rendering affordable on the CI runner.
adb shell wm size 720x1280
adb shell wm density 280
adb install -r dist/wesnoth-android-0.2.0.apk
adb install -r test-apks/app-debug-androidTest.apk
adb shell pm clear io.github.kingdomovuigeil.wesnothmodern
timeout 360 adb shell am instrument -w -e timeout_msec 90000 io.github.kingdomovuigeil.wesnothmodern.test/androidx.test.runner.AndroidJUnitRunner | tee instrumentation.log
grep -Fq 'OK (' instrumentation.log
