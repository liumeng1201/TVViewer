package com.eanyatonic.cctvViewer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
            ListPreference kernelSource = findPreference("x5_webview_version");
            boolean bundled = AssetUtil.fileExistsInAssets(requireContext(), X5Kernel.BUNDLED.filename);
            Boolean process64Bit = X5Kernel.is64BitProcess();

            if (kernelSource != null) {
                kernelSource.setOnPreferenceChangeListener((preference, value) -> {
                    if (preferences.getBoolean("debug_mode", false)) {
                        return true;
                    }
                    String mode = value.toString();
                    if ("0".equals(mode) && !bundled) {
                        Toast.makeText(requireContext(), R.string.x5_bundled_unavailable,
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    if (X5Kernel.select(mode, process64Bit) == null) {
                        Toast.makeText(requireContext(), R.string.x5_process_incompatible,
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    return true;
                });

                if (preferences.getBoolean("is_first_run", true)) {
                    if (!bundled || !Boolean.FALSE.equals(process64Bit)) {
                        kernelSource.setValue("1");
                    }
                    preferences.edit().putBoolean("is_first_run", false).apply();
                }
            }

            Preference systemInfo = findPreference("info");
            if (systemInfo != null) {
                String process = process64Bit == null ? "未知" : process64Bit ? "64 位" : "32 位";
                systemInfo.setSummary("Android " + Build.VERSION.RELEASE + " · 应用进程 " + process);
            }
            Preference version = findPreference("version");
            if (version != null) {
                version.setSummary(getString(R.string.app_version, BuildConfig.VERSION_NAME));
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            // 注册 SharedPreferences 监听器
            Objects.requireNonNull(getPreferenceScreen().getSharedPreferences())
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            // 取消注册监听器
            Objects.requireNonNull(getPreferenceScreen().getSharedPreferences())
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // 当任何设置项改变时，显示吐司提示
            Toast.makeText(getContext(), "重启应用后修改生效", Toast.LENGTH_SHORT).show();
        }
    }
}
