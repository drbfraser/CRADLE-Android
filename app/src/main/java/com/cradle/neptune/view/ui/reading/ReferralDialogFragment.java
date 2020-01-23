package com.cradle.neptune.view.ui.reading;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import androidx.core.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.utilitiles.DateUtil;
import com.cradle.neptune.view.ui.settings.SettingsActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.ZonedDateTime;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Objects;

import javax.inject.Inject;

import okhttp3.OkHttpClient;

import static com.cradle.neptune.view.LoginActivity.AUTH_PREF;
import static com.cradle.neptune.view.LoginActivity.USER_ID;


public class ReferralDialogFragment extends DialogFragment {

    public static final String TWILIO_PHONE_NUMBER = "16042298878";
    // Data Model
    @Inject
    Settings settings;


    // UI elements
    TextView tvSendingStatus;

    // Current state
    private Reading currentReading;
    private String enteredComment = "";
    private boolean isShowingMessagePreview = false;
    private DoneCallback callback;

    private String smsTextMessage = "";
    private String selectedHealthCentreSmsPhoneNumber = "";
    private String selectedHealthCentreName = "";

    // SMS elements
    private EditText mTo;
    private EditText mBody;
    private Button mSend;
    private OkHttpClient mClient = new OkHttpClient();
    private Context mContext;

    public static ReferralDialogFragment makeInstance(Reading currentReading, DoneCallback callback) {
        ReferralDialogFragment dialog = new ReferralDialogFragment();
        dialog.currentReading = currentReading;
        dialog.callback = callback;
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        // inject:
        ((MyApp) getActivity().getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // Get the layout inflater
        LayoutInflater inflater = requireActivity().getLayoutInflater();


        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View dialogView = inflater.inflate(R.layout.referral_dialog,null);
        builder.setView(dialogView)
                .setPositiveButton(R.string.send_text_message, null)
                .setNeutralButton(R.string.send_via_Web, null)
                .setNegativeButton(R.string.cancel, null);
        // Create the AlertDialog object and return it
        Dialog dialog = builder.create();

        // intercept OK button
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                Button btn1 = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);
                btn1.setOnClickListener(view-> sendReferralViaHttps(dialogView));
                // brians code
                button.setOnClickListener(view -> sendSMSMessage(dialog));
            }
        });

        // show it now so that we can call findViewById() w/o getting null
        dialog.show();

        setupCommentBox(dialog);
        setupHealthCentreSpinner(dialog);
        setupPreviewDropDown(dialog);
        setupStatusText(dialog);

        updateUI(dialog);

        return dialog;
    }

    private void sendReferralViaHttps(View dialogView) {
        Dialog dialog = new Dialog(getActivity());
        dialog.setTitle("Sending via HTTPS");
        dialog.setCancelable(false);
        dialog.show();
        RequestQueue queue = Volley.newRequestQueue(getActivity());
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(JsonObjectRequest.Method.POST, settings.getReferralsServerUrl(), getReferralJson(),
                response -> {
                    currentReading.setReferredToHealthCentre(selectedHealthCentreName, ZonedDateTime.now());
                    currentReading.referralComment = enteredComment;
                    dialog.cancel();
                    dismiss();
                }, error -> {
            String json = null;
            try {
                if (error.networkResponse != null) {
                    json = new String(error.networkResponse.data, HttpHeaderParser.parseCharset(error.networkResponse.headers));
                    Log.d("bugg", "referal error: " + json);
                    Toast.makeText(getActivity(), "json: " + json, Toast.LENGTH_LONG).show();
                    Log.d("bugg", "referal error: " + json);
                    Toast.makeText(getActivity(), "json: " + json, Toast.LENGTH_LONG).show();
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            Snackbar.make(dialogView,"Unable to Send the referral: "+json,Snackbar.LENGTH_LONG).show();
            dialog.cancel();
        });
        queue.add(jsonObjectRequest);
    }

    @Override
    public void onResume() {
        super.onResume();

        // update when returning from settings
        setupHealthCentreSpinner(getDialog());
        updateUI(getDialog());
    }

    private void composeMmsMessage(String message, String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("smsto:"));  // This ensures only SMS apps respond
        intent.putExtra("address", phoneNumber);
        intent.putExtra("sms_body", message);
        if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivity(intent);
        }


        Uri uri = Uri.parse("smsto:" + phoneNumber);
        Intent it = new Intent(Intent.ACTION_SENDTO, uri);
        it.putExtra("sms_body", message);
        startActivity(it);
    }

    private void sendSMSMessage(Dialog dialog) {
        Log.d("MySms", "sending message");

//        // source: https://mobiforge.com/design-development/sms-messaging-android
//
//        // check for data errors:
        if (settings.getHealthCentreNames().size() == 0) {
            tvSendingStatus.setText("ERROR: No known health centres.\nPlease go to settings to enter them.");
            tvSendingStatus.setVisibility(View.VISIBLE);
            // return;
        }
//
//        // Must send SMS via intent to default SMS program due to PlayStore policy preventing
//        // apps from sending SMS directly.
        composeMmsMessage(smsTextMessage, TWILIO_PHONE_NUMBER);
        onFinishedSendingSMS(dialog);
//
//        // Json for comments
//        ProgressDialog progressDialog = new ProgressDialog(getActivity());
//        progressDialog.setTitle("Uploading Referral");
//        progressDialog.setCancelable(false);
//        progressDialog.show();
//
//        RequestQueue queue = Volley.newRequestQueue(getActivity());
//        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(JsonObjectRequest.Method.POST, settings.getReferralsServerUrl(), getReferralJson(),
//                response -> {
//                    Log.d("bugg", "delivered " + response.toString() + "   server: " + settings.getReferralsServerUrl());
//                    progressDialog.cancel();
//                    Toast.makeText(getActivity(), "Referral sent to " + settings.getReferralsServerUrl(), Toast.LENGTH_LONG).show();
//                    onFinishedSendingSMS(dialog);
//                    dismiss();
//
//                }, error -> {
//            String json = null;
//            try {
//                if (error.networkResponse != null) {
//                    json = new String(error.networkResponse.data, HttpHeaderParser.parseCharset(error.networkResponse.headers));
//                }
//                progressDialog.cancel();
//                Log.d("bugg", "referal error: " + json);
//                Toast.makeText(getActivity(), "json: " + json, Toast.LENGTH_LONG).show();
//            } catch (UnsupportedEncodingException e) {
//                e.printStackTrace();
//            }
//            Log.d("bugg", "Delivery error: " + json);
//
//        });
//        queue.add(jsonObjectRequest);

//        // prep UI
//        // TODO: put in XML
//        tvSendingStatus.setText("Sending referral.\nPlease wait...");
//        tvSendingStatus.setVisibility(View.VISIBLE);
//
//        String SENT = "SMS_SENT";
//        String DELIVERED = "SMS_DELIVERED";
//
//        // callback on SMS sent
//        getActivity().registerReceiver(new BroadcastReceiver(){
//            @Override
//            public void onReceive(Context arg0, Intent arg1) {
//                int resultCode = getResultCode();
//                onReceiveSMSSentCallback(dialog, resultCode);
//            }
//        }, new IntentFilter(SENT));
//
//        // checking for delivery complicates state machine and life cycles; ignore for now.
////        // callback on SMS delivered
////        Context longLivedContext = getActivity();
////        getActivity().registerReceiver(new BroadcastReceiver(){
////            @Override
////            public void onReceive(Context arg0, Intent arg1) {
////                String message = null;
////                switch (getResultCode())
////                {
////                    case Activity.RESULT_OK:
////                        message = "Referral SMS delivered";
////                        break;
////                    case Activity.RESULT_CANCELED:
////                        message = "Referral SMS *not* delivered";
////                        break;
////                }
////                // may happen after dismissed
////                Toast.makeText(longLivedContext, message, Toast.LENGTH_SHORT).show();
////            }
////        }, new IntentFilter(DELIVERED));
//
//        // make it a multi-part message; setup callback on last part
//        SmsManager sms = SmsManager.getDefault();
//        ArrayList<String> parts = sms.divideMessage(smsTextMessage);
//        ArrayList<PendingIntent> sentPIs = new ArrayList<>();
//        ArrayList<PendingIntent> deliveredPIs = new ArrayList<>();
//        for (int i = 0; i < parts.size(); i++) {
//            if (i == parts.size() - 1) {
//                sentPIs.add(
//                        PendingIntent.getBroadcast(getContext(), 0, new Intent(SENT), 0));
//                deliveredPIs.add(
//                        PendingIntent.getBroadcast(getContext(), 0, new Intent(DELIVERED), 0));
//            } else {
//                sentPIs.add(null);
//                deliveredPIs.add(null);
//            }
//        }
//
//        // send
//        sms.sendMultipartTextMessage(selectedHealthCentreSmsPhoneNumber, null, parts, sentPIs, deliveredPIs);
    }

    private void onFinishedSendingSMS(Dialog dialog) {
        currentReading.setReferredToHealthCentre(selectedHealthCentreName, ZonedDateTime.now());
        currentReading.referralComment = enteredComment;
        callback.sentTextMessage(smsTextMessage);
        dialog.dismiss();
    }

//    private String buildSMSMessage()
//    {
//        String message = currentReading.getReferralString();
//        message += "\n\nVHT Comment: ";
//        message += enteredComment;
//
//        return message;
//    }

//    Call post(String url, Callback callback) throws IOException {
//        RequestBody formBody = new FormBody.Builder()
//                .add("To", selectedHealthCentreSmsPhoneNumber)
//                .add("Body", buildSMSMessage())
//
//                .build();
//        Request request = new Request.Builder()
//                .url(url)
//                .post(formBody)
//                .build();
//        Call response = mClient.newCall(request);
//        response.enqueue(callback);
//        return response;
//    }

//    private void onReceiveSMSSentCallback(Dialog dialog, int resultCode) {
//        String message = null;
//        switch (resultCode)
//        {
//            case Activity.RESULT_OK:
//                message = "Referral SMS sent successfully";
//                break;
//            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
//                message = "Generic failure";
//                break;
//            case SmsManager.RESULT_ERROR_NO_SERVICE:
//                message = "No service";
//                break;
//            case SmsManager.RESULT_ERROR_NULL_PDU:
//                message = "Null PDU (protocol data unit)";
//                break;
//            case SmsManager.RESULT_ERROR_RADIO_OFF:
//                message = "Radio is off";
//                break;
//        }
//
//        if (message != null) {
//            // finish (success) or display error
//            if (resultCode == Activity.RESULT_OK) {
//                // getContext() will work because dialog not dismissed until after this is done
//                Toast.makeText(dialog.getContext(), message, Toast.LENGTH_SHORT).show();
//
//                onFinishedSendingSMS(dialog);
//            } else {
//                // TODO: put in XML
//                message = "Error sending referral\n(" + message + ")";
//                tvSendingStatus.setText(message);
//            }
//        }
//    }

    /**
     * Setup UI
     */
    private void setupCommentBox(Dialog dialog) {
        EditText et = dialog.findViewById(R.id.etReferralComments);
        if (currentReading.referralComment != null) {
            et.setText(currentReading.referralComment);
        }
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                enteredComment = charSequence.toString();
                updateUI(dialog);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void setupHealthCentreSpinner(Dialog dialog) {
        Spinner sp = dialog.findViewById(R.id.spinnerHealthCentre);
        ArrayList<String> options = new ArrayList<>();
        options.addAll(settings.getHealthCentreNames());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getActivity(),
                android.R.layout.simple_spinner_item,
                options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);

        // selection
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                // look up new name and number
                Spinner spin = dialog.findViewById(R.id.spinnerHealthCentre);
                int position = spin.getSelectedItemPosition();
                if (position >= 0) {
                    selectedHealthCentreName = spin.getSelectedItem().toString();
                    selectedHealthCentreSmsPhoneNumber = settings.getHealthCentrePhoneNumber(position);
                }

//                adapter.notifyDataSetChanged();
                updateUI(dialog);
                settings.setLastHealthCentreSelectionIdx(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                updateUI(dialog);
            }
        });
        sp.setSelection(settings.getLastHealthCentreSelectionIdx());

        // settings button
        ImageView iv = dialog.findViewById(R.id.ivSettings);
        iv.setOnClickListener(view -> {
            Intent intent = SettingsActivity.makeLaunchIntent(getActivity());
            getActivity().startActivity(intent);
        });
    }

    private void setupPreviewDropDown(Dialog dialog) {
        TextView tv = dialog.findViewById(R.id.txPreviewMessageHeader);
        tv.setOnClickListener(view -> toggleMessagePreview(dialog));
        ImageView iv = dialog.findViewById(R.id.ivPreviewMessageDropDown);
        iv.setOnClickListener(view -> toggleMessagePreview(dialog));

        // update display (to inverse of this default; it toggles!)
        isShowingMessagePreview = true;
        toggleMessagePreview(dialog);
    }

    private void toggleMessagePreview(Dialog dialog) {
        ImageView iv = dialog.findViewById(R.id.ivPreviewMessageDropDown);
        TextView tv = dialog.findViewById(R.id.txtMessagePreview);
        isShowingMessagePreview = !isShowingMessagePreview;

        if (isShowingMessagePreview) {
            iv.setImageResource(R.drawable.baseline_arrow_drop_up_black_36);
            tv.setVisibility(View.VISIBLE);
        } else {
            iv.setImageResource(R.drawable.baseline_arrow_drop_down_black_36);
            tv.setVisibility(View.GONE);
        }
    }

    private void setupStatusText(Dialog dialog) {
        tvSendingStatus = dialog.findViewById(R.id.tvError);
        tvSendingStatus.setVisibility(View.GONE);
    }

    /**
     * Update UI
     */
    private void updateUI(Dialog dialog) {
        // Status message
        TextView tv = dialog.findViewById(R.id.txtReferralMessageSentStatus);
        if (currentReading.isReferredToHealthCentre()) {
            tv.setText(getString(
                    R.string.reading_referral_sent,
                    currentReading.referralHealthCentre,
                    DateUtil.getFullDateString(currentReading.referralMessageSendTime)
            ));
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }

        // hide error
        tvSendingStatus.setVisibility(View.GONE);

        // preview
        smsTextMessage = buildMessage(dialog);
        TextView tvPreview = dialog.findViewById(R.id.txtMessagePreview);
        tvPreview.setText(smsTextMessage);

    }

    private String buildMessage(Dialog dialog) {

//        // patient
//        String patient = getString(R.string.sms_message_patient,
//                currentReading.patient.patientName,
//                currentReading.patient.patientId
//        );
//        Reading.WeeksAndDays ga = currentReading.getGestationalAgeInWeeksAndDays();
//        String patientAge;
//        if (ga != null) {
//            patientAge = getString(R.string.sms_message_patient_age_pregnant,
//                    currentReading.patient.ageYears,
//                    ga.weeks,
//                    ga.days);
//        } else {
//            patientAge = getString(R.string.sms_message_patient_age_not_pregnant,
//                    currentReading.patient.ageYears);
//        }
//
//        // vitals
//        ReadingAnalysis analysis = ReadingAnalysis.analyze(currentReading);
//        String vitals = getString(R.string.sms_message_reading_results,
//                currentReading.bpSystolic,
//                currentReading.bpDiastolic,
//                currentReading.heartRateBPM,
//                analysis.getAnalysisText(getContext())
//        );
//
//        // health centre: updated when selection changes
//
//        // warn recipient if possible duplicate referral
//        String secondReferralWarning = "";
//        if (currentReading.isReferredToHealthCentre()) {
//            secondReferralWarning =
//                    getString(R.string.sms_message_repeat_referral_warning,
//                            currentReading.referralHealthCentre,
//                            DateUtil.getFullDateString(currentReading.referralMessageSendTime));
//        }
//
//        // comments
//        String comments = enteredComment;
//        if (comments.length() > 0) {
//            comments = "\"" + comments + "\"";
//        }
//
//        // construct it
//        String message = addLf(getString(R.string.sms_message_header))
//        + addLf(patient)
//                + addLf(patientAge)
//                + addLf(vitals)
//                + addLf(currentReading.getSymptomsString())
//                + addLf(getString(R.string.sms_message_reading_date,
//                DateUtil.getFullDateString(currentReading.dateTimeTaken)))
//                + addLf(getString(R.string.sms_message_referral,
//                selectedHealthCentreName))
//                + addLf(getString(R.string.sms_message_by_vht, settings.getVhtName()))
//                + addLf(secondReferralWarning)
//                + comments;

        JSONObject referralJson = getReferralJson();

        String jsonStr = referralJson.toString();
        return jsonStr;
    }

    private JSONObject getReferralJson() {
        JSONObject patientVal = currentReading.patient.getPatientInfoJSon();
        SharedPreferences sharedPreferences = Objects.requireNonNull(getActivity())
                .getSharedPreferences(AUTH_PREF,Context.MODE_PRIVATE);

        JSONObject readingVal = new JSONObject();
        try {
            readingVal.put("readingId", currentReading.serverReadingId);
            readingVal.put(USER_ID,sharedPreferences.getString(USER_ID,""));
            readingVal.put("dateLastSaved", currentReading.dateLastSaved);
            readingVal.put("bpSystolic", currentReading.bpSystolic);
            readingVal.put("bpDiastolic", currentReading.bpDiastolic);
            readingVal.put("heartRateBPM", currentReading.heartRateBPM);
            readingVal.put("dateRecheckVitalsNeeded", currentReading.dateRecheckVitalsNeeded);
            readingVal.put("isFlaggedForFollowup", currentReading.isFlaggedForFollowup());
            readingVal.put("symptoms", currentReading.getSymptomsString());
            readingVal.put("dateTimeTaken", currentReading.dateTimeTaken);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        JSONObject mainObj = new JSONObject();
        try {

            mainObj.put("patient", patientVal);
            mainObj.put("reading", readingVal);
            mainObj.put("comment", enteredComment);
            mainObj.put("healthFacilityName", this.selectedHealthCentreName);
            mainObj.put("date", ZonedDateTime.now().toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return mainObj;

    }

    private String addLf(String str) {
        if (str.trim().length() > 0) {
            return str + "\n";
        } else {
            return str;
        }
    }

    public interface DoneCallback {
        void sentTextMessage(String message);
    }
}
