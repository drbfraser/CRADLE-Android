package com.cradle.neptune.view

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.cradle.neptune.R
import com.cradle.neptune.view.VideoActivity

class VideoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        val toolbar =
            findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        // setup UI components
        // setupBottomBarNavigation();
        setupHelpVideo()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupHelpVideo() {
        val uri =
            Uri.parse("android.resource://" + packageName + "/" + R.raw.cradle_vsa_tutorial_for_health_care)
        val videoView = findViewById<VideoView>(R.id.videoView)

        // set preview image
        // gives the image; however, then does not show the video!
//        videoView.setBackgroundResource(R.drawable.cradle_video_frame);
        val START_LOCATION_ms = 9000
        videoView.setMediaController(MediaController(this@VideoActivity))
        videoView.setVideoURI(uri)
        videoView.requestFocus()
        videoView.seekTo(START_LOCATION_ms)
        videoView.setOnCompletionListener {
            videoView.seekTo(
                START_LOCATION_ms
            )
        }
    }

    companion object {
        fun makeIntent(context: Context?): Intent {
            return Intent(context, VideoActivity::class.java)
        }
    }
}