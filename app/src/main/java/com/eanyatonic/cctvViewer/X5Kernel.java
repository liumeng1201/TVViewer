package com.eanyatonic.cctvViewer;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

enum X5Kernel {
    BUNDLED("045738_x5.tbs.apk", 45738, false),
    ONLINE_32("045738_x5.tbs_.apk", 45738, false),
    ONLINE_64("046007_x5.tbs_.apk", 46007, true);

    private static final String DOWNLOAD_BASE_URL = "http://void-tech.cn/wp-content/uploads/2024/10/";

    final String filename;
    final int version;
    final boolean is64Bit;

    X5Kernel(String filename, int version, boolean is64Bit) {
        this.filename = filename;
        this.version = version;
        this.is64Bit = is64Bit;
    }

    static X5Kernel select(String mode, Boolean process64Bit) {
        if (process64Bit == null) {
            return null;
        }
        if ("0".equals(mode)) {
            return process64Bit ? null : BUNDLED;
        }
        if ("1".equals(mode)) {
            return process64Bit ? ONLINE_64 : ONLINE_32;
        }
        if ("2".equals(mode) && process64Bit) {
            return ONLINE_64;
        }
        if ("3".equals(mode) && !process64Bit) {
            return ONLINE_32;
        }
        return null;
    }

    static Boolean is64BitProcess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Process.is64Bit();
        }
        try {
            String executable = Os.readlink("/proc/self/exe");
            if (executable.endsWith("app_process64")) {
                return true;
            }
            if (executable.endsWith("app_process32")) {
                return false;
            }
        } catch (ErrnoException ignored) {
            // Some Android 5 devices restrict access to process links.
        }
        if (Build.SUPPORTED_64_BIT_ABIS.length == 0) {
            return false;
        }
        return Build.SUPPORTED_32_BIT_ABIS.length == 0 ? Boolean.TRUE : null;
    }

    static File downloadDirectory(Context context) {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    }

    File downloadedFile(Context context) {
        File directory = downloadDirectory(context);
        return directory == null ? null : new File(directory, filename);
    }

    String downloadUrl() {
        return DOWNLOAD_BASE_URL + filename;
    }

    boolean matchesAbi(File apk) throws IOException {
        String[] supportedAbis = is64Bit ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        return containsSupportedLibrary(apk, supportedAbis);
    }

    static boolean containsSupportedLibrary(File apk, String[] supportedAbis) throws IOException {
        try (ZipFile archive = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (!entry.endsWith(".so")) {
                    continue;
                }
                for (String abi : supportedAbis) {
                    if (entry.startsWith("lib/" + abi + "/")
                            || ("armeabi-v7a".equals(abi) && entry.startsWith("lib/armeabi/"))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
