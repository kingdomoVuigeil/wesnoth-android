#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
trap 'adb pull /sdcard/Android/data/io.github.kingdomovuigeil.wesnothmodern/files/screenshots/. screenshots/ || true' EXIT
adb install -r dist/wesnoth-android-0.2.0.apk
adb install -r test-apks/app-debug-androidTest.apk
adb shell pm clear io.github.kingdomovuigeil.wesnothmodern
adb shell am instrument -w io.github.kingdomovuigeil.wesnothmodern.test/androidx.test.runner.AndroidJUnitRunner | tee instrumentation.log
grep -Fq 'OK (' instrumentation.log
