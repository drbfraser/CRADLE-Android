package com.cradletrial.cradlevhtapp.view.ui.intro;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.view.ui.reading.PatientInfoFragment;

import java.util.ArrayList;
import java.util.List;

/**
 *  Confirm privacy policy with user
 */
public class PermissionsFragment extends IntroBaseFragment {
    private View mView;
    private List<CheckBox> checkBoxes = new ArrayList<>();

    public PermissionsFragment() {
        // Required empty public constructor
        TAG = PatientInfoFragment.class.getName();
    }

    public static PermissionsFragment newInstance() {
        PermissionsFragment fragment = new PermissionsFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_intro_permissions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mView = view;

        updateDisplay();
        setupGrantPermissions();
    }

    @Override
    public void onMyBeingDisplayed() {
        // may not have created view yet.
        if (mView == null) {
            return;
        }
        hideKeyboard();
        updateDisplay();

        // Disable NEXT button until all granted
        activityCallbackListener.setNextButtonEnabled(areAllPermissionsGranted(getContext()));

    }

    @Override
    public boolean onMyBeingHidden() {
        // may not have created view yet.
        if (mView == null) {
            return true;
        }
        return true;
    }



    private void updateDisplay() {
        // Show permissions message
        WebView wv = getView().findViewById(R.id.wvPermissions);
        String htmlContents = getString(R.string.intro_permission_description);
        wv.loadDataWithBaseURL(null, htmlContents, "text/html", "utf-8", null);

        boolean allGranted = areAllPermissionsGranted(getContext());

        // Configure screen if we need permissions
        Button btn = getView().findViewById(R.id.btnGrantPermissions);
        btn.setVisibility(allGranted ? View.GONE : View.VISIBLE);

        TextView tvAllGranted = getView().findViewById(R.id.txtAllGranted);
        tvAllGranted.setVisibility(allGranted ? View.VISIBLE : View.GONE);

        // Each time we load, hide this; shown if granting fails.
        TextView tvMore = getView().findViewById(R.id.txtMoreNeededWarning);
        tvMore.setVisibility(View.GONE);

        // callback to activity done when we get focus
    }


    /*
        Permissions
     */
    private static final String[] requiredPermissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
    };
    private static final int MY_PERMISSIONS_REQUEST = 1515;


    private void setupGrantPermissions() {
        Button btn = getView().findViewById(R.id.btnGrantPermissions);
        btn.setOnClickListener(view -> requestAllPermissions());
    }

    private void requestAllPermissions() {
        requestPermissions(requiredPermissions, MY_PERMISSIONS_REQUEST);
    }
    public static boolean areAllPermissionsGranted(Context context) {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        // It seems that the requestCode is not intact from our call.
        // So... ignore it!

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
            }
        }

        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && allGranted) {
            // permission was granted, yay!
            activityCallbackListener.advanceToNextPage();
            activityCallbackListener.setNextButtonEnabled(true);
        } else {
            // permission denied, boo!
            TextView tvMore = getView().findViewById(R.id.txtMoreNeededWarning);
            tvMore.setVisibility(View.VISIBLE);
        }
    }

}
