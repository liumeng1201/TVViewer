package com.eanyatonic.cctvViewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class ChannelCatalogTest {
    @Test
    public void channelNumbersSourcesAndIdsAreConsistent() {
        assertEquals(41, ChannelCatalog.size());
        assertEquals(20, ChannelCatalog.CCTV.size());
        assertEquals(21, ChannelCatalog.PROVINCIAL.size());
        assertNull(ChannelCatalog.byNumber(0));
        assertNull(ChannelCatalog.byNumber(42));

        Set<String> ids = new HashSet<>();
        for (int number = 1; number <= ChannelCatalog.size(); number++) {
            ChannelCatalog.Channel channel = ChannelCatalog.byNumber(number);
            assertTrue(ids.add(channel.id));
            assertSame(channel, ChannelCatalog.byId(channel.id));
            assertEquals(number, channel.number);
            assertTrue(channel.url, channel.url.startsWith(number <= 20
                    ? "https://tv.cctv.com/live/" : "https://www.yangshipin.cn/tv/home?pid="));
        }
        assertEquals("山东卫视", ChannelCatalog.byNumber(41).name);
    }

    @Test
    public void migratesStoredIndexAndOpensSettingsFromDrawerWithRemote() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        preferences.edit().clear().putInt("currentLiveIndex", 40).commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(KernelFallback.SYSTEM_WEBVIEW, true).commit();

        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(ChannelCatalog.byNumber(41).id, preferences.getString("currentChannelId", null));
                assertFalse(preferences.contains("currentLiveIndex"));
                press(activity, KeyEvent.KEYCODE_DPAD_CENTER);
                LinearLayout channels = activity.findViewById(R.id.subMenuLocal);
                assertEquals(View.VISIBLE, channels.getVisibility());
                TextView lastChannel = (TextView) channels.getChildAt(channels.getChildCount() - 1);
                assertEquals("山东卫视", lastChannel.getText().toString());
                assertTrue(lastChannel.hasFocus());
                press(activity, KeyEvent.KEYCODE_DPAD_LEFT);
                press(activity, KeyEvent.KEYCODE_DPAD_DOWN);
                press(activity, KeyEvent.KEYCODE_DPAD_CENTER);
            });
            assertNotNull(waitForSettings());
        } finally {
            preferences.edit().clear().commit();
        }
    }

    private static void press(MainActivity activity, int keyCode) {
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    }

    private static Activity waitForSettings() {
        Activity[] settings = new Activity[1];
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (settings[0] == null && SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activity instanceof SettingsActivity) {
                        settings[0] = activity;
                        activity.finish();
                        break;
                    }
                }
            });
            if (settings[0] == null) {
                SystemClock.sleep(50);
            }
        }
        return settings[0];
    }
}
