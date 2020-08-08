package com.cradle.neptune.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cradle.neptune.R
import com.github.barteksc.pdfviewer.PDFView

class PdfViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_view)
        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Educational Poster"
        }
        intent?.getStringExtra("poster")?.also {
            setupPdfView(it)
        }
    }

    private fun setupPdfView(filename: String) {
        val pdfDocument = findViewById<PDFView>(R.id.pdfView)
        pdfDocument.fromAsset(filename).enableAntialiasing(true).spacing(8).load()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }
}