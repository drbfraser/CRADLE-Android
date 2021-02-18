package com.cradleVSA.neptune.view.ui.reading

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
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.cradle.neptune.viewmodel.OcrFragmentViewModel
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.databinding.FragmentCameraNewBinding
import com.cradleVSA.neptune.ocr.OcrAnalyzer
import com.cradleVSA.neptune.view.ReadingActivity
import com.cradleVSA.neptune.viewmodel.PatientReadingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "CameraXFragment"

@AndroidEntryPoint
class CameraXFragment : Fragment() {
    /**
     * ViewModel is scoped to the [ReadingActivity] that this Fragment is attached to; therefore,
     * this is shared by all Fragments.
     */
    private val viewModel: PatientReadingViewModel by activityViewModels()

    private val ocrViewModel: OcrFragmentViewModel by viewModels()

    // TODO: Look into declaring executors for the whole app and use it from there?
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var binding: FragmentCameraNewBinding? = null

    private var analyzer: OcrAnalyzer? = null

    private var cameraProvider: ProcessCameraProvider? = null

    private var camera: Camera? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraNewBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ocrViewModel.ocrResult.observe(viewLifecycleOwner) {
            binding?.apply {
                useOcrResultsButton.isEnabled = it != null

                val notAvailable = if (it == null) {
                    context?.getString(R.string.not_available_n_slash_a) ?: ""
                } else {
                    ""
                }

                systolicOcrTextView.text = it?.systolic ?: notAvailable
                diastolicOcrTextView.text = it?.diastolic ?: notAvailable
                heartRateOcrTextView.text = it?.heartRate ?: notAvailable
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
                    cameraControl.enableTorch(cameraInfo.torchState.value != TorchState.ON)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onPause() {
        super.onPause()
        cameraProvider?.unbindAll()
        analyzer?.close()
    }

    override fun onResume() {
        super.onResume()
        initCamera()
    }

    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        analyzer = OcrAnalyzer(
            requireContext(),
            { ocrResult -> ocrViewModel.ocrResult.postValue(ocrResult) },
            { systolicBitmap, diastolicBitmap, heartrateBitmap ->
                activity?.runOnUiThread {
                    binding?.apply {
                        ocrProgressBar.isVisible = false
                        systolicImageView.setImageBitmap(systolicBitmap)
                        diastolicImageView.setImageBitmap(diastolicBitmap)
                        heartRateImageView.setImageBitmap(heartrateBitmap)
                    }
                }
            }
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
                }
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }
}
