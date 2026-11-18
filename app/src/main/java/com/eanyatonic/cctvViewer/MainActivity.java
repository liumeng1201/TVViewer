package com.eanyatonic.cctvViewer;


import androidx.core.content.ContextCompat;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AudioManager audioManager;
    private ChannelPlayback playback;

    private int currentLiveIndex;

    private static final String PREF_NAME = "MyPreferences";
    private static final String PREF_KEY_CHANNEL_ID = "currentChannelId";
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

        for (ChannelCatalog.Source source : ChannelCatalog.Source.values()) {
            DrawerLayout.addView(createDrawerButton(source.groupName, 4));
        }
        Button settingsButton = createDrawerButton("打开设置", 28);
        settingsButton.setOnClickListener(view -> openSettings());
        DrawerLayout.addView(settingsButton);
        addChannelButtons(SubMenuCCTV, ChannelCatalog.CCTV);
        addChannelButtons(SubMenuLocal, ChannelCatalog.PROVINCIAL);

        if (canLoadX5) {
            CoreText.setText("当前程序运行在腾讯X5内核上");
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
                            showOverlay(ChannelCatalog.at(currentLiveIndex).label() + "\n正在切换频道…");
                        }
                    }

                    @Override
                    public void onReady(int channelIndex, String programme) {
                        loadingOverlay.setVisibility(View.GONE);
                        saveCurrentLiveIndex();
                        showOverlay(ChannelCatalog.at(channelIndex).label() + "\n" + programme);
                    }

                    @Override
                    public void onFailure(int channelIndex, String reason) {
                        loadingOverlay.setVisibility(View.GONE);
                        showOverlay(ChannelCatalog.at(channelIndex).label() + "\n" + reason
                                + "\n可换台，或按菜单键后选择刷新重试");
                    }
                });
        loadLastLiveIndex();
    }

    private Button createDrawerButton(String text, int topMargin) {
        Button button = new Button(this);
        button.setText(text);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        size.setMargins(4, topMargin, 4, 4);
        button.setLayoutParams(size);
        button.setPadding(16, 16, 16, 16);
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        button.setBackground(ContextCompat.getDrawable(this, R.drawable.detailed_channel_selector));
        button.setTextSize(TEXT_SIZE);
        return button;
    }

    private void addChannelButtons(LinearLayout menu, List<ChannelCatalog.Channel> channels) {
        for (ChannelCatalog.Channel channel : channels) {
            Button button = createDrawerButton(channel.name, 4);
            button.setOnClickListener(view -> selectChannel(channel));
            menu.addView(button);
        }
    }

    private void selectChannel(ChannelCatalog.Channel channel) {
        currentLiveIndex = channel.index();
        loadLiveUrl();
        closeChannelList();
    }

    private void openSettings() {
        closeChannelList();
        if (isMenuOverlayVisible) {
            showMenuOverlay();
        }
        startActivity(new Intent(this, SettingsActivity.class));
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
                            playback.togglePlayback();
                            showMenuOverlay();
                            break;
                        case 2:
                            playback.toggleFullscreen();
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
                            openSettings();
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
                            openSettings();
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
                            SubMenuCCTVSelectedIndex = ChannelCatalog.CCTV.size() - 1;
                        } else {
                            SubMenuCCTVSelectedIndex--;
                        }
                    } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (SubMenuCCTVSelectedIndex == ChannelCatalog.CCTV.size() - 1) {
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
                    selectChannel(ChannelCatalog.CCTV.get(SubMenuCCTVSelectedIndex));
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
                            SubMenuLocalSelectedIndex = ChannelCatalog.PROVINCIAL.size() - 1;
                        } else {
                            SubMenuLocalSelectedIndex--;
                        }
                    } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (SubMenuLocalSelectedIndex == ChannelCatalog.PROVINCIAL.size() - 1) {
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
                    selectChannel(ChannelCatalog.PROVINCIAL.get(SubMenuLocalSelectedIndex));
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
                    showOverlay(ChannelCatalog.at(active).label() + "\n" + playback.activeProgramme());
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
                        && digitBuffer.length() < String.valueOf(ChannelCatalog.size()).length()) {
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

    private void showChannelList() {
        if (isDrawerOverlayVisible) {
            closeChannelList();
        } else {
            openChannelList(currentLiveIndex);
        }
    }

    private void openChannelList(int index) {
        ChannelCatalog.Channel channel = ChannelCatalog.at(index);
        boolean isCctv = channel.source == ChannelCatalog.Source.CCTV;
        DrawerLayout.setVisibility(View.VISIBLE);
        DrawerLayoutDetailed.setVisibility(View.VISIBLE);
        isDrawerOverlayVisible = true;
        SubMenuCCTV.setVisibility(isCctv ? View.VISIBLE : View.GONE);
        findViewById(R.id.CCTVScroll).setVisibility(isCctv ? View.VISIBLE : View.GONE);
        SubMenuLocal.setVisibility(isCctv ? View.GONE : View.VISIBLE);
        findViewById(R.id.LocalScroll).setVisibility(isCctv ? View.GONE : View.VISIBLE);
        if (isCctv) {
            SubMenuCCTVSelectedIndex = ChannelCatalog.CCTV.indexOf(channel);
            DrawerLayoutSelectedIndex = 0;
            SubMenuCCTV.getChildAt(SubMenuCCTVSelectedIndex).requestFocus();
        } else {
            SubMenuLocalSelectedIndex = ChannelCatalog.PROVINCIAL.indexOf(channel);
            DrawerLayoutSelectedIndex = 1;
            SubMenuLocal.getChildAt(SubMenuLocalSelectedIndex).requestFocus();
        }
    }

    private void closeChannelList() {
        DrawerLayout.setVisibility(View.GONE);
        SubMenuCCTV.setVisibility(View.GONE);
        SubMenuLocal.setVisibility(View.GONE);
        findViewById(R.id.LocalScroll).setVisibility(View.GONE);
        findViewById(R.id.CCTVScroll).setVisibility(View.GONE);
        DrawerLayoutDetailed.setVisibility(View.GONE);
        isDrawerOverlayVisible = false;
    }

    private void handleNumericInput() {
        if (digitBuffer.length() == 0) {
            return;
        }

        int number = Integer.parseInt(digitBuffer.toString());
        clearNumericInput();
        ChannelCatalog.Channel channel = ChannelCatalog.byNumber(number);
        if (channel != null) {
            currentLiveIndex = channel.index();
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
        ChannelCatalog.Channel saved = ChannelCatalog.byId(preferences.getString(PREF_KEY_CHANNEL_ID, null));
        if (saved == null) {
            saved = ChannelCatalog.byNumber(preferences.getInt(PREF_KEY_LIVE_INDEX, 0) + 1);
            if (saved != null) {
                storeChannel(preferences, saved);
            }
        }
        currentLiveIndex = saved == null ? 0 : saved.index();
        loadLiveUrl();
    }

    private void saveCurrentLiveIndex() {
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        storeChannel(preferences, ChannelCatalog.at(playback.activeChannel()));
    }

    private void storeChannel(SharedPreferences preferences, ChannelCatalog.Channel channel) {
        preferences.edit().putString(PREF_KEY_CHANNEL_ID, channel.id)
                .remove(PREF_KEY_LIVE_INDEX).apply();
    }

    private void loadLiveUrl() {
        clearNumericInput();
        ChannelCatalog.Channel channel = ChannelCatalog.at(currentLiveIndex);
        playback.load(channel.index(), channel.url, channel.source);
    }

    private void navigateToPreviousLive() {
        if (enableDirectChannelChange) {
            currentLiveIndex = (currentLiveIndex - 1 + ChannelCatalog.size()) % ChannelCatalog.size();
            loadLiveUrl();
        } else {
            openChannelList((currentLiveIndex - 1 + ChannelCatalog.size()) % ChannelCatalog.size());
        }
    }

    private void navigateToNextLive() {
        if (enableDirectChannelChange) {
            currentLiveIndex = (currentLiveIndex + 1 + ChannelCatalog.size()) % ChannelCatalog.size();
            loadLiveUrl();
        } else {
            openChannelList((currentLiveIndex + 1 + ChannelCatalog.size()) % ChannelCatalog.size());
        }
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

}
