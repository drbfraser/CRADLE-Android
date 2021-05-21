package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.ocr.OcrResult

class OcrFragmentViewModel : ViewModel() {
    val ocrResult = MutableLiveData<OcrResult?>(null)

    /** Whether to use the flashlight. Used for saving flashlight state after confirmation step */
    val useFlashlight = MutableLiveData(false)
}
