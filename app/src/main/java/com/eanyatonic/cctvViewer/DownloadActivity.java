package com.eanyatonic.cctvViewer;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class DownloadActivity extends AppCompatActivity {

    private DownloadManager downloadManager;
    private static final long DOWNLOAD_TIMEOUT_MS = 5 * 60 * 1000L;
    private long downloadId;
    private ProgressBar progressBar;
    private TextView downloadStatus;
    private Button retryButton;
    private Handler handler;
    private volatile boolean isDestroyed = false;
    private boolean completed;
    private final Runnable onTimeout = () -> returnToSystem(R.string.x5_install_timeout);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        progressBar = findViewById(R.id.progressBar);
        downloadStatus = findViewById(R.id.downloadStatus);
        retryButton = findViewById(R.id.retryButton);
        handler = new Handler(Looper.getMainLooper());

        String fileUrl = getIntent().getStringExtra("file_url");
        startDownload(fileUrl);
        findViewById(R.id.useSystemWebView).setOnClickListener(v -> onBackPressed());

        // 注册广播接收器
        registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        retryButton.setOnClickListener(v -> {
            retryButton.setVisibility(View.GONE);
            startDownload(fileUrl);
        });
    }

    private void startDownload(String url) {
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);

        String fileName = uri.getLastPathSegment();
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName);

        try {
            downloadId = downloadManager.enqueue(request);
        } catch (SecurityException e) {
            handler.post(() -> {
                downloadStatus.setText("无权限，无法下载");
                retryButton.setVisibility(View.VISIBLE);
            });
            return;
        } catch (Exception e) {
            handler.post(() -> {
                downloadStatus.setText("下载启动失败");
                retryButton.setVisibility(View.VISIBLE);
            });
            return;
        }

        handler.removeCallbacks(onTimeout);
        handler.postDelayed(onTimeout, DOWNLOAD_TIMEOUT_MS);
        new Thread(() -> {
            boolean downloading = true;
            while (downloading && !isDestroyed) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        continue;
                    }

                    int bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));

                    if (bytesTotal > 0) {
                        int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);
                        Log.d("Download", String.valueOf(progress));
                        handler.post(() -> {
                            if (!isDestroyed) {
                                progressBar.setProgress(progress);
                            }
                        });
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        downloading = false;
                        handler.post(() -> {
                            if (isDestroyed || completed || isFinishing()) {
                                return;
                            }
                            completed = true;
                            handler.removeCallbacks(onTimeout);
                            downloadStatus.setText("下载完成");
                            Intent intent = new Intent(DownloadActivity.this, LoadingActivity.class);
                            startActivity(intent);
                            finish();
                        });
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        downloading = false;
                        handler.post(() -> {
                            if (!isDestroyed && !completed) {
                                handler.removeCallbacks(onTimeout);
                                downloadStatus.setText("下载失败");
                                retryButton.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadId && !isDestroyed) {
                Toast.makeText(DownloadActivity.this, "下载完成", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    public void onBackPressed() {
        returnToSystem(R.string.x5_install_cancelled);
    }

    private void returnToSystem(int message) {
        if (!completed && !isDestroyed && !isFinishing()) {
            completed = true;
            handler.removeCallbacks(onTimeout);
            if (downloadManager != null && downloadId != 0) {
                downloadManager.remove(downloadId);
            }
            KernelFallback.returnToPlayback(this, message);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(onDownloadComplete);
    }
}