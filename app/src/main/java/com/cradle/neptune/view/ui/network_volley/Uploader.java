package com.cradle.neptune.view.ui.network_volley;

import com.android.volley.NetworkResponse;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.view.LoginActivity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Uploader {
    private static final String TAG = "Uploader";
    // http related values
    private final String twoHyphens = "--";
    private final String lineEnd = "\r\n";
    private final String boundary = "apiclient-" + System.currentTimeMillis();
    private final String mimeType = "multipart/form-data;boundary=" + boundary;
    // what to send
    private String urlString;


    public Uploader(String urlString) {
        this.urlString = urlString;

    }

    public void doUpload(String jsonStringForBody, Response.Listener<NetworkResponse> callbackOk, Response.ErrorListener callbackFail) {

    }
}