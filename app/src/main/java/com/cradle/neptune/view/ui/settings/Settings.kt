package com.cradle.neptune.view.ui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.view.LoginActivity
import com.cradle.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var healthCentreManager: HealthCentreManager

    @Inject
    lateinit var readingManager: ReadingManager

    override fun onResume() {
        super.onResume()

        // Summary for this preference is not generated through shared
        // preferences so we have to update it manually here.
        findPreference(R.string.key_health_centres_settings_button)?.apply {
            val hcCount = healthCentreManager.getAllSelectedByUserBlocking().size
            summary = "$hcCount configured health centres"
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
            ?.withLaunchActivityOnClick(this, AdvancedSettingsActivity::class.java)

        findPreference(R.string.key_sign_out)
            ?.withOnClickListener {
                val unUploadedReadings = readingManager.getUnUploadedReadingsBlocking()
                val description = if (unUploadedReadings.isEmpty()) {
                    getString(R.string.normalSignoutMessage)
                } else {
                    "${unUploadedReadings.size} ${getString(R.string.unUploadedReadingSignoutMessage)}"
                }

                AlertDialog.Builder(activity!!)
                    .setTitle("Sign out?")
                    .setMessage(description)
                    .setPositiveButton("Yes") { _, _ -> onSignOut() }
                    .setNegativeButton("No", null)
                    .setIcon(R.drawable.ic_sync)
                    .create()
                    .show()

                true
            }

        findPreference(R.string.key_vht_name)
            ?.useDynamicSummary()

        findPreference(R.string.key_region)
            ?.useDynamicSummary()
    }

    // TODO: This should be moved elsewhere
    private fun onSignOut() {
        val editor = sharedPreferences.edit()
        editor.putString(LoginActivity.LOGIN_EMAIL, "")
        editor.putInt(LoginActivity.LOGIN_PASSWORD, LoginActivity.DEFAULT_PASSWORD)
        editor.putString(LoginActivity.TOKEN, "")
        editor.putString(LoginActivity.USER_ID, "")
        editor.apply()
        MainScope().launch { readingManager.deleteAllData() }
        MainScope().launch { healthCentreManager.deleteAllData() }
        startActivity(Intent(activity, LoginActivity::class.java))
        activity!!.finishAffinity()
    }
}
