package com.cradleVSA.neptune.view.ui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import com.cradleVSA.neptune.MyApp
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.manager.HealthFacilityManager
import com.cradleVSA.neptune.manager.LoginManager
import com.cradleVSA.neptune.manager.PatientManager
import com.cradleVSA.neptune.manager.ReadingManager
import com.cradleVSA.neptune.utilitiles.validateHostname
import com.cradleVSA.neptune.utilitiles.validatePort
import com.cradleVSA.neptune.view.LoginActivity
import com.cradleVSA.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    companion object {
        private var launchingContext:Context? = null

        fun makeLaunchIntent(context: Context): Intent {
            launchingContext = context
            return Intent(context, SettingsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(launchingContext!!.javaClass == LoginActivity::class.java) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, AdvancedSettingsFragment())
                .commit()
        } else {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }

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

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var loginManager: LoginManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var healthFacilityManager: HealthFacilityManager

    @Inject
    lateinit var readingManager: ReadingManager

    @Inject
    lateinit var patientManager: PatientManager

    override fun onResume() {
        super.onResume()

        // Summary for this preference is not generated through shared
        // preferences so we have to update it manually here.
        findPreference(R.string.key_health_facilities_settings_button)?.apply {
            lifecycleScope.launch(Dispatchers.IO) {
                val hcCount = healthFacilityManager.getAllSelectedByUser().size
                // need to update UI by main thread
                withContext(Dispatchers.Main) {
                    summary = resources.getQuantityString(
                        R.plurals.settings_n_configured_health_facilities,
                        hcCount,
                        hcCount
                    )
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference(R.string.key_health_facilities_settings_button)
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
                lifecycleScope.launch(Dispatchers.IO) {
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
                        MaterialAlertDialogBuilder(requireActivity())
                            .setTitle(getString(R.string.sign_out_question))
                            .setMessage(description)
                            .setPositiveButton(R.string.sign_out_dialog_yes_button) { _, _ ->
                                LoggingOutDialogFragment().show(
                                    childFragmentManager,
                                    TAG_LOG_OUT_DIALOG
                                )
                                onSignOut()
                            }
                            .setNegativeButton(android.R.string.no, null)
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

    private fun onSignOut() {
        val scope = (requireActivity().application as MyApp).appCoroutineScope
        scope.launch(Dispatchers.Main) {
            loginManager.logout()
            startActivity(Intent(activity, LoginActivity::class.java))
            requireActivity().finishAffinity()
        }
    }

    companion object {
        private const val TAG_LOG_OUT_DIALOG = "logging_out_dialog"
    }
}

@AndroidEntryPoint
class AdvancedSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.v(this::class.simpleName, "Loading advanced settings from resource")
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)

        findPreference(R.string.key_server_hostname)
            ?.useDynamicSummary()
            ?.withValidator(::validateHostname)

        findPreference(R.string.key_server_port)
            ?.useDynamicSummary { v ->
                if (v.isNullOrEmpty()) {
                    getString(R.string.default_settings)
                } else {
                    v
                }
            }
            ?.withValidator(::validatePort)
    }
}
