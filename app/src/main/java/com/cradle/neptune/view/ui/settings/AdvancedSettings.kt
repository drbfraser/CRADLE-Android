package com.cradle.neptune.view.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.utilitiles.functional.Left
import com.cradle.neptune.utilitiles.functional.Right
import javax.inject.Inject

class AdvancedSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(this::class.simpleName, "Created advanced settings activity")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Advanced Settings"

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

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        (activity?.application as MyApp)
            .appComponent
            .inject(this)

        Log.v(this::class.simpleName, "Loading advanced settings from resource")
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)

        preferenceScreen
            .findPreference<Preference>("setting_server_hostname")
            ?.useDynamicSummary()

        preferenceScreen
            .findPreference<Preference>("setting_server_port")
            ?.useDynamicSummary { v -> if (v.isNullOrEmpty()) "(default)" else v }
            ?.withValidator { value: String ->
                // Allow black values as they will be treated as the default port number
                if (value.isEmpty()) {
                    return@withValidator Right(Unit)
                }

                // Ensure that the port is actually a number
                val int = value.toIntOrNull() ?: return@withValidator Left("Port must be a number")

                // Check to make sure it is a valid port number
                if (int !in 0..49151) {
                    return@withValidator Left("'$value' is not a valid port number")
                }

                Right(Unit)
            }
    }
}
