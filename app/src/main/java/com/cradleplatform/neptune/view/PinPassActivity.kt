package com.cradleplatform.neptune.view

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.CradleApplication
import com.cradleplatform.neptune.R

class PinPassActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_pass)

        val app = this.application as CradleApplication

        //Proof of concept will be changed
        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener {
            app.pinActivityActive
            finish()
        }
    }

    override fun onBackPressed() {
        //Do not allow user to leave this screen until password is entered or app exited
    }
}
