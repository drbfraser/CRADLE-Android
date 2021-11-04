package com.cradleplatform.neptune.utilities;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.cradleplatform.neptune.R;

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

    public static String getVersionName(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return context.getString(R.string.not_avaliable);
        }
    }
}
