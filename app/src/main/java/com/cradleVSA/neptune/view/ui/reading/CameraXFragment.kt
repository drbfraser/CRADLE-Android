package com.cradleVSA.neptune.view.ui.reading

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.cradle.neptune.viewmodel.OcrFragmentViewModel
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

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var binding: FragmentCameraNewBinding? = null

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
            it ?: return@observe

            binding?.apply {
                systolicOcrResultTextView.text = it.systolic
                diastolicOcrResultTextView.text = it.diastolic
                heartRateOcrResultTextView.text = it.heartRate
            }
        }

        viewModel.apply {
            bloodPressureSystolicInput.observe(viewLifecycleOwner) {}
            bloodPressureDiastolicInput.observe(viewLifecycleOwner) {}
            bloodPressureHeartRateInput.observe(viewLifecycleOwner) {}
            bloodPressure.observe(viewLifecycleOwner) {}
        }

        binding?.cameraCaptureButton?.setOnClickListener {
            val currentOcrResult = ocrViewModel.ocrResult.value ?: return@setOnClickListener
            findNavController().popBackStack()

            Toast.makeText(requireContext(), "Using OcrResult: $currentOcrResult. To int, they are ${currentOcrResult.systolic.toIntOrNull()}", Toast.LENGTH_SHORT).show()

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initCamera()
    }

    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        val analyzer = OcrAnalyzer(
            requireContext(),
            { ocrResult -> ocrViewModel.ocrResult.postValue(ocrResult) },
            { bitmap1, bitmap2, bitmap3 ->
                activity?.runOnUiThread {
                    binding?.apply {
                        //debugImageView.setImageBitmap(bitmap1)
                        //debugImageView2.setImageBitmap(bitmap2)
                        //debugImageView3.setImageBitmap(bitmap3)
                    }
                }
            },
            { string ->
                activity?.runOnUiThread {
                    Log.d(TAG, string)
                }
            },
        )

        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                val preview = Preview.Builder().build()
                    .apply { setSurfaceProvider(binding?.previewView?.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(analysisExecutor, analyzer)

                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    imageAnalysis,
                    preview
                )
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }
}
