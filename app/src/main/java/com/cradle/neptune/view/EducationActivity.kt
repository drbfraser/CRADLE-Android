package com.cradle.neptune.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.cradle.neptune.R

class EducationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_education)
        setupOnCLickMethods()
        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Help"
        }
    }

    private fun setupOnCLickMethods() {
        val postercard = findViewById<CardView>(R.id.communityPosterView)
        postercard.setOnClickListener {
            val intent = Intent(this@EducationActivity, PdfViewActivity::class.java)
            intent.putExtra("poster", "education_community_poster.pdf")
            startActivity(intent)
        }
        val videoCard = findViewById<CardView>(R.id.videoCardview)
        videoCard.setOnClickListener {
            startActivity(
                HelpActivity.makeIntent(this@EducationActivity)
            )
        }
        val clinicPoster = findViewById<CardView>(R.id.clinicPosterView)
        clinicPoster.setOnClickListener {
            val intent = Intent(this@EducationActivity, PdfViewActivity::class.java)
            intent.putExtra("poster", "education_clinic_poster.pdf")
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}