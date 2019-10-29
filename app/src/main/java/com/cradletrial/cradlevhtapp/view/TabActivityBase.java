package com.cradletrial.cradlevhtapp.view;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

import com.cradletrial.cradlevhtapp.R;

abstract public class TabActivityBase extends AppCompatActivity {
    protected static final int TAB_ACTIVITY_BASE_SETTINGS_DONE = 948;
    private int myTabButtonId;

    public TabActivityBase(int tabButtonId) {
        this.myTabButtonId = tabButtonId;
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //changing the color for all the activity status bar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
        }
    }

}
