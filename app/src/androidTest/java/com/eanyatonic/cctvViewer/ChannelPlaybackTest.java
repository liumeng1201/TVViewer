package com.eanyatonic.cctvViewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.util.Base64;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.WebView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ChannelPlaybackTest {
    private static final long TEST_LOAD_TIMEOUT_MS = 5_000;
    private static final String NO_VIDEO = dataPage("<p>Channel without a video</p>");
    private static final String PLAYER_HTML = """
            <canvas id="canvas" width="32" height="32"></canvas>
            <button id="player_pagefullscreen_yes_player"></button>
            <span class="tvSelectJiemu">当前节目</span>
            <video id="video" muted autoplay playsinline></video>
            <script>
            const canvas = document.getElementById('canvas');
            const pixels = canvas.getContext('2d');
            let frame = 0;
            setInterval(() => {
                pixels.fillStyle = frame++ % 2 === 0 ? 'red' : 'blue';
                pixels.fillRect(0, 0, 32, 32);
            }, 100);
            const player = document.getElementById('video');
            player.srcObject = canvas.captureStream(10);
            player.play();
            setTimeout(() => {
                const button = document.createElement('button');
                button.className = 'videoFull';
                button.addEventListener('click', () => {
                    window.fullscreenClicks = (window.fullscreenClicks || 0) + 1;
                });
                document.body.appendChild(button);
            }, 500);
            </script>
            """;
    private static final String PLAYING = dataPage(PLAYER_HTML);

    @Test
    public void timedOutChannelKeepsPreviousVideoAndAllowsRetry() throws Exception {
        try (PlaybackFixture fixture = new PlaybackFixture()) {
            fixture.load(0, PLAYING);
            fixture.expect("ready:0");
            fixture.scenario.onActivity(activity -> fixture.previousView = fixture.playback.activeView());

            fixture.load(1, NO_VIDEO);
            fixture.expect("failed:1");
            fixture.scenario.onActivity(activity -> {
                assertEquals(0, fixture.playback.activeChannel());
                assertSame(fixture.previousView, fixture.playback.activeView());
            });

            fixture.load(1, PLAYING);
            fixture.expect("ready:1");
        }
    }

    @Test
    public void replacingPendingRequestDiscardsItsPageAndTimeout() throws Exception {
        try (PlaybackFixture fixture = new PlaybackFixture()) {
            fixture.scenario.onActivity(activity -> {
                fixture.playback.load(0, NO_VIDEO, ChannelCatalog.Source.CCTV);
                fixture.playback.load(1, PLAYING, ChannelCatalog.Source.CCTV);
            });
            fixture.expect("ready:1");
            assertEquals(null, fixture.events.poll(TEST_LOAD_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS));
            fixture.scenario.onActivity(activity -> {
                assertEquals(1, fixture.playback.activeChannel());
                assertEquals(1, fixture.container.getChildCount());
            });
        }
    }

    @Test
    public void missingMainFrameFailsAndNextChannelCanPlay() throws Exception {
        try (PlaybackFixture fixture = new PlaybackFixture()) {
            fixture.load(0, "file:///android_asset/missing-channel-fixture.html");
            String event = fixture.events.poll(TEST_LOAD_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
            assertNotNull(event);
            assertTrue(event, event.startsWith("failed:0:频道页面加载失败"));
            fixture.load(1, PLAYING);
            fixture.expect("ready:1");
        }
    }

    @Test
    public void siteAdapterHandlesFullscreenControlInsertedAfterPageLoad() throws Exception {
        try (PlaybackFixture fixture = new PlaybackFixture()) {
            fixture.scenario.onActivity(activity ->
                    fixture.playback.load(20, PLAYING, ChannelCatalog.Source.YANGSHIPIN));
            fixture.expect("ready:20");
            fixture.scenario.onActivity(activity ->
                    assertEquals("当前节目", fixture.playback.activeProgramme()));

            BlockingQueue<String> results = new LinkedBlockingQueue<>();
            long deadline = SystemClock.uptimeMillis() + 4_000;
            String clicks = null;
            while (!"1".equals(clicks) && SystemClock.uptimeMillis() < deadline) {
                fixture.scenario.onActivity(activity -> fixture.playback.activeView()
                        .evaluateJavascript("window.fullscreenClicks || 0", value -> results.add(value)));
                clicks = results.poll(500, TimeUnit.MILLISECONDS);
                if (!"1".equals(clicks)) {
                    SystemClock.sleep(50);
                }
            }
            assertEquals("1", clicks);
        }
    }

    private static String dataPage(String content) {
        String html = "<!doctype html><meta charset='utf-8'>" + content;
        return "data:text/html;base64,"
                + Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static final class PlaybackFixture implements AutoCloseable {
        final ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class);
        final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        FrameLayout container;
        ChannelPlayback playback;
        WebView previousView;

        PlaybackFixture() {
            scenario.onActivity(activity -> {
                QbSdk.forceSysWebView();
                container = new FrameLayout(activity);
                activity.setContentView(container);
                playback = new ChannelPlayback(container, true, false, new ChannelPlayback.Listener() {
                    @Override
                    public void onLoading(boolean hasPreviousChannel) {
                    }

                    @Override
                    public void onReady(int channelIndex, String programme) {
                        events.add("ready:" + channelIndex);
                    }

                    @Override
                    public void onFailure(int channelIndex, String reason) {
                        events.add("failed:" + channelIndex + ":" + reason);
                    }
                }, TEST_LOAD_TIMEOUT_MS);
            });
        }

        void load(int index, String url) {
            scenario.onActivity(activity -> playback.load(index, url, ChannelCatalog.Source.CCTV));
        }

        void expect(String expected) throws InterruptedException {
            String event = events.poll(TEST_LOAD_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS);
            assertNotNull(expected, event);
            assertTrue(event, event.equals(expected) || event.startsWith(expected + ":"));
        }

        @Override
        public void close() {
            scenario.onActivity(activity -> playback.close());
            scenario.close();
        }
    }
}
