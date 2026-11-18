package com.eanyatonic.cctvViewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public class X5DownloadTest {
    @Test
    public void selectsByProcessWidthAndChecksPackagedAbi() throws Exception {
        assertEquals(X5Kernel.ONLINE_32, X5Kernel.select("1", false));
        assertEquals(X5Kernel.ONLINE_64, X5Kernel.select("1", true));
        assertEquals(X5Kernel.BUNDLED, X5Kernel.select("0", false));
        assertNull(X5Kernel.select("0", true));
        assertNull(X5Kernel.select("2", false));
        assertNull(X5Kernel.select("3", true));
        assertNull(X5Kernel.select("1", null));

        Context context = ApplicationProvider.getApplicationContext();
        File apk = File.createTempFile("kernel-abi", ".apk", context.getCacheDir());
        try {
            try (ZipOutputStream archive = new ZipOutputStream(new FileOutputStream(apk))) {
                archive.putNextEntry(new ZipEntry("lib/armeabi/libexample.so"));
                archive.write(1);
                archive.closeEntry();
            }
            assertTrue(X5Kernel.containsSupportedLibrary(apk, new String[]{"armeabi-v7a"}));
            assertFalse(X5Kernel.containsSupportedLibrary(apk, new String[]{"arm64-v8a"}));
            assertFalse(X5Kernel.containsSupportedLibrary(apk, new String[]{"x86"}));
        } finally {
            apk.delete();
        }
    }

    @Test
    public void missingDownloadCanBeRetriedAndRecreationDoesNotEnqueue() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences downloads = context.getSharedPreferences("x5_download", Context.MODE_PRIVATE);
        downloads.edit().clear().putLong("id", Long.MAX_VALUE)
                .putString("kernel", X5Kernel.ONLINE_32.name())
                .putLong("deadline", System.currentTimeMillis() + 30_000).commit();
        Intent intent = new Intent(context, DownloadActivity.class)
                .putExtra(DownloadActivity.EXTRA_KERNEL, X5Kernel.ONLINE_32.name());

        try (ActivityScenario<DownloadActivity> scenario = ActivityScenario.launch(intent)) {
            waitForMissingDownload(scenario);
            assertFalse(downloads.contains("id"));
            scenario.recreate();
            waitForMissingDownload(scenario);
            assertFalse(downloads.contains("id"));
        } finally {
            downloads.edit().clear().commit();
        }
    }

    @Test
    public void completedDownloadIsCopiedIntoAnAppSpecificStagingFile() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File staged = DownloadActivity.copyToStaging(new ByteArrayInputStream(new byte[]{1, 2, 3}),
                context.getCacheDir());
        try {
            assertEquals(context.getCacheDir(), staged.getParentFile());
            assertTrue(staged.getName().endsWith(".part"));
            assertEquals(3, staged.length());
        } finally {
            staged.delete();
        }
    }

    private static void waitForMissingDownload(ActivityScenario<DownloadActivity> scenario) {
        AtomicBoolean retryVisible = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (!retryVisible.get() && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                TextView status = activity.findViewById(R.id.downloadStatus);
                View retry = activity.findViewById(R.id.retryButton);
                retryVisible.set(retry.getVisibility() == View.VISIBLE
                        && activity.getString(R.string.x5_download_missing).contentEquals(status.getText()));
            });
            if (!retryVisible.get()) {
                SystemClock.sleep(50);
            }
        }
        assertTrue("A missing task should stop polling and expose Retry", retryVisible.get());
    }
}
