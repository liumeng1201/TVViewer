package com.eanyatonic.cctvViewer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

final class KernelFallback {
    static final String SYSTEM_WEBVIEW = "sys_webview";
    static final String INSTALL_IN_PROGRESS = "x5_install_in_progress";

    private KernelFallback() {
    }

    static void preferSystem(SharedPreferences preferences) {
        // Persist the fallback before restarting the playback activity.
        preferences.edit().putBoolean(SYSTEM_WEBVIEW, true)
                .remove(INSTALL_IN_PROGRESS).commit();
    }

    static void returnToPlayback(Activity activity, int message) {
        if (message != 0) {
            preferSystem(PreferenceManager.getDefaultSharedPreferences(activity));
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
        Intent main = new Intent(activity, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(main);
        activity.finish();
    }
}
