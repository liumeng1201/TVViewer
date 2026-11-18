package com.eanyatonic.cctvViewer;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadActivity extends AppCompatActivity {
    static final String EXTRA_KERNEL = "kernel";
    private static final String TAG = "X5Download";
    private static final long DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final long QUERY_INTERVAL_MS = 1000;
    private static final int STATUS_MISSING = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable poll = this::queryDownload;
    private final Runnable timeout = () -> returnToSystem(R.string.x5_install_timeout);
    private SharedPreferences downloads;
    private DownloadManager manager;
    private ProgressBar progressBar;
    private TextView statusText;
    private Button retry;
    private X5Kernel kernel;
    private Task task;
    private int failure;
    private boolean started;
    private boolean completed;
    private boolean querying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        downloads = getSharedPreferences("x5_download", MODE_PRIVATE);
        manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.downloadStatus);
        retry = findViewById(R.id.retryButton);
        retry.setOnClickListener(view -> enqueue());
        findViewById(R.id.useSystemWebView).setOnClickListener(view -> onBackPressed());

        try {
            kernel = X5Kernel.valueOf(getIntent().getStringExtra(EXTRA_KERNEL));
            if (kernel == X5Kernel.BUNDLED || manager == null) {
                throw new IllegalArgumentException("Expected an online X5 kernel and DownloadManager");
            }
        } catch (IllegalArgumentException | NullPointerException error) {
            Log.w(TAG, "Invalid X5 download request", error);
            returnToSystem(R.string.x5_install_failed);
            return;
        }
        task = restoreTask();
        if (task == null) {
            failure = savedInstanceState == null ? 0 : savedInstanceState.getInt("failure");
            if (failure != 0) {
                showFailure(failure);
            } else {
                enqueue();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        monitorTask();
    }

    @Override
    protected void onStop() {
        started = false;
        handler.removeCallbacks(poll);
        handler.removeCallbacks(timeout);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("failure", failure);
        super.onSaveInstanceState(outState);
    }

    private void enqueue() {
        if (kernel == null || task != null || completed) {
            return;
        }
        failure = 0;
        retry.setVisibility(View.GONE);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(true);
        statusText.setText(R.string.x5_download_in_progress);

        String partName = "x5-" + UUID.randomUUID() + ".part";
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(kernel.downloadUrl()));
            request.setTitle(getString(R.string.x5_download_title));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, partName);
            long id = manager.enqueue(request);
            task = new Task(id, partName, System.currentTimeMillis() + DOWNLOAD_TIMEOUT_MS);
            if (!downloads.edit().putLong("id", id).putString("kernel", kernel.name())
                    .putString("part", partName).putLong("deadline", task.deadline).commit()) {
                throw new IllegalStateException("Could not persist the download");
            }
            monitorTask();
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not enqueue X5 download", error);
            clearTask();
            showFailure(R.string.x5_download_start_failed);
        }
    }

    private Task restoreTask() {
        long id = downloads.getLong("id", -1);
        String part = downloads.getString("part", "");
        long deadline = downloads.getLong("deadline", 0);
        if (id < 0) {
            return null;
        }
        if (!kernel.name().equals(downloads.getString("kernel", null))
                || !part.matches("x5-[0-9a-f-]{36}\\.part") || deadline <= 0) {
            removeDownload(id);
            downloads.edit().clear().commit();
            return null;
        }
        return new Task(id, part, deadline);
    }

    private void monitorTask() {
        if (!started || task == null || completed) {
            return;
        }
        handler.removeCallbacks(poll);
        handler.removeCallbacks(timeout);
        long remaining = Math.max(0, task.deadline - System.currentTimeMillis());
        handler.postDelayed(timeout, Math.min(remaining, DOWNLOAD_TIMEOUT_MS));
        handler.post(poll);
    }

    private void queryDownload() {
        if (!started || completed || task == null || querying) {
            return;
        }
        Task requested = task;
        querying = true;
        worker.execute(() -> {
            DownloadState result = getDownloadState(requested.id);
            handler.post(() -> {
                querying = false;
                if (started && !completed && requested == task && !isFinishing()) {
                    render(result);
                } else if (started && !completed && task != null) {
                    handler.post(poll);
                }
            });
        });
    }

    private DownloadState getDownloadState(long id) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return new DownloadState(STATUS_MISSING, 0, 0);
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long bytes = cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            return new DownloadState(status, bytes, total);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not read X5 download state", error);
            return new DownloadState(DownloadManager.STATUS_FAILED, 0, 0);
        }
    }

    private void render(DownloadState result) {
        switch (result.status) {
            case DownloadManager.STATUS_SUCCESSFUL:
                finishDownload();
                return;
            case STATUS_MISSING:
                clearTask();
                showFailure(R.string.x5_download_missing);
                return;
            case DownloadManager.STATUS_FAILED:
                clearTask();
                showFailure(R.string.x5_download_failed);
                return;
            default:
                progressBar.setIndeterminate(result.total <= 0);
                if (result.total > 0) {
                    progressBar.setProgress((int) Math.min(100, result.bytes * 100.0 / result.total));
                }
                statusText.setText(result.status == DownloadManager.STATUS_PAUSED
                        ? R.string.x5_download_waiting : R.string.x5_download_in_progress);
                handler.postDelayed(poll, QUERY_INTERVAL_MS);
        }
    }

    private void finishDownload() {
        File destination = kernel.downloadedFile(this);
        if (destination == null || !task.publish(destination)) {
            clearTask();
            showFailure(R.string.x5_download_failed);
            return;
        }
        completed = true;
        clearTask();
        startActivity(new Intent(this, LoadingActivity.class).putExtra(EXTRA_KERNEL, kernel.name()));
        finish();
    }

    private void showFailure(int message) {
        failure = message;
        progressBar.setIndeterminate(false);
        statusText.setText(message);
        retry.setVisibility(View.VISIBLE);
    }

    private void clearTask() {
        handler.removeCallbacks(poll);
        handler.removeCallbacks(timeout);
        if (task != null) {
            removeDownload(task.id);
            task = null;
        }
        downloads.edit().clear().commit();
    }

    private void removeDownload(long id) {
        try {
            manager.remove(id);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not remove X5 download " + id, error);
        }
    }

    @Override
    public void onBackPressed() {
        returnToSystem(R.string.x5_install_cancelled);
    }

    private void returnToSystem(int message) {
        if (!completed && !isFinishing()) {
            completed = true;
            clearTask();
            KernelFallback.returnToPlayback(this, message);
        }
    }

    @Override
    protected void onDestroy() {
        completed = true;
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    static final class Task {
        final long id;
        final String partName;
        final long deadline;

        Task(long id, String partName, long deadline) {
            this.id = id;
            this.partName = partName;
            this.deadline = deadline;
        }

        boolean publish(File target) {
            File part = new File(target.getParentFile(), partName);
            // The process may have stopped just after moving this task's output.
            return part.isFile() ? part.renameTo(target) : target.isFile();
        }
    }

    private static final class DownloadState {
        final int status;
        final long bytes;
        final long total;

        DownloadState(int status, long bytes, long total) {
            this.status = status;
            this.bytes = bytes;
            this.total = total;
        }
    }
}
