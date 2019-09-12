package com.cradletrial.cradlevhtapp.utilitiles;

import android.support.v7.preference.EditTextPreference;
import android.util.Log;

import java.io.File;
import java.util.List;

public class Util {
    private static final String TAG = "Util";

    public static void ensure(boolean condition) {
        if (!condition) {
            RuntimeException e = new RuntimeException("ASSERT FAILED (custom)");
            Log.wtf(TAG, "Custom assert failure at: " + Log.getStackTraceString(e));
            throw e;
        }
    }

    public static final boolean isTrue(Boolean b) {
        return (b != null) && b;
    }

    public static boolean isNullOrEmpty(String s) {
        return s == null || s.length() == 0;
    }

    public static boolean isNullOrZero(Integer value) {
        return value == null || value == 0;
    }

    public static int stringToIntOr0(String str) {
        int value = 0;
        try {
            value = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            Log.d(TAG, "Unable to convert string to int: '" + str + "'");
        }
        return value;
    }

    public static String makeRepeatString(String s, int length) {
        String msg = "";
        for (int i = 0; i < length; i++) {
            msg += s;
        }
        return msg;
    }

    public static void deleteFilesInList(List<File> filesToZip) {
        for (File f : filesToZip) {
            deleteFile(f);
        }
    }

    public static void deleteFile(File f) {
        if (f != null && f.exists()) {
            if (!f.delete()) {
                Log.d(TAG, "Unable to delete file: " + f);
            }
        }
    }
}
