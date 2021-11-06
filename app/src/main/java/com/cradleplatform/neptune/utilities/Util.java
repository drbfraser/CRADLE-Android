package com.cradleplatform.neptune.utilities;

import android.util.Log;
import java.time.ZonedDateTime;

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
        return s == null || s.length() == 0 || s.toLowerCase().equals("null");
    }

    public static boolean isRecheckNeededNow(Long dateRecheckVitalsNeeded) {
        if (dateRecheckVitalsNeeded == null)
            return false;

        long timeLeft = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            timeLeft = dateRecheckVitalsNeeded - ZonedDateTime.now().toEpochSecond();
        }

        return timeLeft <= 0;
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
