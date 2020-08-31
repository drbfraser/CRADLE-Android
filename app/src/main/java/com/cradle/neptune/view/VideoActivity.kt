package com.cradle.neptune.view

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.cradle.neptune.R

class VideoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "CRADLE VSA Instructions"
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
        videoView.setMediaController(MediaController(this@VideoActivity))
        videoView.setVideoURI(uri)
        videoView.requestFocus()
        videoView.seekTo(START_LOCATION_MS)
        videoView.setOnCompletionListener {
            videoView.seekTo(
                START_LOCATION_MS
            )
        }
    }

    companion object {
        fun makeIntent(context: Context?): Intent {
            return Intent(context, VideoActivity::class.java)
        }
        const val START_LOCATION_MS = 9000
    }
}
