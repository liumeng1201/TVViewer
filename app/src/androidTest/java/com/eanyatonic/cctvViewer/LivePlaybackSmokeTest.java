package com.eanyatonic.cctvViewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tencent.smtt.export.external.interfaces.WebResourceError;
import com.tencent.smtt.export.external.interfaces.WebResourceRequest;
import com.tencent.smtt.export.external.interfaces.WebResourceResponse;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class LivePlaybackSmokeTest {
    private static final String TAG = "LivePlaybackSmoke";
    private static final long APP_STARTUP_MS = 20_000;
    private static final long OBSERVATION_MS = 30_000;
    private static final long SAMPLE_INTERVAL_MS = 500;
    private static final long MAX_STALL_MS = 2_000;
    private static final double MIN_PLAYING_FRACTION = 0.90;
    private static final double MAX_DROPPED_FRACTION = 0.05;

    @Before
    public void requireOptIn() {
        assumeTrue("Live playback runs only when explicitly enabled",
                "true".equals(InstrumentationRegistry.getArguments().getString("livePlayback")));
    }

    @Test
    @LivePlayback
    public void probeDetectsLocallyRenderedVideo() throws Exception {
        verify("control", "Local control video", ChannelCatalog.byNumber(1),
                ChannelPlaybackTest.PLAYING, 12_000, 4_000, false);
    }

    @Test
    @LivePlayback
    public void cctv1PlaysFromInternet() throws Exception {
        ChannelCatalog.Channel channel = ChannelCatalog.byNumber(1);
        verifyLive("cctv", channel.label(), channel, channel.url, false);
    }

    @Test
    @LivePlayback
    public void beijingSatellitePlaysFromYangshipin() throws Exception {
        ChannelCatalog.Channel channel = ChannelCatalog.byNumber(21);
        verifyLive("yangshipin", channel.label(), channel, channel.url, false);
    }

    @Test
    @LivePlayback
    public void cctv1PlaysDirectly() throws Exception {
        ChannelCatalog.Channel channel = ChannelCatalog.byNumber(1);
        verifyLive("cctv-direct", channel.label() + " · Direct page", channel, channel.url, true);
    }

    @Test
    @LivePlayback
    public void beijingSatellitePlaysDirectly() throws Exception {
        ChannelCatalog.Channel channel = ChannelCatalog.byNumber(21);
        verifyLive("yangshipin-direct", channel.label() + " · Direct page", channel, channel.url, true);
    }

    @Test
    @LivePlayback
    public void cctv1PlaysDirectlyFromYangshipin() throws Exception {
        ChannelCatalog.Channel channel = ChannelCatalog.byNumber(1);
        // An alternate public channel page listed in DongYuTVWeb's bundled channel catalog.
        verifyLive("cctv-yangshipin-direct", channel.label() + " · Yangshipin direct page", channel,
                "https://www.yangshipin.cn/tv/home?pid=600001859", true);
    }

    private void verifyLive(String id, String label, ChannelCatalog.Channel channel, String url,
                            boolean direct) throws Exception {
        verify(id, label, channel, url, startupBudgetMs(), OBSERVATION_MS, direct);
    }

    private void verify(String id, String label, ChannelCatalog.Channel channel, String url,
                        long startupMs, long observationMs, boolean direct) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        File output = new File(instrumentation.getTargetContext().getFilesDir(), "live-playback-smoke");
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IllegalStateException("Cannot create live playback report directory");
        }

        JSONObject report = new JSONObject()
                .put("source", id)
                .put("channel", label)
                .put("playbackPath", direct ? "direct" : "app")
                .put("url", "control".equals(id) ? "local fixture" : url)
                .put("startupBudgetMs", startupMs)
                .put("appTimeoutMs", APP_STARTUP_MS)
                .put("observationMs", observationMs)
                .put("result", "TEST_ERROR");
        JSONArray samples = new JSONArray();
        JSONArray captures = new JSONArray();
        report.put("samples", samples).put("captures", captures);

        try (ProbeSession session = new ProbeSession(instrumentation, startupMs, direct)) {
            session.load(channel, url);
            Snapshot first = awaitFirstFrame(session, samples, startupMs);
            if (first == null) {
                String reason = session.failure;
                report.put("appFailure", reason == null ? JSONObject.NULL : reason);
                String result = reason != null && reason.startsWith("频道页面加载失败")
                        ? "PAGE_LOAD_FAILED" : "NO_VIDEO_FRAME";
                if ("视频播放失败".equals(reason)) {
                    result = "MEDIA_ERROR";
                }
                report.put("result", result);
                captures.put(session.capture(output, id + "-failure", session.latest));
            } else {
                long firstFrameMs = first.at - session.startedAt;
                report.put("firstFrameMs", firstFrameMs)
                        .put("frameEvidence", first.data.isNull("presentedFrames")
                                ? "playback-quality-counter" : "compositor")
                        .put("withinAppTimeout", firstFrameMs <= APP_STARTUP_MS);
                observe(session, first, observationMs, output, id, samples, captures, report);
            }
            report.put("pageReadyMs", session.pageReadyAt == 0
                            ? JSONObject.NULL : session.pageReadyAt - session.startedAt)
                    .put("videoElementMs", session.videoAt == 0
                            ? JSONObject.NULL : session.videoAt - session.startedAt)
                    .put("appReadyMs", session.readyAt == 0
                            ? JSONObject.NULL : session.readyAt - session.startedAt)
                    .put("appReadyWithinDefaultTimeout", direct ? JSONObject.NULL
                            : session.readyAt != 0 && session.readyAt - session.startedAt <= APP_STARTUP_MS);
        } catch (Exception | AssertionError error) {
            report.put("exception", error.toString());
            throw error;
        } finally {
            try (FileOutputStream file = new FileOutputStream(new File(output, id + ".json"))) {
                file.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Bundle status = new Bundle();
            status.putString("tvviewerPlaybackSummary", new JSONObject(report, new String[]{
                    "source", "channel", "url", "playbackPath", "result", "startupBudgetMs",
                    "observationMs", "pageReadyMs", "videoElementMs", "firstFrameMs", "appReadyMs",
                    "withinAppTimeout", "appReadyWithinDefaultTimeout", "frameEvidence",
                    "playingMs", "stallCount", "longestStallMs", "droppedFrameRatio",
                    "visibleVideoRegion", "appFailure", "exception"
            }).toString());
            instrumentation.sendStatus(2, status);
            Log.i(TAG, id + ": " + report.optString("result"));
        }

        assertEquals(report.optString("channel") + " produced " + report.optString("result")
                + "; see " + id + ".json", "PASS", report.optString("result"));
    }

    private static Snapshot awaitFirstFrame(ProbeSession session, JSONArray samples, long budgetMs)
            throws Exception {
        long deadline = session.startedAt + budgetMs;
        while (SystemClock.elapsedRealtime() < deadline && session.failure == null) {
            Snapshot snapshot = session.sample(samples);
            if (snapshot != null && snapshot.at <= deadline && snapshot.hasFrame()) {
                return snapshot;
            }
            SystemClock.sleep(SAMPLE_INTERVAL_MS);
        }
        return null;
    }

    private static void observe(ProbeSession session, Snapshot first, long duration,
                                File output, String id, JSONArray samples, JSONArray captures,
                                JSONObject report) throws Exception {
        long end = first.at + duration;
        PlaybackMetrics metrics = new PlaybackMetrics(first);
        boolean visible = recordCapture(session, first, output, id + "-start", captures);
        boolean midpointCaptured = false;
        while (SystemClock.elapsedRealtime() < end && session.failure == null) {
            SystemClock.sleep(SAMPLE_INTERVAL_MS);
            Snapshot next = session.sample(samples);
            if (next == null) {
                continue;
            }
            metrics.add(next, end);
            if (!midpointCaptured && next.at >= first.at + duration / 2) {
                visible |= recordCapture(session, next, output, id + "-middle", captures);
                midpointCaptured = true;
            }
        }
        metrics.finish(end);
        visible |= recordCapture(session, session.latest, output, id + "-end", captures);

        Double droppedFraction = metrics.totalFrames > 0
                ? (double) metrics.droppedFrames / metrics.totalFrames : null;
        report.put("playingMs", metrics.playingMs)
                .put("stallCount", metrics.stallCount)
                .put("longestStallMs", metrics.longestStallMs)
                .put("droppedFrameRatio", droppedFraction == null ? JSONObject.NULL : droppedFraction)
                .put("visibleVideoRegion", visible)
                .put("appFailure", session.failure == null ? JSONObject.NULL : session.failure);

        String result = "PASS";
        if (session.failure != null) {
            result = "PLAYBACK_INTERRUPTED";
        } else if (!session.direct && session.readyAt == 0) {
            result = "APP_PLAYBACK_NOT_READY";
        } else if (!visible) {
            result = "PICTURE_UNCONFIRMED";
        } else if (metrics.playingMs < duration * MIN_PLAYING_FRACTION
                || metrics.longestStallMs > MAX_STALL_MS
                || (droppedFraction != null && droppedFraction > MAX_DROPPED_FRACTION)) {
            result = "PLAYBACK_NOT_SMOOTH";
        }
        report.put("result", result);
    }

    private static boolean recordCapture(ProbeSession session, Snapshot snapshot, File output,
                                         String name, JSONArray captures) throws Exception {
        JSONObject capture = session.capture(output, name, snapshot);
        captures.put(capture);
        return capture.optBoolean("videoRegionNonBlack");
    }

    private static long startupBudgetMs() {
        String value = InstrumentationRegistry.getArguments().getString("startupSeconds", "90");
        int seconds = Integer.parseInt(value);
        if (seconds < 20 || seconds > 180) {
            throw new IllegalArgumentException("startupSeconds must be between 20 and 180");
        }
        return seconds * 1000L;
    }

    private static final class PlaybackMetrics {
        private Snapshot previous;
        private long coveredUntil;
        private long currentStallMs;
        long playingMs;
        long longestStallMs;
        int stallCount;
        long totalFrames;
        long droppedFrames;

        PlaybackMetrics(Snapshot first) {
            previous = first;
            coveredUntil = first.at;
        }

        void add(Snapshot next, long end) {
            long elapsed = next.at - previous.at;
            long interval = Math.max(0, Math.min(next.at, end) - coveredUntil);
            if (elapsed <= 0 || interval <= 0) {
                return;
            }

            boolean sameVideo = previous.data.optInt("generation", -1)
                    == next.data.optInt("generation", -2);
            double mediaAdvance = (next.data.optDouble("currentTime")
                    - previous.data.optDouble("currentTime")) * 1000;
            boolean progressing = sameVideo && !next.data.optBoolean("paused")
                    && !next.data.optBoolean("seeking") && mediaAdvance > 50
                    && mediaAdvance <= elapsed * 1.5 + 200
                    && next.frameCount() > previous.frameCount();
            if (progressing) {
                playingMs += Math.min(interval, Math.round(mediaAdvance * interval / elapsed));
                endStall();
            } else {
                currentStallMs += interval;
            }

            long total = next.data.optLong("totalFrames", -1) - previous.data.optLong("totalFrames", -1);
            long dropped = next.data.optLong("droppedFrames", -1) - previous.data.optLong("droppedFrames", -1);
            if (sameVideo && !previous.data.isNull("totalFrames") && !next.data.isNull("totalFrames")
                    && !previous.data.isNull("droppedFrames") && !next.data.isNull("droppedFrames")
                    && total >= 0 && dropped >= 0 && dropped <= total) {
                totalFrames += total;
                droppedFrames += dropped;
            }

            coveredUntil += interval;
            previous = next;
        }

        void finish(long end) {
            currentStallMs += Math.max(0, end - coveredUntil);
            endStall();
        }

        private void endStall() {
            longestStallMs = Math.max(longestStallMs, currentStallMs);
            if (currentStallMs >= 1000) {
                stallCount++;
            }
            currentStallMs = 0;
        }
    }

    private static final class Snapshot {
        final long at;
        final JSONObject data;
        final Rect webViewBounds;

        Snapshot(long at, JSONObject data, Rect webViewBounds) {
            this.at = at;
            this.data = data;
            this.webViewBounds = webViewBounds;
        }

        boolean hasFrame() {
            return data.optInt("width") > 0 && data.optInt("height") > 0 && frameCount() > 0;
        }

        long frameCount() {
            return data.optLong("presentedFrames",
                    data.optLong("totalFrames", 0) - data.optLong("droppedFrames", 0));
        }
    }

    private static final class ProbeSession implements AutoCloseable {
        private final Instrumentation instrumentation;
        private final ActivityScenario<SettingsActivity> scenario;
        private final String script;
        private final boolean direct;
        private FrameLayout container;
        private ChannelPlayback playback;
        private WebView directView;
        private volatile String failure;
        private volatile long readyAt;
        private long startedAt;
        private long pageReadyAt;
        private long videoAt;
        private Snapshot latest;

        ProbeSession(Instrumentation instrumentation, long startupMs, boolean direct) throws Exception {
            this.instrumentation = instrumentation;
            this.direct = direct;
            String probe = AssetUtil.readText(instrumentation.getContext(), "live-playback-probe.js");
            script = "window.tvViewerLivePlaybackProbe ? window.tvViewerLivePlaybackProbe() : ("
                    + probe + ")(" + direct + ")";
            scenario = ActivityScenario.launch(SettingsActivity.class);
            scenario.onActivity(activity -> {
                QbSdk.forceSysWebView();
                container = new FrameLayout(activity);
                container.setBackgroundColor(Color.BLACK);
                activity.setContentView(container);
                if (direct) {
                    directView = createDirectView();
                    return;
                }
                playback = new ChannelPlayback(container, false, false, new ChannelPlayback.Listener() {
                    @Override
                    public void onLoading(boolean hasPreviousChannel) {
                    }

                    @Override
                    public void onReady(int channelIndex, String programme) {
                        readyAt = SystemClock.elapsedRealtime();
                    }

                    @Override
                    public void onFailure(int channelIndex, String reason) {
                        failure = reason;
                    }
                }, startupMs);
            });
        }

        private WebView createDirectView() {
            WebView view = new WebView(container.getContext());
            WebSettings settings = view.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setUserAgent(ChannelPlayback.DESKTOP_USER_AGENT);
            view.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, WebResourceRequest request,
                                            WebResourceError error) {
                    if (request.isForMainFrame()) {
                        failure = "频道页面加载失败（WebView " + error.getErrorCode() + "）";
                    }
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                WebResourceResponse error) {
                    if (request.isForMainFrame()) {
                        failure = "频道页面加载失败（HTTP " + error.getStatusCode() + "）";
                    }
                }
            });
            container.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            return view;
        }

        void load(ChannelCatalog.Channel channel, String url) {
            scenario.onActivity(activity -> {
                startedAt = SystemClock.elapsedRealtime();
                if (direct) {
                    directView.loadUrl(url);
                } else {
                    playback.load(channel.index(), url, channel.source);
                }
            });
        }

        Snapshot sample(JSONArray samples) throws InterruptedException, JSONException {
            CountDownLatch complete = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            Rect bounds = new Rect();
            scenario.onActivity(activity -> {
                if (!(container.getChildAt(0) instanceof WebView)) {
                    complete.countDown();
                    return;
                }
                WebView view = (WebView) container.getChildAt(0);
                int[] origin = new int[2];
                view.getLocationOnScreen(origin);
                bounds.set(origin[0], origin[1], origin[0] + view.getWidth(), origin[1] + view.getHeight());
                view.evaluateJavascript(script, value -> {
                    response.set(value);
                    complete.countDown();
                });
            });

            if (!complete.await(1, TimeUnit.SECONDS)) {
                return null;
            }
            String value = response.get();
            if (value == null || !value.startsWith("{")) {
                return null;
            }
            long now = SystemClock.elapsedRealtime();
            JSONObject data = new JSONObject(value).put("elapsedMs", now - startedAt);
            latest = new Snapshot(now, data, bounds);
            if (pageReadyAt == 0 && !data.optString("host").isEmpty()
                    && "complete".equals(data.optString("state"))) {
                pageReadyAt = now;
            }
            if (videoAt == 0 && data.optBoolean("videoFound")) {
                videoAt = now;
            }
            samples.put(data);
            return latest;
        }

        JSONObject capture(File output, String name, Snapshot snapshot) throws Exception {
            JSONObject capture = new JSONObject().put("name", name)
                    .put("elapsedMs", SystemClock.elapsedRealtime() - startedAt);
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            if (screenshot == null) {
                return capture.put("status", "capture-unavailable");
            }
            Bitmap bitmap;
            try {
                bitmap = screenshot.copy(Bitmap.Config.ARGB_8888, false);
            } finally {
                screenshot.recycle();
            }
            if (bitmap == null) {
                return capture.put("status", "capture-unavailable");
            }
            try {
                try (FileOutputStream png = new FileOutputStream(new File(output, name + ".png"))) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)) {
                        throw new IllegalStateException("Could not write emulator screenshot");
                    }
                }
                capture.put("status", "captured");
                JSONArray rect = snapshot == null ? null : snapshot.data.optJSONArray("rect");
                if (rect != null && !snapshot.webViewBounds.isEmpty()) {
                    capture.put("videoRegionNonBlack", hasVisibleVideoPixels(bitmap, snapshot.webViewBounds, rect));
                }
                return capture;
            } finally {
                bitmap.recycle();
            }
        }

        @Override
        public void close() {
            try {
                scenario.onActivity(activity -> {
                    if (direct) {
                        container.removeView(directView);
                        directView.stopLoading();
                        directView.destroy();
                    } else {
                        playback.close();
                    }
                });
            } finally {
                scenario.close();
            }
        }
    }

    private static boolean hasVisibleVideoPixels(Bitmap bitmap, Rect view, JSONArray rect) {
        double left = Math.max(0, rect.optDouble(0));
        double top = Math.max(0, rect.optDouble(1));
        double right = Math.min(1, rect.optDouble(2));
        double bottom = Math.min(1, rect.optDouble(3));
        if (!(right > left && bottom > top)) {
            return false;
        }

        int visible = 0;
        int sampled = 0;
        // Sample inside the video rectangle so navigation and letterbox bars do not count.
        for (int row = 1; row <= 8; row++) {
            for (int column = 1; column <= 12; column++) {
                int x = view.left + (int) (view.width() * (left + (right - left) * (0.2 + 0.6 * column / 13)));
                int y = view.top + (int) (view.height() * (top + (bottom - top) * (0.2 + 0.6 * row / 9)));
                if (x < 0 || x >= bitmap.getWidth() || y < 0 || y >= bitmap.getHeight()) {
                    continue;
                }
                int pixel = bitmap.getPixel(x, y);
                sampled++;
                if (Math.max(Color.red(pixel), Math.max(Color.green(pixel), Color.blue(pixel))) > 24) {
                    visible++;
                }
            }
        }
        return sampled >= 48 && visible >= sampled * 0.1;
    }
}
