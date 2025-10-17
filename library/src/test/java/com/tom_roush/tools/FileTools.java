package com.tom_roush.tools;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class FileTools {

    public static File getInternetFile(String url, String fileName) {
        var outputFile = new File("target/pdfs",fileName);

        if(!outputFile.exists()){
            outputFile.mkdirs();
        }

        try(var stream = new URL(url).openStream()) {
            java.nio.file.Files.copy(
                    stream,
                    outputFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new File("target/pdfs",fileName);
    }


    public static void saveBitmap(File file, Bitmap bitmap, Bitmap.CompressFormat format) throws IOException {
        File parent = file.getParentFile();
        if (!parent.exists()) parent.mkdirs();

        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(format, 100, out);
        }
    }
}
