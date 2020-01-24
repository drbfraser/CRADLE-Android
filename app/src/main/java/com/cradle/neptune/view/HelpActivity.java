package com.cradle.neptune.view;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.cradle.neptune.R;

public class HelpActivity extends AppCompatActivity {

    public HelpActivity() {
        super();
    }

    public static Intent makeIntent(Context context) {
        return new Intent(context, HelpActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        // setup UI components
        // setupBottomBarNavigation();
        setupHelpVideo();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setupHelpVideo() {
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.cradle_vsa_tutorial_for_health_care);

        VideoView videoView = findViewById(R.id.videoView);

        // set preview image
        // gives the image; however, then does not show the video!
//        videoView.setBackgroundResource(R.drawable.cradle_video_frame);

        final int START_LOCATION_ms = 9000;
        videoView.setMediaController(new MediaController(HelpActivity.this));
        videoView.setVideoURI(uri);
        videoView.requestFocus();
        videoView.seekTo(START_LOCATION_ms);

        videoView.setOnCompletionListener(mediaPlayer -> {
            videoView.seekTo(START_LOCATION_ms);
        });
    }

}
