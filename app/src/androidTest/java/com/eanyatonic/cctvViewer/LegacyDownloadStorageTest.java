package com.eanyatonic.cctvViewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LegacyDownloadStorageTest {
    @Test
    public void downloadManagerAcceptsARequestWithoutBroadStoragePermission() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals(PackageManager.PERMISSION_DENIED, context.getPackageManager()
                .checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, context.getPackageName()));

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = DownloadActivity.downloadRequest(
                Uri.parse("http://127.0.0.1:1/download-test"), "Storage compatibility test");
        long id = manager.enqueue(request);
        try {
            assertTrue(id > 0);
        } finally {
            manager.remove(id);
        }
    }
}
