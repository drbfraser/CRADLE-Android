package com.cradleplatform.neptune.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.R
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class FormsActivity : AppCompatActivity(){
    //private val viewModel: SavedFormsViewModel by viewModels()
    //private var formList: MutableList<FormResponse>? = null
    //private var adapter: SavedFormAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_forms)

       // setUpSavedFormsRecyclerView()
        setUpActionBar()
    }

    /*
    private fun setUpSavedFormsRecyclerView() {

    }*/

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setUpActionBar() {
        supportActionBar?.title = getString(R.string.see_saved_forms)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

}
