package com.cradletrial.cradlevhtapp.utilitiles;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Zipper {
    private static final int BUFFER_SIZE = 1024 * 1024;

    public static File zip(List<File> files, File zipFile) {
        // source: https://stackoverflow.com/questions/25562262/how-to-compress-files-into-zip-folder-in-android
        try {
            BufferedInputStream origin = null;
            FileOutputStream dest = new FileOutputStream(zipFile.getAbsolutePath());
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                    dest));
            byte data[] = new byte[BUFFER_SIZE];

            for (File file : files) {
                Log.v("Compress", "Adding: " + file);
                FileInputStream fi = new FileInputStream(file);
                origin = new BufferedInputStream(fi, BUFFER_SIZE);

                ZipEntry entry = new ZipEntry(file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf("/") + 1));
                out.putNextEntry(entry);
                int count;

                while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }

            out.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return zipFile;
    }
}
