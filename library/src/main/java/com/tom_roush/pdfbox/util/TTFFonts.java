package com.tom_roush.pdfbox.util;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class TTFFonts {
    public static List<File> fontList = new ArrayList<>();
    public static String cachePath = "";


    public static void loadTTFFonts(Context context) {
        cachePath = context.getCacheDir().getAbsolutePath();
        fontList.clear();
        try {
            String path = "com/tom_roush/pdfbox/resources/ttf";
            String[] files = context.getAssets().list(path);
            if (files != null) {
                for (String filename : files) {
                    if (filename.toLowerCase().endsWith(".ttf")) {
                        InputStream is = context.getAssets().open(path + "/" + filename);
                        File outFile = new File(context.getCacheDir(), filename);
                        FileOutputStream fos = new FileOutputStream(outFile);

                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                        fos.flush();
                        fos.close();
                        is.close();

                        fontList.add(outFile);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
