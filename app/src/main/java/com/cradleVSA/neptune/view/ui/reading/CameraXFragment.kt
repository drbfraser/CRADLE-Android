package com.cradleVSA.neptune.view.ui.reading

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.cradleVSA.neptune.databinding.FragmentCameraNewBinding
import com.cradleVSA.neptune.ocr.OcrAnalyzer
import com.cradleVSA.neptune.view.ReadingActivity
import com.cradleVSA.neptune.viewmodel.PatientReadingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

private const val TAG = "CameraXFragment"

class CameraXFragment : Fragment() {
    /**
     * ViewModel is scoped to the [ReadingActivity] that this Fragment is attached to; therefore,
     * this is shared by all Fragments.
     */
    private val viewModel: PatientReadingViewModel by activityViewModels()

    private var _binding: FragmentCameraNewBinding? = null
    // valid iff fragment lifecycle between onCreateView and onDestroyView
    private val binding: FragmentCameraNewBinding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initCamera()
    }

    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        val analyzer = OcrAnalyzer(
            requireContext(),
            { map ->
                Log.d(TAG, "Analysis: $map")
                activity?.runOnUiThread {
                    _binding?.apply {
                        resultTextView.text = map.toString()
                    }
                }
            },
            { bitmap1, bitmap2, bitmap3 ->
                activity?.runOnUiThread {
                    _binding?.apply {
                        debugImageView.setImageBitmap(bitmap1)
                        debugImageView2.setImageBitmap(bitmap2)
                        debugImageView3.setImageBitmap(bitmap3)
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
                    .apply { setSurfaceProvider(binding.previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(Dispatchers.Default.asExecutor(), analyzer)

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
