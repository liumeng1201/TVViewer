package com.eanyatonic.cctvViewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class StartupAndRemoteTest {
    private Context context;
    private SharedPreferences preferences;

    @Before
    public void resetPreferences() {
        context = ApplicationProvider.getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().clear().commit();
        context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After
    public void clearPreferences() {
        preferences.edit().clear().commit();
    }

    @Test
    public void interruptedX5AttemptRestartsInSystemWebView() {
        preferences.edit().putBoolean(KernelFallback.SYSTEM_WEBVIEW, false)
                .putBoolean(KernelFallback.INSTALL_IN_PROGRESS, true).commit();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(preferences.getBoolean(KernelFallback.SYSTEM_WEBVIEW, false));
                assertFalse(preferences.contains(KernelFallback.INSTALL_IN_PROGRESS));
                assertTrue(activity.findViewById(R.id.webViewContainer) != null);
            });
        }
    }

    @Test
    public void missingBundledKernelReturnsToMainActivity() {
        assertFalse(AssetUtil.fileExistsInAssets(context, "045738_x5.tbs.apk"));
        preferences.edit().putBoolean(KernelFallback.SYSTEM_WEBVIEW, false)
                .putString("x5_webview_version", "0").commit();

        Intent intent = new Intent(context, LoadingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        MainActivity main = waitForMainActivity();
        try {
            assertTrue(preferences.getBoolean(KernelFallback.SYSTEM_WEBVIEW, false));
            assertFalse(preferences.contains(KernelFallback.INSTALL_IN_PROGRESS));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(main::finish);
        }
    }

    @Test
    public void longPressAndExtraDigitsCannotOverflowChannelNumber() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                pressDigit(activity, KeyEvent.KEYCODE_9, 0);
                for (int repeat = 1; repeat <= 30; repeat++) {
                    pressDigit(activity, KeyEvent.KEYCODE_9, repeat);
                }
                TextView input = activity.findViewById(R.id.inputTextView);
                assertEquals("换台：9", input.getText().toString());

                pressDigit(activity, KeyEvent.KEYCODE_9, 0);
                for (int i = 0; i < 12; i++) {
                    pressDigit(activity, KeyEvent.KEYCODE_9, 0);
                }
                assertEquals("换台：99", input.getText().toString());
            });

            waitForInvalidNumber(scenario);
            scenario.onActivity(activity -> {
                pressDigit(activity, KeyEvent.KEYCODE_0, 0);
            });
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                TextView input = activity.findViewById(R.id.inputTextView);
                assertEquals(View.GONE, input.getVisibility());
            });
        }
    }

    private static void pressDigit(MainActivity activity, int key, int repeat) {
        long now = SystemClock.uptimeMillis();
        activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, repeat));
    }

    private static void waitForInvalidNumber(ActivityScenario<MainActivity> scenario) {
        AtomicBoolean invalid = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (!invalid.get() && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                TextView input = activity.findViewById(R.id.inputTextView);
                TextView overlay = activity.findViewById(R.id.overlayTextView);
                invalid.set(input.getVisibility() == View.GONE
                        && "频道号无效".contentEquals(overlay.getText()));
            });
            if (!invalid.get()) {
                SystemClock.sleep(50);
            }
        }
        assertTrue("Invalid number should be cleared and reported", invalid.get());
    }

    private static MainActivity waitForMainActivity() {
        MainActivity[] result = new MainActivity[1];
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (result[0] == null && SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof MainActivity && !activity.isFinishing()) {
                        result[0] = (MainActivity) activity;
                    }
                }
            });
            if (result[0] == null) {
                SystemClock.sleep(50);
            }
        }
        assertTrue("System WebView playback should be resumed", result[0] != null);
        return result[0];
    }
}
