package com.eanyatonic.cctvViewer;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.tencent.smtt.export.external.extension.interfaces.IX5WebSettingsExtension;
import com.tencent.smtt.export.external.interfaces.SslError;
import com.tencent.smtt.export.external.interfaces.SslErrorHandler;
import com.tencent.smtt.export.external.interfaces.WebResourceError;
import com.tencent.smtt.export.external.interfaces.WebResourceRequest;
import com.tencent.smtt.export.external.interfaces.WebResourceResponse;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;

final class ChannelPlayback {
    interface Listener {
        void onLoading(boolean hasPreviousChannel);
        void onReady(int channelIndex, String programme);
        void onFailure(int channelIndex, String reason);
    }

    private static final long LOAD_TIMEOUT_MS = 20_000;
    private static final long PLAYBACK_CHECK_MS = 300;
    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36";

    private static final String FAST_LOADING = """
            function FastLoading() {
                         const fullscreenBtn = document.querySelector('#player_pagefullscreen_yes_player') || document.querySelector('.videoFull');
                         if (fullscreenBtn) return;

                         // 清空所有图片的 src 属性，阻止图片加载
                         Array.from(document.getElementsByTagName('img')).forEach(img => {
                             img.src = '';
                         });

                         // 清空特定的脚本 src 属性
                         const scriptKeywords = ['login', 'index', 'daohang', 'grey', 'jquery'];
                         Array.from(document.getElementsByTagName('script')).forEach(script => {
                             if (scriptKeywords.some(keyword => script.src.includes(keyword))) {
                                 script.src = '';
                             }
                         });

                         // 清空具有特定 class 的 div 内容
                         const classNames = ['newmap', 'newtopbz', 'newtopbzTV', 'column_wrapper'];
                         classNames.forEach(className => {
                             Array.from(document.getElementsByClassName(className)).forEach(div => {
                                 div.innerHTML = '';
                             });
                         });

                         // 递归调用 FastLoading，每 4ms 触发一次
                         setTimeout(FastLoading, 4);
                     }

                     FastLoading();
            """;

    private static final String AUTO_FULLSCREEN = """
            (function fullscreen() {
                const button = document.querySelector('#player_pagefullscreen_yes_player,.videoFull');
                if (!button) {
                    setTimeout(fullscreen, 16);
                    return;
                }
                button.click();
                const video = document.querySelector('video');
                if (video) video.volume = 1;
            })();
            """;

    private static final String PLAYBACK_STATUS = """
            (function() {
                const video = Array.from(document.querySelectorAll('video'))
                    .find(player => player.currentSrc || player.src)
                    || document.querySelector('video');
                if (video && video.error) return ['error', ''];
                if (!video || video.paused || video.ended || video.readyState < 2
                    || (video.currentTime <= 0 && video.readyState < 3)) return ['waiting', ''];
                const programme = Array.from(document.querySelectorAll('%s'))
                    .map(item => item.textContent.trim()).filter(Boolean).join('\\n');
                return ['playing', programme];
            })();
            """;

    private final FrameLayout container;
    private final boolean dualWebView;
    private final boolean x5;
    private final Listener listener;
    private final long loadTimeoutMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Request pending;
    private Request active;
    private boolean paused;

    ChannelPlayback(FrameLayout container, boolean dualWebView, boolean x5, Listener listener) {
        this(container, dualWebView, x5, listener, LOAD_TIMEOUT_MS);
    }

    ChannelPlayback(FrameLayout container, boolean dualWebView, boolean x5, Listener listener,
                    long loadTimeoutMs) {
        this.container = container;
        this.dualWebView = dualWebView;
        this.x5 = x5;
        this.listener = listener;
        this.loadTimeoutMs = loadTimeoutMs;
    }

    void load(int channelIndex, String url, boolean cctv) {
        discardPending();
        if (!dualWebView) {
            destroy(active);
            active = null;
        }
        listener.onLoading(active != null);
        WebView view = createView();
        Request request = new Request(view, channelIndex, url, cctv);
        pending = request;
        view.setWebViewClient(new PlaybackClient(request));
        container.addView(view, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (paused) {
            view.onPause();
        }
        schedule(request, () -> fail(request, "播放加载超时"), loadTimeoutMs);
        view.loadUrl(url);
    }

    WebView activeView() {
        return active == null ? null : active.view;
    }

    int activeChannel() {
        return active == null ? -1 : active.channelIndex;
    }

    String activeProgramme() {
        return active == null ? "" : active.programme;
    }

    void pause() {
        paused = true;
        if (active != null) {
            active.view.onPause();
        }
        if (pending != null) {
            pending.view.onPause();
        }
    }

    void resume() {
        paused = false;
        if (active != null) {
            active.view.onResume();
        }
        if (pending != null) {
            pending.view.onResume();
        }
    }

    void close() {
        discardPending();
        destroy(active);
        active = null;
    }

    private WebView createView() {
        WebView view = new WebView(container.getContext());
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(false);
        settings.setBlockNetworkImage(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgent(DESKTOP_USER_AGENT);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.LOAD_NORMAL);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        view.setFocusable(false);
        view.setWebChromeClient(new WebChromeClient());
        IX5WebSettingsExtension extension = x5 ? view.getSettingsExtension() : null;
        if (extension != null) {
            extension.setPicModel(IX5WebSettingsExtension.PicModel_NoPic);
        }

        DisplayMetrics display = container.getResources().getDisplayMetrics();
        double scale = Math.min(display.widthPixels / 1920.0, display.heightPixels / 1080.0);
        view.setInitialScale((int) Math.round(scale * 100));
        return view;
    }

    private void checkPlayback(Request request, int navigation) {
        if (request != pending || navigation != request.navigation) {
            return;
        }
        String selectors = request.cctv ? "#jiemu > li.cur.act,#jiemu > li:nth-child(4)"
                : ".tvSelectJiemu:nth-child(-n+2)";
        request.view.evaluateJavascript(String.format(PLAYBACK_STATUS, selectors), value -> {
            if (request != pending || navigation != request.navigation) {
                return;
            }
            try {
                JSONArray status = new JSONArray(value == null ? "" : value);
                switch (status.optString(0)) {
                    case "playing":
                        showChannel(request, status.optString(1));
                        return;
                    case "error":
                        fail(request, "视频播放失败");
                        return;
                }
            } catch (JSONException ignored) {
                // 页面可能尚未准备好执行脚本，交给下一次检查。
            }
            schedule(request, () -> checkPlayback(request, navigation), PLAYBACK_CHECK_MS);
        });
    }

    private void showChannel(Request request, String programme) {
        pending = null;
        handler.removeCallbacksAndMessages(request);
        destroy(active);
        active = request;
        request.programme = programme;
        listener.onReady(request.channelIndex, programme);
    }

    private void fail(Request request, String reason) {
        if (request == pending) {
            discardPending();
            listener.onFailure(request.channelIndex, reason);
        }
    }

    private void schedule(Request request, Runnable task, long delayMs) {
        handler.postAtTime(task, request, SystemClock.uptimeMillis() + delayMs);
    }

    private void discardPending() {
        Request request = pending;
        pending = null;
        destroy(request);
    }

    private void destroy(Request request) {
        if (request != null) {
            handler.removeCallbacksAndMessages(request);
            container.removeView(request.view);
            request.view.stopLoading();
            request.view.destroy();
        }
    }

    // Each request owns a WebView so even callbacks from reloading the same URL
    // are distinguishable from callbacks for the current request.
    private final class PlaybackClient extends WebViewClient {
        private final Request request;

        PlaybackClient(Request request) {
            this.request = request;
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (request == pending) {
                request.navigation++;
                request.loadingUrl = url;
                view.evaluateJavascript(FAST_LOADING, null);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (request != pending || "about:blank".equals(url)
                    || (request.loadingUrl != null && !request.loadingUrl.equals(url))
                    || request.checkedNavigation == request.navigation) {
                return;
            }
            request.checkedNavigation = request.navigation;
            view.evaluateJavascript(AUTO_FULLSCREEN, null);
            checkPlayback(request, request.navigation);
        }

        @Override
        public void onReceivedError(WebView view, int code, String description, String failingUrl) {
            if (failingUrl != null
                    && (failingUrl.equals(request.loadingUrl) || failingUrl.equals(request.url))) {
                fail(request, "频道页面加载失败");
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest resource, WebResourceError error) {
            if (resource.isForMainFrame()) {
                fail(request, "频道页面加载失败");
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest resource,
                                        WebResourceResponse response) {
            if (resource.isForMainFrame()) {
                fail(request, "频道页面加载失败（HTTP " + response.getStatusCode() + "）");
            }
        }
    }

    private static final class Request {
        final WebView view;
        final int channelIndex;
        final String url;
        final boolean cctv;
        String loadingUrl;
        int navigation;
        int checkedNavigation = -1;
        String programme = "";

        Request(WebView view, int channelIndex, String url, boolean cctv) {
            this.view = view;
            this.channelIndex = channelIndex;
            this.url = url;
            this.cctv = cctv;
        }
    }
}
