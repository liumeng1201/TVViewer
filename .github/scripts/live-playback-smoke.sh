#!/usr/bin/env bash
set -euo pipefail

app_id=com.eanyatonic.cctvViewer
test_class="$app_id.LivePlaybackSmokeTest"
report_dir="$PLAYBACK_REPORT_DIR"
mkdir -p "$report_dir"

collect_device_reports() {
    if adb exec-out run-as "$app_id" tar -c -C files/live-playback-smoke . > "$report_dir/device-reports.tar"; then
        tar -xf "$report_dir/device-reports.tar" -C "$report_dir"
    fi
    rm -f "$report_dir/device-reports.tar"
}

collect_reports() {
    collect_device_reports
    adb logcat -d -v threadtime -s 'LivePlaybackSmoke:I' 'ChannelPlayback:W' 'AndroidRuntime:E' '*:S' \
        > "$report_dir/logcat.txt" || true
}
trap collect_reports EXIT

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --stacktrace
adb install -t app/build/outputs/apk/debug/app-debug.apk
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c

run_instrumentation() {
    local selection="$1" expected="$2" output="$3"
    local deadline=$((expected * (10#$STARTUP_SECONDS + 75)))
    # Bound the host-side wait if a WebView or the instrumentation process stops responding.
    timeout --kill-after=10s "${deadline}s" adb shell am instrument -w -r \
        -e class "$selection" \
        -e livePlayback true \
        -e startupSeconds "$STARTUP_SECONDS" \
        "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tee "$output" || return
    grep -Eq "^[[:space:]]*OK \\($expected tests?\\)[[:space:]]*$" "$output"
}

if [[ "${COMPARE_DIRECT:-false}" != true ]]; then
    run_instrumentation "$test_class#probeDetectsLocallyRenderedVideo,$test_class#cctv1PlaysFromInternet,$test_class#beijingSatellitePlaysFromYangshipin" \
        3 "$report_dir/instrumentation.txt"
    exit
fi

methods=(
    probeDetectsLocallyRenderedVideo
    cctv1PlaysFromInternet
    cctv1PlaysDirectly
    beijingSatellitePlaysFromYangshipin
    beijingSatellitePlaysDirectly
    cctv1PlaysDirectlyFromYangshipin
)
failed=0
for method in "${methods[@]}"; do
    # Each measurement starts with fresh WebView data on the same virtual device.
    adb shell pm clear "$app_id" | grep -q '^Success'
    if ! run_instrumentation "$test_class#$method" 1 "$report_dir/$method.txt"; then
        failed=1
    fi
    collect_device_reports
done
exit "$failed"
