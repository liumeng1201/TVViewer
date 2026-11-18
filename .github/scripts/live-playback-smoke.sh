#!/usr/bin/env bash
set -euo pipefail

app_id=com.eanyatonic.cctvViewer
test_class="$app_id.LivePlaybackSmokeTest"
report_dir="$PLAYBACK_REPORT_DIR"
mkdir -p "$report_dir"

collect_device_reports() {
    local archive="$report_dir/device-reports.tar"
    if timeout --kill-after=5s 20s adb exec-out run-as "$app_id" tar -c -C files/live-playback-smoke . > "$archive" &&
       tar -tf "$archive" > /dev/null; then
        tar -xf "$archive" -C "$report_dir"
    else
        echo "::warning::Could not retrieve full device reports; recorded instrumentation summaries are retained."
    fi
    rm -f "$archive"
}

collect_reports() {
    collect_device_reports
    timeout --kill-after=5s 15s adb logcat -d -v threadtime -s 'LivePlaybackSmoke:I' 'ChannelPlayback:W' 'AndroidRuntime:E' '*:S' \
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
    local result=0 summary source
    # Bound the host-side wait if a WebView or the instrumentation process stops responding.
    timeout --kill-after=10s "${deadline}s" adb shell am instrument -w -r \
        -e class "$selection" \
        -e livePlayback true \
        -e startupSeconds "$STARTUP_SECONDS" \
        "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tee "$output" || result=$?

    while IFS= read -r summary; do
        source=$(jq -er '.source | select(test("^[a-z-]+$"))' <<< "$summary") || continue
        printf '%s\n' "$summary" > "$report_dir/$source.json"
    done < <(tr -d '\r' < "$output" | sed -n 's/^INSTRUMENTATION_STATUS: tvviewerPlaybackSummary=//p')

    if (( result != 0 )); then
        echo "::warning::Instrumentation exited with status $result."
        return "$result"
    fi
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
    if ! timeout --kill-after=5s 20s adb shell pm clear "$app_id" | grep -q '^Success'; then
        echo "::error::Cannot reset the app before $method; the emulator may be offline."
        failed=1
        break
    fi
    if ! run_instrumentation "$test_class#$method" 1 "$report_dir/$method.txt"; then
        failed=1
    fi
    collect_device_reports
done
exit "$failed"
