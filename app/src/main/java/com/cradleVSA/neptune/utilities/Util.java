package com.cradleVSA.neptune.utilities;

import android.util.Log;

public class Util {
    private static final String TAG = "Util";

    public static void ensure(boolean condition) {
        if (!condition) {
            RuntimeException e = new RuntimeException("ASSERT FAILED (custom)");
            Log.wtf(TAG, "Custom assert failure at: " + Log.getStackTraceString(e));
            throw e;
        }
    }

    public static boolean isNullOrEmpty(String s) {
        return s == null || s.length() == 0;
    }

    public static boolean stringNullOrEmpty(String s) {
        if (s == null || s.length() == 0 || s.toLowerCase().equals("null")) {
            return true;
        }
        return false;
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
}
