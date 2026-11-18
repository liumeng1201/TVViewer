#!/usr/bin/env bash
set -euo pipefail

app_id=com.eanyatonic.cctvViewer
report_dir="$PLAYBACK_REPORT_DIR"
mkdir -p "$report_dir"

collect_reports() {
    if adb exec-out run-as "$app_id" tar -c -C files/live-playback-smoke . > "$report_dir/device-reports.tar"; then
        tar -xf "$report_dir/device-reports.tar" -C "$report_dir"
    fi
    rm -f "$report_dir/device-reports.tar"
    adb logcat -d -v threadtime -s 'LivePlaybackSmoke:I' 'ChannelPlayback:W' 'AndroidRuntime:E' '*:S' \
        > "$report_dir/logcat.txt" || true
}
trap collect_reports EXIT

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --stacktrace
adb install -t app/build/outputs/apk/debug/app-debug.apk
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c

adb shell am instrument -w -r \
    -e annotation "$app_id.LivePlayback" \
    -e livePlayback true \
    -e startupSeconds "$STARTUP_SECONDS" \
    "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tee "$report_dir/instrumentation.txt"
grep -Eq '^[[:space:]]*OK \(3 tests\)[[:space:]]*$' "$report_dir/instrumentation.txt"
