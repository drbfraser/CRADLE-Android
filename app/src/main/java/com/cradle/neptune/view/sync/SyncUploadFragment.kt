package com.cradle.neptune.view.sync

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.cradle.neptune.R



/**
 * A simple [Fragment] subclass.
 * Use the [SyncUploadFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SyncUploadFragment : Fragment() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view  = inflater.inflate(R.layout.fragment_sync_upload, container, false)
        return view
    }

}