package com.cradleplatform.neptune.activities.education

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.R
import com.ortiz.touchview.TouchImageView

/**
 * We are using PDFs that are converted into PNG images appended on top of each other.
 * There doesn't seem to be any *minimal* PDF library that supports our very low minSdkVersion of
 * 17, so a TouchImageView is what we use. TouchImageView has pinch-and-zoom support and a small
 * footprint on the app size. This works well for PDFs with a small amount of pages like 4 or 5.
 *
 * To setup a PDF to be displayed here, just convert it to an image, and then compress it.
 *
 * Run this script to convert each page of a PDF into individual images:
 *
 *     #!/bin/bash
 *     echo $2
 *     for i in {0..8}
 *     do
 *         convert -alpha remove "$1[$i]" "${1%.*}-$((i+1)).png"
 *     done
 *
 * Usage: ./script.sh pdf-name.pdf (might have to change the number in the loop to # of pages)
 *
 * Then append them vertically:
 *
 *     convert someimage-1.png someimage-2.png -append result-image.png
 *
 * Compress the resulting image. e.g., with pngquant on Fedora / CentOS
 * (https://computingforgeeks.com/compress-png-images-on-linux-command-line/):
 *
 *     sudo dnf -y install git libpng-devel gcc cmake
 *     git clone --recursive https://github.com/kornelski/pngquant.git
 *     cd pngquant/
 *     ./configure
 *     sudo make install
 *     pngquant
 *
 * then compress it:
 *
 *     pngquant --quality=55-100 result-image.png
 *
 */
class PosterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        check(DEFAULT_ZOOM_RATIO in 0f..MAX_ZOOM_RATIO)
        check(intent?.hasExtra(EXTRA_POSTER_RES_ID) == true)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_view)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        intent.getIntExtra(EXTRA_POSTER_RES_ID, 0).let { drawableResId ->
            supportActionBar?.title = when (drawableResId) {
                R.drawable.educational_clinic_poster ->
                    getString(R.string.activity_poster_view_clinic_poster_title)
                R.drawable.educational_community_poster ->
                    getString(R.string.activity_poster_view_community_poster_title)
                else -> error("invalid poster ID; maybe the title isn't setup?")
            }
            setupPdfView(drawableResId)
        }
    }

    private fun setupPdfView(@DrawableRes imageId: Int) {
        findViewById<TouchImageView>(R.id.pdf_view_touch_image_view).apply {
            setImageResource(imageId)
            setMaxZoomRatio(MAX_ZOOM_RATIO)
            setZoom(scale = DEFAULT_ZOOM_RATIO, focusX = 0f, focusY = 0f)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    companion object {
        private const val EXTRA_POSTER_RES_ID = "poster"
        private const val MAX_ZOOM_RATIO = 6.0f
        private const val DEFAULT_ZOOM_RATIO = 1.8f

        fun makeIntent(context: Context, @DrawableRes drawableResId: Int) =
            Intent(context, PosterActivity::class.java)
                .putExtra(EXTRA_POSTER_RES_ID, drawableResId)
    }
}
