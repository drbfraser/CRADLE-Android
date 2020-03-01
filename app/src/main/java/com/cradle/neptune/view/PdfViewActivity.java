package com.cradle.neptune.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cradle.neptune.R;
import com.github.barteksc.pdfviewer.PDFView;

public class PdfViewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_view);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Educational Poster");
        }
        Intent intent = getIntent();
        if (intent != null) {
            String filename = intent.getStringExtra("poster");
            setupPDFview(filename);
        }
    }

    private void setupPDFview(String filename) {
        PDFView pdfDocument = findViewById(R.id.pdfView);
        pdfDocument.fromAsset(filename).enableAntialiasing(true).spacing(8).load();

    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }
}
