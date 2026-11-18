package com.eanyatonic.cctvViewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.TbsListener;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadingActivity extends AppCompatActivity {
    private static final String TAG = "LoadingActivity";
    private static final int X5_INSTALL_SUCCESS = 200;
    private static final long INSTALL_TIMEOUT_MS = 45_000;
    private static final String BUNDLED_KERNEL = "045738_x5.tbs.apk";
    private static final String KERNEL_32 = "045738_x5.tbs_.apk";
    private static final String KERNEL_64 = "046007_x5.tbs_.apk";
    private static final String DOWNLOAD_BASE_URL = "http://void-tech.cn/wp-content/uploads/2024/10/";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService preparation = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private boolean installing;
    private boolean completed;
    private String downloadedKernel;
    private final Runnable onTimeout = () -> complete(R.string.x5_install_timeout);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        findViewById(R.id.useSystemWebView).setOnClickListener(v -> onBackPressed());

        if (preferences.getBoolean(KernelFallback.INSTALL_IN_PROGRESS, false)) {
            complete(R.string.x5_install_interrupted);
            return;
        }

        String mode = preferences.getString("x5_webview_version", "0");
        if ("0".equals(mode)) {
            if (!AssetUtil.fileExistsInAssets(this, BUNDLED_KERNEL)) {
                complete(R.string.x5_kernel_missing);
                return;
            }
            handler.postDelayed(onTimeout, INSTALL_TIMEOUT_MS);
            preparation.execute(this::prepareBundledKernel);
            return;
        }

        boolean use64Bit = "2".equals(mode) || ("1".equals(mode) && isCpu64Bit());
        downloadedKernel = use64Bit ? KERNEL_64 : KERNEL_32;
        File directory = getExternalFilesDir("Download");
        File file = directory == null ? null : new File(directory, downloadedKernel);
        if (file == null || !file.isFile()) {
            Intent download = new Intent(this, DownloadActivity.class);
            download.putExtra("file_url", DOWNLOAD_BASE_URL + downloadedKernel);
            startActivity(download);
            finish();
            return;
        }

        handler.postDelayed(onTimeout, INSTALL_TIMEOUT_MS);
        install(use64Bit ? 46007 : 45738, file);
    }

    private void prepareBundledKernel() {
        File staged = null;
        File kernel = null;
        try {
            File directory = FileUtils.getTBSFileDir(getApplicationContext());
            if (directory != null && (directory.isDirectory() || directory.mkdirs())) {
                staged = File.createTempFile("x5-kernel-", ".tmp", directory);
                if (FileUtils.copyAssets(getApplicationContext(), BUNDLED_KERNEL, staged.getPath())
                        && !Thread.currentThread().isInterrupted()) {
                    File destination = new File(directory, BUNDLED_KERNEL);
                    if (staged.renameTo(destination)) {
                        kernel = destination;
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Could not prepare the bundled X5 kernel", error);
        } finally {
            if (staged != null && staged.exists() && !staged.delete()) {
                Log.w(TAG, "Could not remove the temporary X5 kernel");
            }
        }

        File prepared = kernel;
        handler.post(() -> {
            if (completed || isFinishing() || isDestroyed()) {
                return;
            }
            if (prepared == null) {
                complete(R.string.x5_kernel_missing);
            } else {
                install(45738, prepared);
            }
        });
    }

    private void install(int version, File file) {
        if (completed || installing) {
            return;
        }
        installing = true;
        if (!preferences.edit().putBoolean(KernelFallback.INSTALL_IN_PROGRESS, true).commit()) {
            complete(R.string.x5_install_failed);
            return;
        }

        try {
            QbSdk.reset(getApplicationContext());
            QbSdk.setTbsListener(new TbsListener() {
                @Override
                public void onDownloadFinish(int code) {
                }

                @Override
                public void onDownloadProgress(int progress) {
                }

                @Override
                public void onInstallFinish(int code) {
                    handler.post(() -> verifyInstall(code));
                }
            });
            QbSdk.installLocalTbsCore(getApplicationContext(), version, file.getPath());
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Could not start X5 installation", error);
            complete(R.string.x5_install_failed);
        }
    }

    private void verifyInstall(int code) {
        if (completed || isFinishing() || isDestroyed()) {
            return;
        }

        try {
            if (code == X5_INSTALL_SUCCESS && QbSdk.canLoadX5(getApplicationContext())) {
                complete(0);
                return;
            }
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Could not verify the X5 installation", error);
        }

        Log.w(TAG, "X5 installation failed with status " + code);
        if (code != X5_INSTALL_SUCCESS && downloadedKernel != null) {
            FileUtils.deleteFile(getApplicationContext(), downloadedKernel);
        }
        complete(R.string.x5_install_failed);
    }

    private void complete(int message) {
        if (completed || isFinishing() || isDestroyed()) {
            return;
        }
        completed = true;
        handler.removeCallbacksAndMessages(null);
        if (message == 0) {
            preferences.edit().remove(KernelFallback.INSTALL_IN_PROGRESS).commit();
        }
        KernelFallback.returnToPlayback(this, message);
    }

    @Override
    public void onBackPressed() {
        complete(R.string.x5_install_cancelled);
    }

    @Override
    protected void onDestroy() {
        completed = true;
        handler.removeCallbacksAndMessages(null);
        preparation.shutdownNow();
        if (installing) {
            QbSdk.setTbsListener(null);
        }
        super.onDestroy();
    }

    private static boolean isCpu64Bit() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.contains("64")) {
                return true;
            }
        }
        return false;
    }
}
