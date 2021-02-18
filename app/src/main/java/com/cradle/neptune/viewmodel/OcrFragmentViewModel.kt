package com.cradle.neptune.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.cradleVSA.neptune.ocr.OcrResult

class OcrFragmentViewModel : ViewModel() {
    val ocrResult = MutableLiveData<OcrResult?>(null)
}
