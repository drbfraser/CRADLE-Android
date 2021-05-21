package com.cradleplatform.neptune.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.cradleplatform.neptune.R

class EducationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_education)
        setupOnCLickMethods()
        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = getString(R.string.education_activity_title)
        }
    }

    private fun setupOnCLickMethods() {
        val communityPosterCard = findViewById<CardView>(R.id.communityPosterView)
        communityPosterCard.setOnClickListener {
            val intent = PosterActivity.makeIntent(
                this@EducationActivity,
                R.drawable.educational_community_poster
            )
            startActivity(intent)
        }
        val videoCard = findViewById<CardView>(R.id.videoCardview)
        videoCard.setOnClickListener {
            startActivity(VideoActivity.makeIntent(this@EducationActivity))
        }
        val clinicPosterCard = findViewById<CardView>(R.id.clinicPosterView)
        clinicPosterCard.setOnClickListener {
            val intent = PosterActivity.makeIntent(
                this@EducationActivity,
                R.drawable.educational_clinic_poster
            )
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
