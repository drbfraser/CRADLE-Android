package com.cradle.neptune.view;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import com.cradle.neptune.R;
import com.github.barteksc.pdfviewer.PDFView;
import com.shockwave.pdfium.PdfDocument;

public class PdfViewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_view);
        setupPDFview();
    }

    private void setupPDFview() {
        PDFView pdfDocument = findViewById(R.id.pdfView);
            pdfDocument.fromAsset("education_poster.pdf").enableAntialiasing(true).spacing(4).load();

    }
}
