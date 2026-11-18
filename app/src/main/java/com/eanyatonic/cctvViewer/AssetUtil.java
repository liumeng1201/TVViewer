package com.eanyatonic.cctvViewer;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class AssetUtil {

    public static boolean fileExistsInAssets(Context context, String fileName) {
        AssetManager assetManager = context.getAssets();
        try {
            String[] files = assetManager.list("");
            if (files != null) {
                for (String file : files) {
                    if (file.equals(fileName)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    static String readText(Context context, String path) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            readAsset(context, path, output);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static void copy(Context context, String path, File target) throws IOException {
        try (FileOutputStream output = new FileOutputStream(target)) {
            readAsset(context, path, output);
        }
    }

    private static void readAsset(Context context, String path, OutputStream output) throws IOException {
        try (InputStream input = context.getAssets().open(path)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Reading asset was interrupted");
                }
                output.write(buffer, 0, bytesRead);
            }
        }
    }
}
