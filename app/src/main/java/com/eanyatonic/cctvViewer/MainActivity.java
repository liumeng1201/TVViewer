package com.eanyatonic.cctvViewer;


import android.Manifest;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

// X5内核代码
import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.sdk.QbSdk;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private AudioManager audioManager;
    private ChannelPlayback playback;

    private final String[] liveUrls = { "https://tv.cctv.com/live/cctv1/", "https://tv.cctv.com/live/cctv2/",
            "https://tv.cctv.com/live/cctv3/", "https://tv.cctv.com/live/cctv4/", "https://tv.cctv.com/live/cctv5/",
            "https://tv.cctv.com/live/cctv6/", "https://tv.cctv.com/live/cctv7/", "https://tv.cctv.com/live/cctv8/",
            "https://tv.cctv.com/live/cctvjilu", "https://tv.cctv.com/live/cctv10/", "https://tv.cctv.com/live/cctv11/",
            "https://tv.cctv.com/live/cctv12/", "https://tv.cctv.com/live/cctv13/",
            "https://tv.cctv.com/live/cctvchild", "https://tv.cctv.com/live/cctv15/",
            "https://tv.cctv.com/live/cctv16/", "https://tv.cctv.com/live/cctv17/",
            "https://tv.cctv.com/live/cctv5plus/", "https://tv.cctv.com/live/cctveurope",
            "https://tv.cctv.com/live/cctvamerica/", "https://www.yangshipin.cn/tv/home?pid=600002309",
            "https://www.yangshipin.cn/tv/home?pid=600002521", "https://www.yangshipin.cn/tv/home?pid=600002483",
            "https://www.yangshipin.cn/tv/home?pid=600002520", "https://www.yangshipin.cn/tv/home?pid=600002475",
            "https://www.yangshipin.cn/tv/home?pid=600002508", "https://www.yangshipin.cn/tv/home?pid=600002485",
            "https://www.yangshipin.cn/tv/home?pid=600002509", "https://www.yangshipin.cn/tv/home?pid=600002498",
            "https://www.yangshipin.cn/tv/home?pid=600002506", "https://www.yangshipin.cn/tv/home?pid=600002531",
            "https://www.yangshipin.cn/tv/home?pid=600002481", "https://www.yangshipin.cn/tv/home?pid=600002516",
            "https://www.yangshipin.cn/tv/home?pid=600002525", "https://www.yangshipin.cn/tv/home?pid=600002484",
            "https://www.yangshipin.cn/tv/home?pid=600002490", "https://www.yangshipin.cn/tv/home?pid=600002503",
            "https://www.yangshipin.cn/tv/home?pid=600002505", "https://www.yangshipin.cn/tv/home?pid=600002532",
            "https://www.yangshipin.cn/tv/home?pid=600002493", "https://www.yangshipin.cn/tv/home?pid=600002513", };

    private final String[] channelNames = { "1 CCTV-1 综合", "2 CCTV-2 财经", "3 CCTV-3 综艺", "4 CCTV-4 中文国际（亚）",
            "5 CCTV-5 体育", "6 CCTV-6 电影", "7 CCTV-7 国防军事", "8 CCTV-8 电视剧", "9 CCTV-9 纪录", "10 CCTV-10 科教",
            "11 CCTV-11 戏曲", "12 CCTV-12 社会与法", "13 CCTV-13 新闻", "14 CCTV-14 少儿", "15 CCTV-15 音乐", "16 CCTV-16 奥林匹克",
            "17 CCTV-17 农业农村", "18 CCTV-5+ 体育赛事", "19 CCTV-4 中文国际（欧）", "20 CCTV-4 中文国际（美）", "21 北京卫视", "22 江苏卫视",
            "23 东方卫视", "24 浙江卫视", "25 湖南卫视", "26 湖北卫视", "27 广东卫视", "28 广西卫视", "29 黑龙江卫视", "30 海南卫视", "31 重庆卫视",
            "32 深圳卫视", "33 四川卫视", "34 河南卫视", "35 福建东南卫视", "36 贵州卫视", "37 江西卫视", "38 辽宁卫视", "39 安徽卫视", "40 河北卫视",
            "41 山东卫视", };

    private int currentLiveIndex;

    private static final String PREF_NAME = "MyPreferences";
    private static final String PREF_KEY_LIVE_INDEX = "currentLiveIndex";

    private boolean doubleBackToExitPressedOnce = false;

    private final StringBuilder digitBuffer = new StringBuilder();
    private static final long DIGIT_TIMEOUT = 3000; // 超时时间（毫秒）

    private TextView inputTextView; // 用于显示正在输入的数字的 TextView

    // 初始化透明的View
    private View loadingOverlay;

    // 频道显示view
    private TextView overlayTextView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable submitDigits = this::handleNumericInput;
    private final Runnable hideOverlay = () -> {
        if (overlayTextView != null) {
            overlayTextView.setVisibility(View.GONE);
        }
    };

    private boolean isMenuOverlayVisible = false;
    private boolean isDrawerOverlayVisible = false;

    private LinearLayout menuOverlay;
    private LinearLayout DrawerLayout;
    private LinearLayout DrawerLayoutDetailed;
    private LinearLayout SubMenuCCTV;
    private LinearLayout SubMenuLocal;
    private TextView CoreText;

    private int menuOverlaySelectedIndex = 0;
    private int DrawerLayoutSelectedIndex = 0;
    private int SubMenuCCTVSelectedIndex = 0;
    private int SubMenuLocalSelectedIndex = 0;

    // 可自定义设置项
    private int TEXT_SIZE = 22;
    private Boolean enableDualWebView = true;
    private Boolean enableDirectChannelChange = false;
    private Boolean enableDirectBack = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 加载设置
        // 获取 SharedPreferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean canLoadX5 = prepareWebViewKernel(sharedPreferences);
        if (isFinishing()) {
            return;
        }
        setContentView(R.layout.activity_main);

        // 读取字体大小
        String selectedOption = sharedPreferences.getString("text_size", "1");
        switch (selectedOption) {
            case "0":
                TEXT_SIZE = 18;
                break;
            case "1":
                TEXT_SIZE = 22;
                break;
            case "2":
                TEXT_SIZE = 25;
                break;
            case "3":
                TEXT_SIZE = 30;
                break;

        }

        // 读取直接频道切换设置
        enableDirectChannelChange = sharedPreferences.getBoolean("direct_channel_change", false);

        // 读取直接返回设置
        enableDirectBack = sharedPreferences.getBoolean("direct_back", true);

        // 读取双缓冲设置
        enableDualWebView = sharedPreferences.getBoolean("dual_webview", false);

        // 获取 AudioManager 实例
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        // 初始化显示正在输入的数字的 TextView
        inputTextView = findViewById(R.id.inputTextView);
        inputTextView.setVisibility(View.GONE);

        // 初始化 loadingOverlay
        loadingOverlay = findViewById(R.id.loadingOverlay);

        // 初始化 overlayTextView
        overlayTextView = findViewById(R.id.overlayTextView);

        // 初始化 菜单
        menuOverlay = findViewById(R.id.menuOverlay);

        // 初始化 DrawerLayout
        DrawerLayout = findViewById(R.id.DrawerLayout);

        // 初始化 DrawerLayoutDetailed
        DrawerLayoutDetailed = findViewById(R.id.DrawerLayoutDetailed);

        // 初始化 CCTV 子菜单
        SubMenuCCTV = findViewById(R.id.subMenuCCTV);

        // 初始化 Local 子菜单
        SubMenuLocal = findViewById(R.id.subMenuLocal);

        // 初始化 CoreText
        CoreText = findViewById(R.id.CoreText);

        LinearLayout DrawerLayout = findViewById(R.id.DrawerLayout);

        // 中央台频道列表
        String[] firstDrawer = {
                "央视频道", "地方频道"
        };

        // 动态生成中央台按钮和地方台按钮
        for (String channel : firstDrawer) {
            Button button = new Button(this);
            button.setText(channel);
            // 创建 LayoutParams 并设置 margin
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(4, 4, 4, 4);
            // 设置按钮属性
            button.setLayoutParams(layoutParams);
            button.setPadding(16, 16, 16, 16);
            button.setTextColor(getResources().getColor(android.R.color.white));
            button.setBackground(getResources().getDrawable(R.drawable.detailed_channel_selector));
            button.setTextSize(TEXT_SIZE);
            DrawerLayout.addView(button);
        }

        // 添加设置按钮
        Button SettingButton = new Button(this);
        SettingButton.setText("打开设置");
        // 创建 LayoutParams 并设置 margin
        LinearLayout.LayoutParams layoutParams0 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams0.setMargins(4, 28, 4, 4);
        // 设置按钮属性
        SettingButton.setLayoutParams(layoutParams0);
        SettingButton.setPadding(16, 16, 16, 16);
        SettingButton.setTextColor(getResources().getColor(android.R.color.white));
        SettingButton.setBackground(getResources().getDrawable(R.drawable.detailed_channel_selector));
        SettingButton.setTextSize(TEXT_SIZE);
        DrawerLayout.addView(SettingButton);

        LinearLayout subMenuCCTV = findViewById(R.id.subMenuCCTV);
        LinearLayout subMenuLocal = findViewById(R.id.subMenuLocal);

        // 中央台频道列表
        String[] cctvChannels = {
                "CCTV-1 综合", "CCTV-2 财经", "CCTV-3 综艺", "CCTV-4 中文国际（亚）",
                "CCTV-5 体育", "CCTV-6 电影", "CCTV-7 国防军事", "CCTV-8 电视剧",
                "CCTV-9 纪录", "CCTV-10 科教", "CCTV-11 戏曲", "CCTV-12 社会与法",
                "CCTV-13 新闻", "CCTV-14 少儿", "CCTV-15 音乐", "CCTV-16 奥林匹克",
                "CCTV-17 农业农村", "CCTV-5+ 体育赛事", "CCTV-4 中文国际（欧）", "CCTV-4 中文国际（美）"
        };

        // 动态生成中央台按钮
        for (String channel : cctvChannels) {
            Button button = new Button(this);
            button.setText(channel);
            // 创建 LayoutParams 并设置 margin
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(4, 4, 4, 4);
            // 设置按钮属性
            button.setLayoutParams(layoutParams);
            button.setPadding(16, 16, 16, 16);
            button.setTextColor(getResources().getColor(android.R.color.white));
            button.setBackground(getResources().getDrawable(R.drawable.detailed_channel_selector));
            button.setTextSize(TEXT_SIZE);
            subMenuCCTV.addView(button);
        }

        // 地方台频道列表
        String[] localChannels = {
                "北京卫视", "江苏卫视", "东方卫视", "浙江卫视", "湖南卫视", "湖北卫视",
                "广东卫视", "广西卫视", "黑龙江卫视", "海南卫视", "重庆卫视", "深圳卫视",
                "四川卫视", "河南卫视", "福建东南卫视", "贵州卫视", "江西卫视", "辽宁卫视",
                "安徽卫视", "河北卫视", "山西卫视"
        };

        // 动态生成地方台按钮
        for (String channel : localChannels) {
            Button button = new Button(this);
            button.setText(channel);
            // 创建 LayoutParams 并设置 margin
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(4, 4, 4, 4);
            // 设置按钮属性
            button.setLayoutParams(layoutParams);
            button.setPadding(16, 16, 16, 16);
            button.setTextColor(getResources().getColor(android.R.color.white));
            button.setBackground(getResources().getDrawable(R.drawable.detailed_channel_selector));
            button.setTextSize(TEXT_SIZE);
            subMenuLocal.addView(button);
        }

        if (canLoadX5) {
            CoreText.setText("当前程序运行在腾讯X5内核上");
            requestPermission();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PackageInfo pkgInfo = android.webkit.WebView.getCurrentWebViewPackage();
            if (pkgInfo != null) {
                CoreText.setText("当前程序运行在系统WebView上，版本号：" + pkgInfo.versionName);
            }
        }

        FrameLayout container = findViewById(R.id.webViewContainer);
        playback = new ChannelPlayback(container, enableDualWebView, canLoadX5,
                new ChannelPlayback.Listener() {
                    @Override
                    public void onLoading(boolean hasPreviousChannel) {
                        hideChannelOverlay();
                        loadingOverlay.setVisibility(hasPreviousChannel ? View.GONE : View.VISIBLE);
                        if (hasPreviousChannel) {
                            showOverlay(channelNames[currentLiveIndex] + "\n正在切换频道…");
                        }
                    }

                    @Override
                    public void onReady(int channelIndex, String programme) {
                        loadingOverlay.setVisibility(View.GONE);
                        saveCurrentLiveIndex();
                        showOverlay(channelNames[channelIndex] + "\n" + programme);
                    }

                    @Override
                    public void onFailure(int channelIndex, String reason) {
                        loadingOverlay.setVisibility(View.GONE);
                        showOverlay(channelNames[channelIndex] + "\n" + reason
                                + "\n可换台，或按菜单键后选择刷新重试");
                    }
                });
        loadLastLiveIndex();
    }

    private boolean prepareWebViewKernel(SharedPreferences preferences) {
        HashMap<String, Object> settings = new HashMap<>(2);
        settings.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        settings.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
        QbSdk.initTbsSettings(settings);

        if (preferences.getBoolean(KernelFallback.INSTALL_IN_PROGRESS, false)) {
            KernelFallback.preferSystem(preferences);
            Toast.makeText(this, R.string.x5_install_interrupted, Toast.LENGTH_LONG).show();
        }
        if (preferences.getBoolean(KernelFallback.SYSTEM_WEBVIEW, true)) {
            QbSdk.forceSysWebView();
            return false;
        }

        try {
            QbSdk.unForceSysWebView();
            if (QbSdk.canLoadX5(getApplicationContext())) {
                return true;
            }
        } catch (RuntimeException | LinkageError error) {
            Log.w("MainActivity", "Could not initialize the X5 kernel", error);
            KernelFallback.preferSystem(preferences);
            QbSdk.forceSysWebView();
            Toast.makeText(this, R.string.x5_install_failed, Toast.LENGTH_LONG).show();
            return false;
        }

        startActivity(new Intent(this, LoadingActivity.class));
        finish();
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (playback == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI);
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI);
            } else if (menuOverlay.hasFocus()) {
                // menuOverlay具有焦点
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_B) {
                    // 按下返回键
                    showMenuOverlay();
                    return true;
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
                        || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    // 方向键,切换五个按钮选择
                    if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (menuOverlaySelectedIndex == 0) {
                            menuOverlaySelectedIndex = 5;
                        } else {
                            menuOverlaySelectedIndex--;
                        }
                    } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (menuOverlaySelectedIndex == 5) {
                            menuOverlaySelectedIndex = 0;
                        } else {
                            menuOverlaySelectedIndex++;
                        }
                    }
                    menuOverlay.getChildAt(menuOverlaySelectedIndex).requestFocus();
                    return true;
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    // 中间键,执行按钮操作
                    switch (menuOverlaySelectedIndex) {
                        case 0:
                            // 刷新页面
                            loadLiveUrl();
                            showMenuOverlay();
                            break;
                        case 1:
                            // 播放
                            if (playback.activeView() == null) {
                                break;
                            }
                            if (playback.activeChannel() <= 19) {
                                simulateTouch(playback.activeView(), 0.5f, 0.5f);
                            } else {
                                String scriptPause = """
                                        try{
                                        document.querySelector('.play.play2').click();
                                        } catch(e) {
                                        document.querySelector('.play.play1').click();
                                        }
                                        """;
                                playback.activeView().evaluateJavascript(scriptPause, null);
                            }
                            showMenuOverlay();
                            break;
                        case 2:
                            // 切换全屏
                            String script1 = """
                                    console.log('点击全屏按钮');
                                    document.querySelector('#player_pagefullscreen_yes_player').click();
                                    """;

                            String script2 = """
                                    console.log('点击全屏按钮');
                                    if(document.querySelector('.videoFull').id == ''){
                                        document.querySelector('.videoFull').click();
                                    }else{
                                        document.querySelector('.videoFull_ac').click();
                                    }
                                    """;

                            if (playback.activeView() != null) {
                                playback.activeView().evaluateJavascript(
                                        playback.activeChannel() <= 19 ? script1 : script2, null);
                            }
                            break;
                        case 3:
                            // 放大
                            String scriptZoomIn = """
                                    // 获取当前页面的缩放比例
                                    function getZoom() {
                                      return parseFloat(document.body.style.zoom) || 1;
                                    }

                                    // 设置页面的缩放比例
                                    function setZoom(zoom) {
                                      document.body.style.zoom = zoom;
                                    }

                                    // 页面放大函数
                                    function zoomIn() {
                                      var zoom = getZoom();
                                      setZoom(zoom + 0.1);
                                    }

                                    zoomIn();
                                    """;
                            if (playback.activeView() != null) {
                                playback.activeView().evaluateJavascript(scriptZoomIn, null);
                            }
                            break;
                        case 4:
                            // 缩小
                            String scriptZoomOut = """
                                    // 获取当前页面的缩放比例
                                    function getZoom() {
                                      return parseFloat(document.body.style.zoom) || 1;
                                    }

                                    // 设置页面的缩放比例
                                    function setZoom(zoom) {
                                      document.body.style.zoom = zoom;
                                    }

                                    // 页面缩小函数
                                    function zoomOut() {
                                      var zoom = getZoom();
                                      if (zoom > 0.2) {
                                        setZoom(zoom - 0.1);
                                      }
                                    }

                                    zoomOut();
                                    """;
                            if (playback.activeView() != null) {
                                playback.activeView().evaluateJavascript(scriptZoomOut, null);
                            }
                            break;
                        case 5:
                            // 打开设置
                            Intent intent = new Intent(this, SettingsActivity.class);
                            startActivity(intent);
                            break;
                    }
                    return true;
                }
                return true;
            }
            if (DrawerLayout.hasFocus() && !SubMenuCCTV.hasFocus() && !SubMenuLocal.hasFocus()
                    && !DrawerLayoutDetailed.hasFocus()) {
                // DrawerLayout具有焦点
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
                        || event.getKeyCode() == KeyEvent.KEYCODE_B) {
                    // 按下返回键
                    showChannelList();
                    return true;
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                        || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // 方向键,切换频道选择
                    if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                        if (DrawerLayoutSelectedIndex == 0) {
                            DrawerLayoutSelectedIndex = 2;
                        } else {
                            DrawerLayoutSelectedIndex--;
                        }
                    } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (DrawerLayoutSelectedIndex == 2) {
                            DrawerLayoutSelectedIndex = 0;
                        } else {
                            DrawerLayoutSelectedIndex++;
                        }
                    }
                    DrawerLayout.getChildAt(DrawerLayoutSelectedIndex).requestFocus();
                    return true;
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    // 中间键,执行按钮操作
                    switch (DrawerLayoutSelectedIndex) {
                        case 0:
                            // 中央频道
                            DrawerLayoutDetailed.setVisibility(View.VISIBLE);
                            findViewById(R.id.subMenuCCTV).setVisibility(View.VISIBLE);
                            findViewById(R.id.CCTVScroll).setVisibility(View.VISIBLE);
                            findViewById(R.id.subMenuLocal).setVisibility(View.GONE);
                            findViewById(R.id.LocalScroll).setVisibility(View.GONE);
                            SubMenuCCTV.getChildAt(SubMenuCCTVSelectedIndex).requestFocus();
                            break;
                        case 1:
                            // 地方频道
                            DrawerLayoutDetailed.setVisibility(View.VISIBLE);
                            findViewById(R.id.subMenuCCTV).setVisibility(View.GONE);
                            findViewById(R.id.CCTVScroll).setVisibility(View.GONE);
                            findViewById(R.id.subMenuLocal).setVisibility(View.VISIBLE);
                            findViewById(R.id.LocalScroll).setVisibility(View.VISIBLE);
                            SubMenuLocal.getChildAt(SubMenuLocalSelectedIndex).requestFocus();
                            break;
                        case 2:
                            // 打开设置
                            showChannelList();
                            showMenuOverlay();
                            break;
                    }
                    return true;
                }
            } else if (SubMenuCCTV.hasFocus()) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_B) {
                    // 按下返回键
                    if (enableDirectBack) {
                        showChannelList();
                        return true;
                    } else {
                        DrawerLayout.getChildAt(DrawerLayoutSelectedIndex).requestFocus();
                        SubMenuCCTV.setVisibility(View.GONE);
                        DrawerLayoutDetailed.setVisibility(View.GONE);
                        findViewById(R.id.CCTVScroll).setVisibility(View.GONE);
                        return true;
                    }
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                    DrawerLayout.getChildAt(DrawerLayoutSelectedIndex).requestFocus();
                    SubMenuCCTV.setVisibility(View.GONE);
                    DrawerLayoutDetailed.setVisibility(View.GONE);
                    findViewById(R.id.CCTVScroll).setVisibility(View.GONE);
                    return true;
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                        || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // 方向键,切换频道选择
                    if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                        if (SubMenuCCTVSelectedIndex == 0) {
                            SubMenuCCTVSelectedIndex = 19;
                        } else {
                            SubMenuCCTVSelectedIndex--;
                        }
                    } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (SubMenuCCTVSelectedIndex == 19) {
                            SubMenuCCTVSelectedIndex = 0;
                        } else {
                            SubMenuCCTVSelectedIndex++;
                        }
                    }
                    SubMenuCCTV.getChildAt(SubMenuCCTVSelectedIndex).requestFocus();
                    return true;
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    // 中间键,执行按钮操作
                    currentLiveIndex = SubMenuCCTVSelectedIndex;
                    loadLiveUrl();
                    showChannelList();
                    return true;
                }
            } else if (SubMenuLocal.hasFocus()) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_B) {
                    if (enableDirectBack) {
                        showChannelList();
                        return true;
                    } else {
                        // 按下返回键
                        DrawerLayout.getChildAt(DrawerLayoutSelectedIndex).requestFocus();
                        SubMenuLocal.setVisibility(View.GONE);
                        DrawerLayoutDetailed.setVisibility(View.GONE);
                        findViewById(R.id.LocalScroll).setVisibility(View.GONE);
                        return true;
                    }
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                    DrawerLayout.getChildAt(DrawerLayoutSelectedIndex).requestFocus();
                    SubMenuLocal.setVisibility(View.GONE);
                    DrawerLayoutDetailed.setVisibility(View.GONE);
                    findViewById(R.id.LocalScroll).setVisibility(View.GONE);
                    return true;
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                        || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // 方向键,切换频道选择
                    if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                        if (SubMenuLocalSelectedIndex == 0) {
                            SubMenuLocalSelectedIndex = 20;
                        } else {
                            SubMenuLocalSelectedIndex--;
                        }
                    } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (SubMenuLocalSelectedIndex == 20) {
                            SubMenuLocalSelectedIndex = 0;
                        } else {
                            SubMenuLocalSelectedIndex++;
                        }
                    }
                    SubMenuLocal.getChildAt(SubMenuLocalSelectedIndex).requestFocus();
                    return true;
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    // 中间键,执行按钮操作
                    currentLiveIndex = SubMenuLocalSelectedIndex + 20;
                    loadLiveUrl();
                    showChannelList();
                    return true;
                }
            } else if (DrawerLayoutDetailed.hasFocus()) {
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                // 执行上一个直播地址的操作
                navigateToPreviousLive();
                return true; // 返回 true 表示事件已处理，不传递给 WebView
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                // 执行下一个直播地址的操作
                navigateToNextLive();
                return true; // 返回 true 表示事件已处理，不传递给 WebView
            } else if ((event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER)) {
                // 换台菜单
                showChannelList();
                // 显示节目列表
                int active = playback.activeChannel();
                if (active >= 0) {
                    showOverlay(channelNames[active] + "\n" + playback.activeProgramme());
                }
                return true; // 返回 true 表示事件已处理，不传递给 WebView
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_MENU
                    || event.getKeyCode() == KeyEvent.KEYCODE_M) {
                // 显示菜单
                showMenuOverlay();

                return true; // 返回 true 表示事件已处理，不传递给 WebView
            } else if (event.getKeyCode() >= KeyEvent.KEYCODE_0
                    && event.getKeyCode() <= KeyEvent.KEYCODE_9) {
                if (event.getRepeatCount() == 0
                        && digitBuffer.length() < String.valueOf(liveUrls.length).length()) {
                    digitBuffer.append(event.getKeyCode() - KeyEvent.KEYCODE_0);
                    handler.removeCallbacks(submitDigits);
                    handler.postDelayed(submitDigits, DIGIT_TIMEOUT);
                    updateInputTextView();
                }

                return true; // 事件已处理，不传递给 WebView
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_B) {
                if (doubleBackToExitPressedOnce) {
                    finish();
                    return true;
                }

                this.doubleBackToExitPressedOnce = true;
                Toast.makeText(this, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();

                handler.postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
                // 如果两秒内再次按返回键，则退出应用
            }
        }
        return true; // 不处理其余事件
    }

    // 显示底部菜单
    private void showMenuOverlay() {
        if (!isMenuOverlayVisible) {
            menuOverlay.setVisibility(View.VISIBLE);
            menuOverlay.getChildAt(menuOverlaySelectedIndex).requestFocus();
            isMenuOverlayVisible = true;
        } else {
            menuOverlay.setVisibility(View.GONE);
            menuOverlaySelectedIndex = 0;
            isMenuOverlayVisible = false;
        }
    }

    // 频道选择列表
    private void showChannelList() {
        // 显示频道抽屉
        if (!isDrawerOverlayVisible) {
            DrawerLayoutDetailed.setVisibility(View.VISIBLE);
            DrawerLayout.setVisibility(View.VISIBLE);
            isDrawerOverlayVisible = true;
            if (currentLiveIndex < 20) {
                SubMenuCCTV.setVisibility(View.VISIBLE);
                findViewById(R.id.CCTVScroll).setVisibility(View.VISIBLE);
                SubMenuCCTV.getChildAt(currentLiveIndex).requestFocus();
                SubMenuCCTVSelectedIndex = currentLiveIndex;
                DrawerLayoutSelectedIndex = 0;
            } else {
                SubMenuLocal.setVisibility(View.VISIBLE);
                findViewById(R.id.LocalScroll).setVisibility(View.VISIBLE);
                SubMenuLocal.getChildAt(currentLiveIndex - 20).requestFocus();
                SubMenuLocalSelectedIndex = currentLiveIndex - 20;
                DrawerLayoutSelectedIndex = 1;
            }
        } else {
            DrawerLayout.setVisibility(View.GONE);
            SubMenuCCTV.setVisibility(View.GONE);
            SubMenuLocal.setVisibility(View.GONE);
            findViewById(R.id.LocalScroll).setVisibility(View.GONE);
            findViewById(R.id.CCTVScroll).setVisibility(View.GONE);
            DrawerLayoutDetailed.setVisibility(View.GONE);
            isDrawerOverlayVisible = false;
        }
    }

    private void showChannelListDPAD(int selectIndex) {
        // 显示频道抽屉
        if (!isDrawerOverlayVisible) {
            DrawerLayoutDetailed.setVisibility(View.VISIBLE);
            DrawerLayout.setVisibility(View.VISIBLE);
            isDrawerOverlayVisible = true;
            if (selectIndex < 20) {
                SubMenuCCTV.setVisibility(View.VISIBLE);
                findViewById(R.id.CCTVScroll).setVisibility(View.VISIBLE);
                SubMenuCCTV.getChildAt(selectIndex).requestFocus();
                SubMenuCCTVSelectedIndex = selectIndex;
                DrawerLayoutSelectedIndex = 0;
            } else {
                SubMenuLocal.setVisibility(View.VISIBLE);
                findViewById(R.id.LocalScroll).setVisibility(View.VISIBLE);
                SubMenuLocal.getChildAt(selectIndex - 20).requestFocus();
                SubMenuLocalSelectedIndex = selectIndex - 20;
                DrawerLayoutSelectedIndex = 1;
            }
        } else {
            DrawerLayout.setVisibility(View.GONE);
            SubMenuCCTV.setVisibility(View.GONE);
            SubMenuLocal.setVisibility(View.GONE);
            findViewById(R.id.LocalScroll).setVisibility(View.GONE);
            findViewById(R.id.CCTVScroll).setVisibility(View.GONE);
            DrawerLayoutDetailed.setVisibility(View.GONE);
            isDrawerOverlayVisible = false;
        }
    }

    private void handleNumericInput() {
        if (digitBuffer.length() == 0) {
            return;
        }

        int number = Integer.parseInt(digitBuffer.toString());
        clearNumericInput();
        if (number > 0 && number <= liveUrls.length) {
            currentLiveIndex = number - 1;
            loadLiveUrl();
        } else {
            showOverlay("频道号无效");
        }
    }

    private void clearNumericInput() {
        handler.removeCallbacks(submitDigits);
        digitBuffer.setLength(0);
        if (inputTextView != null) {
            inputTextView.setVisibility(View.GONE);
        }
    }

    private void updateInputTextView() {
        // 在 TextView 中显示当前正在输入的数字
        inputTextView.setVisibility(View.VISIBLE);
        inputTextView.setText("换台：" + digitBuffer.toString());
    }

    private void loadLastLiveIndex() {
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int savedIndex = preferences.getInt(PREF_KEY_LIVE_INDEX, 0);
        currentLiveIndex = savedIndex >= 0 && savedIndex < liveUrls.length ? savedIndex : 0;
        loadLiveUrl();
    }

    private void saveCurrentLiveIndex() {
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREF_KEY_LIVE_INDEX, playback.activeChannel());
        editor.apply();
    }

    private void loadLiveUrl() {
        clearNumericInput();
        playback.load(currentLiveIndex, liveUrls[currentLiveIndex], currentLiveIndex < 20);
    }

    private void navigateToPreviousLive() {
        if (enableDirectChannelChange) {
            currentLiveIndex = (currentLiveIndex - 1 + liveUrls.length) % liveUrls.length;
            loadLiveUrl();
        } else {
            showChannelListDPAD((currentLiveIndex - 1 + liveUrls.length) % liveUrls.length);
        }
    }

    private void navigateToNextLive() {
        if (enableDirectChannelChange) {
            currentLiveIndex = (currentLiveIndex + 1 + liveUrls.length) % liveUrls.length;
            loadLiveUrl();
        } else {
            showChannelListDPAD((currentLiveIndex + 1 + liveUrls.length) % liveUrls.length);
        }
    }

    // 在需要模拟触摸的地方调用该方法
    public void simulateTouch(View view, float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis() + 100;

        // 构造 ACTION_DOWN 事件
        MotionEvent downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0);
        view.dispatchTouchEvent(downEvent);

        // 构造 ACTION_UP 事件
        MotionEvent upEvent = MotionEvent.obtain(downTime, eventTime + 100, MotionEvent.ACTION_UP, x, y, 0);
        view.dispatchTouchEvent(upEvent);

        // 释放事件对象
        downEvent.recycle();
        upEvent.recycle();
    }

    private void showOverlay(String channelInfo) {
        overlayTextView.setText(channelInfo);
        overlayTextView.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOverlay);
        handler.postDelayed(hideOverlay, 8000);
    }

    private void hideChannelOverlay() {
        handler.removeCallbacks(hideOverlay);
        hideOverlay.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playback != null) {
            playback.resume();
        }
    }

    @Override
    protected void onPause() {
        clearNumericInput();
        if (playback != null) {
            playback.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (playback != null) {
            playback.close();
        }
        super.onDestroy();
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (!checkPermission()) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE , Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }
}
