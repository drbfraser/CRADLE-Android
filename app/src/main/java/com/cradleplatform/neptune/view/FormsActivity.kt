package com.cradleplatform.neptune.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FormsActivity : AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_forms)

        setUpSavedFormsRecyclerView()
        setUpActionBar()
    }

    private fun setUpSavedFormsRecyclerView() {

    }
    private fun setUpActionBar() {
        supportActionBar?.title = getString(R.string.see_saved_forms)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}