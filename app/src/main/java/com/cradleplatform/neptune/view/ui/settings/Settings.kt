package com.cradleplatform.neptune.view.ui.settings
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import com.cradleplatform.neptune.CradleApplication
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.manager.LoginManager.Companion.RELAY_PHONE_NUMBER
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.model.RelayPhoneNumberResponse
import com.cradleplatform.neptune.sync.PeriodicSyncer
import com.cradleplatform.neptune.utilities.validateHostname
import com.cradleplatform.neptune.utilities.validatePort
import com.cradleplatform.neptune.view.LoginActivity
import com.cradleplatform.neptune.view.PinPassActivity
import com.cradleplatform.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val ADVANCED_SETTINGS_KEY: String = "advanced settings"

        fun makeSettingsActivityLaunchIntent(context: Context): Intent =
            Intent(context, SettingsActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchAdvancedSettings: Boolean = intent.getBooleanExtra(ADVANCED_SETTINGS_KEY, false)

        if (launchAdvancedSettings) {
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

    @Inject
    lateinit var restApi: RestApi

    private var selectedPosition = -1 // the index of the selected relay phone number in the list view

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

        findPreference(R.string.key_change_pin)
            ?.withOnClickListener {
                val intent = Intent(activity, PinPassActivity::class.java)
                intent.putExtra("isChangePin", true)
                startActivity(intent)
                requireActivity().finishAffinity()
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

        findPreference(R.string.key_change_relay_phone_number)?.withOnClickListener {
            lifecycleScope.launch {
                val relayPhoneNumbers = restApi.getAllRelayPhoneNumbers()
                handleRelayPhoneNumbersResult(relayPhoneNumbers)
            }
            true
        }

        findPreference(R.string.key_vht_name)
            ?.useDynamicSummary()

        findPreference(R.string.key_role)
            ?.useDynamicSummary()

        findPreference(R.string.key_region)
            ?.useDynamicSummary()
    }

    /**
     * Handles the result of fetching relay phone numbers from the API.
     * @param result The network result containing the result and relay phone numbers (if result is successful).
     */
    private fun handleRelayPhoneNumbersResult(result: NetworkResult<RelayPhoneNumberResponse>) {
        when (result) {
            is NetworkResult.Success -> showRelayPhoneNumbersDialog(result.value.relayPhoneNumbers)
            else -> showErrorToast("Failed to fetch relay phone numbers. Check your internet connection")
        }
    }

    /**
     * Displays a dialog containing the list of relay phone numbers.
     * @param phoneNumbers The list of relay phone numbers to display.
     */
    private fun showRelayPhoneNumbersDialog(phoneNumbers: List<String>) {
        val listView = ListView(requireContext())
        val adapter = ArrayAdapter(requireContext(), R.layout.list_item_relay, phoneNumbers)
        listView.adapter = adapter

        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedPosition = position
            adapter.notifyDataSetChanged()
        }

        listView.selector = ResourcesCompat.getDrawable(resources, R.drawable.list_item_selector_relay_numbers, null)
        val numberToPreselect = sharedPreferences.getString(RELAY_PHONE_NUMBER, "")
        val preselectedIndex = phoneNumbers.indexOf(numberToPreselect)
        if (preselectedIndex != -1) {
            listView.setItemChecked(preselectedIndex, true)
            adapter.notifyDataSetChanged()
        }

        val dialog = createRelayPhoneNumberDialog(listView, phoneNumbers)
        dialog.show()
    }

    /**
     * Creates an AlertDialog for selecting a relay phone number from the list.
     * @param listView The ListView containing the list of phone numbers.
     * @param selectedPosition The initially selected position in the list.
     * @param phoneNumbers The list of relay phone numbers.
     * @return The AlertDialog instance.
     */
    private fun createRelayPhoneNumberDialog(listView: ListView, phoneNumbers: List<String>): AlertDialog {
        return AlertDialog.Builder(requireActivity())
            .setTitle(R.string.select_relay_phone_number_title)
            .setView(listView)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Submit") { dialog, _ ->
                if (selectedPosition != -1) {
                    val selectedPhoneNumber = phoneNumbers[selectedPosition]
                    sharedPreferences.edit().putString(RELAY_PHONE_NUMBER, selectedPhoneNumber).apply()
                    showToast("Successfully updated the relay phone number.")
                } else {
                    showToast("Failed to update relay phone number. You need to select a phone number.")
                }
                dialog.dismiss()
            }
            .setIcon(R.drawable.ic_edit_24)
            .create()
    }

    /**
     * Displays a short-duration toast with the given message.
     * @param message The message to display in the toast.
     */
    private fun showToast(message: String) {
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast.show()
    }

    /**
     * Displays an error toast with the given error message.
     * @param message The error message to display in the toast.
     */
    private fun showErrorToast(errorMessage: String) {
        showToast(errorMessage)
    }

    private fun onSignOut() {
        val scope = (requireActivity().application as CradleApplication).appCoroutineScope
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

    @Inject
    lateinit var periodicSyncer: PeriodicSyncer

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

        val myPref = findPreference(R.string.key_periodic_sync_enabled)?.withValidator(::validatePort)

        myPref?.onPreferenceChangeListener =
            // The callback is triggered whenever the switch preference is changed
            Preference.OnPreferenceChangeListener { pref, newValue ->
                if (newValue == true) {
                    periodicSyncer.startPeriodicSync()
                } else {
                    periodicSyncer.endPeriodicSync()
                }
                true
            }
    }
}
