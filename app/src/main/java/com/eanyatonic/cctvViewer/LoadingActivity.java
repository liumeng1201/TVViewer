package com.eanyatonic.cctvViewer;

import android.content.Intent;
import android.content.SharedPreferences;
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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService preparation = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private boolean installing;
    private boolean completed;
    private File downloadedKernel;
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
        if ("0".equals(mode) && !AssetUtil.fileExistsInAssets(this, X5Kernel.BUNDLED.filename)) {
            complete(R.string.x5_kernel_missing);
            return;
        }
        X5Kernel kernel = X5Kernel.select(mode, X5Kernel.is64BitProcess());
        String downloadedSource = getIntent().getStringExtra(DownloadActivity.EXTRA_KERNEL);
        if (kernel == null || (downloadedSource != null && !kernel.name().equals(downloadedSource))) {
            complete(R.string.x5_kernel_incompatible);
            return;
        }

        if (kernel == X5Kernel.BUNDLED) {
            handler.postDelayed(onTimeout, INSTALL_TIMEOUT_MS);
            preparation.execute(() -> prepareBundledKernel(kernel));
            return;
        }

        downloadedKernel = kernel.downloadedFile(this);
        if (downloadedKernel == null) {
            complete(R.string.x5_kernel_missing);
            return;
        }
        if (!downloadedKernel.isFile()) {
            if (downloadedSource != null) {
                complete(R.string.x5_kernel_missing);
                return;
            }
            Intent download = new Intent(this, DownloadActivity.class);
            download.putExtra(DownloadActivity.EXTRA_KERNEL, kernel.name());
            startActivity(download);
            finish();
            return;
        }

        handler.postDelayed(onTimeout, INSTALL_TIMEOUT_MS);
        preparation.execute(() -> prepareInstallation(kernel, downloadedKernel));
    }

    private void prepareBundledKernel(X5Kernel kernel) {
        File staged = null;
        File prepared = null;
        try {
            File directory = new File(getFilesDir(), "x5-kernel");
            if (directory.isDirectory() || directory.mkdirs()) {
                staged = File.createTempFile("x5-kernel-", ".tmp", directory);
                AssetUtil.copy(this, kernel.filename, staged);
                File destination = new File(directory, kernel.filename);
                if (!Thread.currentThread().isInterrupted() && staged.renameTo(destination)) {
                    prepared = destination;
                }
            }
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Could not prepare the bundled X5 kernel", error);
        } finally {
            if (staged != null && staged.exists() && !staged.delete()) {
                Log.w(TAG, "Could not remove the temporary X5 kernel");
            }
        }
        prepareInstallation(kernel, prepared);
    }

    private void prepareInstallation(X5Kernel kernel, File file) {
        int errorMessage = 0;
        if (file == null) {
            errorMessage = R.string.x5_kernel_missing;
        } else {
            try {
                if (!kernel.matchesAbi(file)) {
                    errorMessage = R.string.x5_kernel_incompatible;
                }
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Could not inspect the X5 kernel archive", error);
                errorMessage = R.string.x5_kernel_missing;
            }
        }

        int failure = errorMessage;
        handler.post(() -> {
            if (completed || isFinishing() || isDestroyed()) {
                return;
            }
            if (failure != 0) {
                removeDownloadedKernel();
                complete(failure);
            } else {
                install(kernel.version, file);
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
        if (code != X5_INSTALL_SUCCESS) {
            removeDownloadedKernel();
        }
        complete(R.string.x5_install_failed);
    }

    private void removeDownloadedKernel() {
        if (downloadedKernel != null && downloadedKernel.isFile() && !downloadedKernel.delete()) {
            Log.w(TAG, "Could not remove the failed X5 kernel");
        }
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

}
