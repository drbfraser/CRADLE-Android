package com.cradle.neptune.utilitiles;

import android.content.Context;
import android.os.AsyncTask;

import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;

import static com.cradle.neptune.view.LoginActivity.PatientDownloadingNotificationID;

public class JSONArrayParseAsyncTask extends AsyncTask {

    JSONArray jsonArray;
    Context context;
    public JSONArrayParseAsyncTask(JSONArray jsonArray,Context context){
        this.jsonArray=jsonArray;
        this.context = context;
    }
    @Override
    protected Object doInBackground(Object[] objects) {
        return null;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

    }

}
