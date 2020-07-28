package com.cradle.neptune.view.sync

import android.app.ActionBar
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.cradle.neptune.R

class SyncActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)
        setSupportActionBar(findViewById(R.id.toolbar2))
        supportActionBar?.setTitle("Sync Everything")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager.beginTransaction().replace(R.id.syncFrameLayout,SyncUploadFragment(),"syncUpload").commit()

        findViewById<Button>(R.id.uploadEverythingButton).setOnClickListener {
            supportFragmentManager.beginTransaction().replace(R.id.syncFrameLayout,SyncResultFragment()).commit()
            //dont make it clickable for now
            it.isClickable = false
            it.isEnabled = false
            it.alpha = 0.2F
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    override fun onAttachFragment(fragment: Fragment) {
        //setup some sort of call backs
        when(fragment){
            is SyncUploadFragment -> {
                Log.d("bugg", "SyncUploadFragment is attached")
            }
            is SyncResultFragment -> {
                Log.d("bugg", "SyncDownload frag is attached")
            }
        }
    }


}