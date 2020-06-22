package com.cradle.neptune.view.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.utilitiles.validateHostname
import com.cradle.neptune.utilitiles.validatePort

class AdvancedSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(this::class.simpleName, "Created advanced settings activity")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Advanced"

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, AdvancedSettingsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        Log.v(this::class.simpleName, "Navigating away from advanced settings")
        onBackPressed()
        return super.onSupportNavigateUp()
    }
}

class AdvancedSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        (activity?.application as MyApp)
            .appComponent
            .inject(this)

        Log.v(this::class.simpleName, "Loading advanced settings from resource")
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)

        findPreference(R.string.key_server_hostname)
            ?.useDynamicSummary()
            ?.withValidator(::validateHostname)

        findPreference(R.string.key_server_port)
            ?.useDynamicSummary { v -> if (v.isNullOrEmpty()) "(default)" else v }
            ?.withValidator(::validatePort)
    }
}
