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
        // Save the current video position and playing state before configuration change or pause
        videoView?.let {
            viewModel.saveVideoPosition(it.currentPosition)
            viewModel.savePlayingState(it.isPlaying)
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
            // Hide the video view initially to prevent showing loading indicators
            alpha = 0f

            // set preview image
            // gives the image; however, then does not show the video!
//        videoView.setBackgroundResource(R.drawable.cradle_video_frame);
            setMediaController(MediaController(this@VideoActivity))
            setVideoURI(uri)
            requestFocus()

            // Restore saved position or use default start location
            val savedPosition = viewModel.getVideoPosition()
            val wasPlaying = viewModel.wasPlaying()
            val startPosition = if (savedPosition > 0) savedPosition else START_LOCATION_MS

            setOnPreparedListener { mediaPlayer ->
                // Adjust video dimensions to maintain aspect ratio
                val videoWidth = mediaPlayer.videoWidth
                val videoHeight = mediaPlayer.videoHeight

                if (videoWidth > 0 && videoHeight > 0) {
                    val videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels

                    // Calculate the proper dimensions
                    val viewWidth = screenWidth - dpToPx(16) // Account for margins
                    val viewHeight = (viewWidth / videoAspectRatio).toInt()

                    // Update layout params
                    layoutParams?.apply {
                        width = viewWidth
                        height = viewHeight
                    }
                    requestLayout()
                }

                seekTo(startPosition)
                // Show the video view with a smooth fade-in
                animate().alpha(1f).setDuration(200).start()
                // Resume playing if it was playing before rotation
                if (wasPlaying) {
                    start()
                }
            }

            setOnCompletionListener {
                seekTo(START_LOCATION_MS)
                viewModel.saveVideoPosition(START_LOCATION_MS)
                viewModel.savePlayingState(false)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        fun makeIntent(context: Context?): Intent {
            return Intent(context, VideoActivity::class.java)
        }
        const val START_LOCATION_MS = 9000
    }
}
