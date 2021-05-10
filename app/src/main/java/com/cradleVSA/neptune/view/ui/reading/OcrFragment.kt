package com.cradleVSA.neptune.view.ui.reading

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.databinding.FragmentOcrBinding
import com.cradleVSA.neptune.ocr.OcrAnalyzer
import com.cradleVSA.neptune.view.ReadingActivity
import com.cradleVSA.neptune.viewmodel.OcrFragmentViewModel
import com.cradleVSA.neptune.viewmodel.PatientReadingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val CONFIRMATION_DELAY_MILLIS = 1500L

/**
 * A Fragment that does OCR on the CRADLE VSA device to extract the readings into Strings of numbers
 * the app can process.
 *
 * TODO: Look into using androidx.camera.view.PreviewView again but with the 3 views that the
 *  user aligns.
 * TODO: Add back debug info that shows the score for each digit using BorderedText, etc.
 */
@AndroidEntryPoint
class OcrFragment : Fragment() {
    /**
     * ViewModel is scoped to the [ReadingActivity] that this Fragment is attached to; therefore,
     * this is shared by all Fragments.
     */
    private val viewModel: PatientReadingViewModel by activityViewModels()

    /** A ViewModel for OCR-specific info / state **/
    private val ocrViewModel: OcrFragmentViewModel by viewModels()

    // TODO: Look into declaring executors for the whole app and use it from there?
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var binding: FragmentOcrBinding? = null

    private var analyzer: OcrAnalyzer? = null

    private var cameraProvider: ProcessCameraProvider? = null

    private var camera: Camera? = null

    private val isConfirming: Boolean
        get() = binding?.useOcrResultsButton?.isEnabled == false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOcrBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toggleConfirmationState(false)

        ocrViewModel.ocrResult.observe(viewLifecycleOwner) {
            binding?.apply {
                useOcrResultsButton.isEnabled = it != null
                systolicOcrTextView.text = it?.systolic
                    ?: context?.getString(R.string.not_available_n_slash_a) ?: ""
                diastolicOcrTextView.text = it?.diastolic
                    ?: context?.getString(R.string.not_available_n_slash_a) ?: ""
                heartRateOcrTextView.text = it?.heartRate
                    ?: context?.getString(R.string.not_available_n_slash_a) ?: ""
            }
        }

        viewModel.apply {
            bloodPressureSystolicInput.observe(viewLifecycleOwner) {}
            bloodPressureDiastolicInput.observe(viewLifecycleOwner) {}
            bloodPressureHeartRateInput.observe(viewLifecycleOwner) {}
            bloodPressure.observe(viewLifecycleOwner) {}
        }

        binding?.apply {
            useOcrResultsButton.setOnClickListener {
                toggleConfirmationState(true)
            }

            noButton.setOnClickListener {
                toggleConfirmationState(false)
            }

            yesResultsCorrectButton.setOnClickListener {
                val currentOcrResult = ocrViewModel.ocrResult.value ?: return@setOnClickListener
                findNavController().popBackStack()
                viewModel.apply {
                    viewModelScope.launch {
                        // FIXME: Timing issues. If we don't delay, something from the
                        //  VitalSignsFragment will ignore these new inputs.
                        @Suppress("MagicNumber")
                        delay(250L)

                        bloodPressureSystolicInput.value = currentOcrResult.systolic.toIntOrNull()
                        bloodPressureDiastolicInput.value = currentOcrResult.diastolic.toIntOrNull()
                        bloodPressureHeartRateInput.value = currentOcrResult.heartRate.toIntOrNull()
                    }
                }
            }

            flashlightButton.setOnClickListener {
                camera?.apply {
                    if (!cameraInfo.hasFlashUnit()) return@setOnClickListener
                    val enableTorch = cameraInfo.torchState.value != TorchState.ON
                    cameraControl.enableTorch(enableTorch)
                    ocrViewModel.useFlashlight.value = enableTorch
                }
            }
        }
    }

    /**
     * Toggles between showing the use button and the yes and no confirmation buttons.
     * Function handles releasing the camera and stopping the analysis if [isConfirming] is true,
     * and reinitializing the camera if [isConfirming] false.
     */
    private fun toggleConfirmationState(isConfirming: Boolean) {
        binding?.apply {
            if (isConfirming) {
                stopCameraAndAnalysis()

                flashlightButton.isEnabled = false
                // Show the yes and no buttons; enable them after a delay.
                useOcrResultsButton.apply {
                    isVisible = false
                    isEnabled = false
                }
                confirmationTextView.isVisible = true
                yesResultsCorrectButton.apply {
                    isEnabled = false
                    isVisible = true
                }
                noButton.apply {
                    isEnabled = false
                    isVisible = true
                }
                lifecycleScope.launch {
                    delay(CONFIRMATION_DELAY_MILLIS)
                    yesResultsCorrectButton.isEnabled = true
                    noButton.isEnabled = true
                }
            } else {
                // Clear out the previous result
                ocrViewModel.ocrResult.value = null

                flashlightButton.isEnabled = true
                useOcrResultsButton.isVisible = true
                confirmationTextView.isVisible = false
                yesResultsCorrectButton.apply {
                    isEnabled = false
                    isVisible = false
                }
                noButton.apply {
                    isEnabled = false
                    isVisible = false
                }

                initCamera()
            }
        }
    }

    private fun stopCameraAndAnalysis() {
        analyzer?.doAnalysis = false
        analyzer?.close()
        analyzer = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onPause() {
        super.onPause()
        stopCameraAndAnalysis()
    }

    override fun onResume() {
        super.onResume()
        if (!isConfirming) {
            initCamera()
        }
    }

    private fun showCradleReadingsCameraCapture(
        systolicImage: Bitmap,
        diastolicImage: Bitmap,
        heartrateImage: Bitmap
    ) {
        activity?.runOnUiThread {
            binding?.apply {
                ocrProgressBar.isVisible = false
                systolicImageView.setImageBitmap(systolicImage)
                diastolicImageView.setImageBitmap(diastolicImage)
                heartRateImageView.setImageBitmap(heartrateImage)
            }
        }
    }

    private fun initCamera() {
        val thisContext = context ?: return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(thisContext)

        analyzer = OcrAnalyzer(
            thisContext,
            { ocrResult -> ocrViewModel.ocrResult.postValue(ocrResult) },
            ::showCradleReadingsCameraCapture
        )

        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                val imageAnalysis = analyzer?.let {
                    ImageAnalysis.Builder()
                        .setTargetRotation(Surface.ROTATION_0)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply { setAnalyzer(analysisExecutor, it) }
                }

                camera = cameraProvider?.run {
                    unbindAll()
                    if (imageAnalysis != null) {
                        bindToLifecycle(viewLifecycleOwner, cameraSelector, imageAnalysis)
                    } else {
                        bindToLifecycle(viewLifecycleOwner, cameraSelector)
                    }
                }.also {
                    it ?: return@also
                    binding?.flashlightButton?.isEnabled = it.cameraInfo.hasFlashUnit()
                    if (ocrViewModel.useFlashlight.value == true) {
                        it.cameraControl.enableTorch(true)
                    }
                }
            },
            ContextCompat.getMainExecutor(thisContext)
        )
    }
}
