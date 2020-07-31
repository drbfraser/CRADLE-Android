package com.cradle.neptune.view.sync

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A Fragment to show all the objects needing to be uploaded to the server
 */
class SyncUploadFragment : Fragment() {

    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var patientManager: PatientManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as MyApp).appComponent.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view  = inflater.inflate(R.layout.fragment_sync_upload, container, false)
        lifecycleScope.launch(Dispatchers.IO) {
            setupReadingRecyclerview(view)
        }
        return view

    }

    /**
     * setups recyclerview for readings or hides the view if there is no readings to upload
     */
    private suspend fun setupReadingRecyclerview(view: View) {
        val recyclerview: RecyclerView = view.findViewById(R.id.readingRecyclerview)
        val layoutManager = LinearLayoutManager(context)
        val readingList = readingManager.getUnUploadedReadings()
        if (readingList.isEmpty()){
            recyclerview.visibility = View.GONE
            return
        }
        val readingAdapter = SyncReadingRecyclerview(readingList)
        withContext(Dispatchers.Main) {
            recyclerview . layoutManager = layoutManager
            recyclerview.adapter = readingAdapter
            readingAdapter.notifyDataSetChanged()
        }
    }
}