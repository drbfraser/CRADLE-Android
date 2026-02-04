package com.cradleplatform.neptune.activities.education

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.viewmodel.VideoViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VideoActivity : AppCompatActivity() {
    private val viewModel: VideoViewModel by viewModels()
    private var videoView: VideoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = getString(R.string.video_activity_title)
        }
        // setup UI components
        // setupBottomBarNavigation();
        setupHelpVideo()
    }

    override fun onPause() {
        super.onPause()
        // Save the current video position before configuration change or pause
        videoView?.let {
            if (it.isPlaying) {
                viewModel.saveVideoPosition(it.currentPosition)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupHelpVideo() {
        val uri =
            Uri.parse("android.resource://" + packageName + "/" + R.raw.cradle_vsa_tutorial_for_health_care)
        videoView = findViewById<VideoView>(R.id.videoView)

        videoView?.apply {
            // set preview image
            // gives the image; however, then does not show the video!
//        videoView.setBackgroundResource(R.drawable.cradle_video_frame);
            setMediaController(MediaController(this@VideoActivity))
            setVideoURI(uri)
            requestFocus()

            // Restore saved position or use default start location
            val savedPosition = viewModel.getVideoPosition()
            val startPosition = if (savedPosition > 0) savedPosition else START_LOCATION_MS

            setOnPreparedListener {
                seekTo(startPosition)
            }

            setOnCompletionListener {
                seekTo(START_LOCATION_MS)
                viewModel.saveVideoPosition(START_LOCATION_MS)
            }
        }
    }

    companion object {
        fun makeIntent(context: Context?): Intent {
            return Intent(context, VideoActivity::class.java)
        }
        const val START_LOCATION_MS = 9000
    }
}
