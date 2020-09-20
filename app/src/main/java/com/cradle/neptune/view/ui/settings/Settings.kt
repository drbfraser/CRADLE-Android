package com.cradle.neptune.view.ui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.manager.LoginManager
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.sync.SyncStepperImplementation
import com.cradle.neptune.utilitiles.validateHostname
import com.cradle.neptune.utilitiles.validatePort
import com.cradle.neptune.view.LoginActivity
import com.cradle.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsActivity : AppCompatActivity() {
    companion object {
        fun makeLaunchIntent(context: Context) = Intent(context, SettingsActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Register a listener with the back stack to update the action bar title
        // based on what fragment is on top.
        supportFragmentManager.addOnBackStackChangedListener {
            val count = supportFragmentManager.backStackEntryCount
            var title = getString(R.string.settings_title)
            if (count != 0) {
                val name = supportFragmentManager.getBackStackEntryAt(count - 1).name
                if (name == AdvancedSettingsFragment::class.qualifiedName) {
                    title = getString(R.string.settings_advanced)
                }
            }
            supportActionBar?.title = title
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        onBackPressed()
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount == 0) {
            super.onBackPressed()
        } else {
            supportFragmentManager.popBackStack()
        }
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var healthCentreManager: HealthCentreManager

    @Inject
    lateinit var readingManager: ReadingManager

    @Inject
    lateinit var patientManager: PatientManager

    override fun onResume() {
        super.onResume()

        // Summary for this preference is not generated through shared
        // preferences so we have to update it manually here.
        findPreference(R.string.key_health_centres_settings_button)?.apply {
            GlobalScope.launch(Dispatchers.IO) {
                val hcCount = healthCentreManager.getAllSelectedByUser().size
                // need to update UI by main thread
                withContext(Dispatchers.Main) {
                    summary = resources.getQuantityString(
                        R.plurals.settings_n_configured_health_centres,
                        hcCount,
                        hcCount
                    )
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        (activity?.application as MyApp)
            .appComponent
            .inject(this)

        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference(R.string.key_health_centres_settings_button)
            ?.withLaunchActivityOnClick(this, HealthFacilitiesActivity::class.java)

        findPreference(R.string.key_advanced_settings_settings_button)
            ?.withOnClickListener {
                parentFragmentManager
                    .beginTransaction()
                    .replace(android.R.id.content, AdvancedSettingsFragment())
                    .addToBackStack(AdvancedSettingsFragment::class.qualifiedName) // add to back stack with name
                    .commit()
                true
            }

        findPreference(R.string.key_sign_out)
            ?.withOnClickListener {
                GlobalScope.launch(Dispatchers.IO) {
                    val unUploadedReadings = readingManager.getUnUploadedReadings()
                    val description = if (unUploadedReadings.isEmpty()) {
                        getString(R.string.normal_signout_message)
                    } else {
                        resources.getQuantityString(
                            R.plurals.unuploaded_reading_signout_message,
                            unUploadedReadings.size,
                            unUploadedReadings.size
                        )
                    }
                    // need to switch the context to main thread since ui is always created on main thread
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(requireActivity())
                            .setTitle(getString(R.string.sign_out_question))
                            .setMessage(description)
                            .setPositiveButton(getString(android.R.string.yes)) { _, _ -> onSignOut() }
                            .setNegativeButton(getString(android.R.string.no), null)
                            .setIcon(R.drawable.ic_sync)
                            .create()
                            .show()
                    }
                }
                true
            }

        findPreference(R.string.key_vht_name)
            ?.useDynamicSummary()

        findPreference(R.string.key_region)
            ?.useDynamicSummary()
    }

    // TODO: The business logic portion of this method should be moved to
    //  com.cradle.neptune.manager.LoginManager. By "business logic" I mean
    //  the stuff that edits shared preferences and the database. The code
    //  to start the activity is fine here as we don't want UI code in the
    //  manager class.
    private fun onSignOut() {
        val editor = sharedPreferences.edit()
        editor.putString(LoginManager.EMAIL_KEY, "")
        editor.putString(LoginManager.TOKEN_KEY, null)
        editor.putString(LoginManager.USER_ID_KEY, null)
        editor.putLong(SyncStepperImplementation.LAST_SYNC, 0L)
        editor.apply()
        GlobalScope.launch(Dispatchers.IO) { readingManager.deleteAllData() }
        GlobalScope.launch(Dispatchers.IO) { healthCentreManager.deleteAll() }
        GlobalScope.launch(Dispatchers.IO) { patientManager.deleteAll() }
        startActivity(Intent(activity, LoginActivity::class.java))
        requireActivity().finishAffinity()
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
            ?.useDynamicSummary { v ->
                if (v.isNullOrEmpty())
                    getString(R.string.default_settings)
                else
                    v
            }
            ?.withValidator(::validatePort)
    }
}
