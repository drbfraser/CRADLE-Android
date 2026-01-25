package com.cradleplatform.neptune.activities.dashboard
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.ActivityDashBoardBinding
import com.cradleplatform.neptune.sync.SyncReminderHelper
import com.cradleplatform.neptune.sync.views.SyncActivity
import com.cradleplatform.neptune.utilities.CustomToast
import com.cradleplatform.neptune.utilities.Util
import com.cradleplatform.neptune.activities.education.EducationActivity
import com.cradleplatform.neptune.activities.patients.PatientsActivity
import com.cradleplatform.neptune.activities.newPatient.ReadingActivity
import com.cradleplatform.neptune.activities.forms.SavedFormsActivity
import com.cradleplatform.neptune.activities.statistics.StatsActivity
import com.cradleplatform.neptune.activities.settings.SettingsActivity.Companion.makeSettingsActivityLaunchIntent
import com.cradleplatform.neptune.viewmodel.DashboardViewModel
import com.cradleplatform.neptune.viewmodel.SmsKeyUpdateState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DashBoardActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityDashBoardBinding
    private val viewModel: DashboardViewModel by viewModels()

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDashBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        setupObservers()
        setupOnClickListener()
        checkPinIfPinSet()
        setVersionName()
        viewModel.validateSmsKeyAndPerformActions()
    }

    private fun setupActionBar() {
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayUseLogoEnabled(true)
            title = ""
        }
    }

    private fun setupObservers() {
        // Observe network state for enabling/disabling statistics
        viewModel.isNetworkAvailable.observe(this) { isAvailable ->
            updateStatisticsCardState(isAvailable)
        }

        // Observe SMS key update results
        viewModel.smsKeyUpdateResult.observe(this) { state ->
            handleSmsKeyUpdateState(state)
        }

        // Observe new phone number detection
        viewModel.newPhoneNumber.observe(this) { newNumber ->
            newNumber?.let { showPhoneChangedDialog(it) }
        }
    }

    private fun updateStatisticsCardState(isNetworkAvailable: Boolean) {
        binding.statConstraintLayout.apply {
            val alpha = if (isNetworkAvailable) OPACITY_FULL else OPACITY_HALF
            val statImg = findViewById<View>(R.id.statImg)
            val statCardView = findViewById<View>(R.id.statCardView)

            statImg.alpha = alpha
            statCardView.alpha = alpha
            isClickable = isNetworkAvailable
            statCardView.isClickable = isNetworkAvailable
            statImg.isClickable = isNetworkAvailable
        }
    }

    private fun handleSmsKeyUpdateState(state: SmsKeyUpdateState) {
        when (state) {
            is SmsKeyUpdateState.Success -> {
                showToast(state.message)
                viewModel.resetSmsKeyUpdateState()
            }
            is SmsKeyUpdateState.Error -> {
                showToast(state.message)
                viewModel.resetSmsKeyUpdateState()
            }
            is SmsKeyUpdateState.Warning -> {
                showExpiryWarning(this, state.daysUntilExpiry)
                viewModel.resetSmsKeyUpdateState()
            }
            is SmsKeyUpdateState.Idle -> {
                // No action needed
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showExpiryWarning(context: Context, daysUntilExpiry: Int) {
        val message = "Warning: Your SMS key is set to expire in $daysUntilExpiry days.\n" +
            "To avoid disruption, promptly refresh your SMS key through the settings."

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun showPhoneChangedDialog(newPhoneNumber: String) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.phone_new_number_detected))
        builder.setMessage(
            String.format(getString(R.string.phone_update_number_to), newPhoneNumber)
        )

        builder.setNegativeButton("Yes") { _, _ ->
            viewModel.updateUserPhoneNumber(newPhoneNumber)
        }

        builder.setPositiveButton("No") { _, _ ->
            viewModel.dismissPhoneNumberDialog()
        }
        builder.show()
    }

    private fun setVersionName() {
        binding.versionNameTextView.text = Util.getVersionName(this)
    }

    override fun onRestart() {
        super.onRestart()
        reminderUserToSync()
    }

    private fun reminderUserToSync() {
        if (SyncReminderHelper.checkIfOverTime(this, sharedPreferences))
            CustomToast.longToast(
                this,
                getString(R.string.remind_user_to_sync)
            )
    }

    private fun setupOnClickListener() {
        // Patient card
        binding.patientConstraintLayout.apply {
            findViewById<View>(R.id.patientCardView).setOnClickListener(this@DashBoardActivity)
            findViewById<View>(R.id.patientImg).setOnClickListener(this@DashBoardActivity)
        }

        // Stats card
        binding.statConstraintLayout.apply {
            findViewById<View>(R.id.statCardView).setOnClickListener(this@DashBoardActivity)
            findViewById<View>(R.id.statImg).setOnClickListener(this@DashBoardActivity)
        }

        // Sync card
        binding.syncConstraintlayout.apply {
            findViewById<View>(R.id.syncCardView).setOnClickListener(this@DashBoardActivity)
            findViewById<View>(R.id.syncImg).setOnClickListener(this@DashBoardActivity)
        }

        // Reading card
        binding.readingConstraintLayout.apply {
            findViewById<View>(R.id.readingCardView).setOnClickListener(this@DashBoardActivity)
            findViewById<View>(R.id.readingImg).setOnClickListener(this@DashBoardActivity)
        }

        // Education card
        binding.educationConstraintLayout.apply {
            findViewById<View>(R.id.educationCardView).setOnClickListener(this@DashBoardActivity)
            findViewById<View>(R.id.educationImg).setOnClickListener(this@DashBoardActivity)
        }

        // Forms card
        binding.formsConstraintLayout.apply {
            findViewById<View>(R.id.formsCardView).setOnClickListener(this@DashBoardActivity)
            findViewById<View>(R.id.formsImg).setOnClickListener(this@DashBoardActivity)
        }
    }

    private fun checkPinIfPinSet() {
        val pinCodePrefKey = getString(R.string.key_pin_shared_key)
        val defaultPinCode = getString(R.string.key_pin_default_pin)

        if (viewModel.checkPinIfPinSet(pinCodePrefKey, defaultPinCode)) {
            AlertDialog.Builder(this@DashBoardActivity)
                .setMessage(R.string.dash_pin_not_set)
                .setCancelable(true)
                .setTitle(R.string.warning)
                .setPositiveButton(R.string.ok) { _, _ -> }
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        if (id == R.id.action_settings) {
            val intent = makeSettingsActivityLaunchIntent(this)
            @Suppress("DEPRECATION")
            startActivityForResult(
                intent,
                TabActivityBase.TAB_ACTIVITY_BASE_SETTINGS_DONE
            )
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.readingCardView, R.id.readingImg -> {
                val intent = ReadingActivity.makeIntentForNewReading(this@DashBoardActivity)
                @Suppress("DEPRECATION")
                startActivityForResult(intent, READING_ACTIVITY_DONE)
            }

            R.id.patientCardView, R.id.patientImg -> startActivity(PatientsActivity.makeIntent(this))

            R.id.syncCardView, R.id.syncImg -> startActivity(Intent(this, SyncActivity::class.java))

            R.id.educationCardView, R.id.educationImg -> startActivity(Intent(this, EducationActivity::class.java))

            R.id.statCardView, R.id.statImg -> startActivity(Intent(this, StatsActivity::class.java))
            R.id.formsCardView, R.id.formsImg -> {
                val intent = Intent(this, SavedFormsActivity::class.java)
                intent.putExtra("Patient ID that the forms are saved for", "")
                intent.putExtra("The Patient object that the forms are saved for", "")
                intent.putExtra("Boolean value indicating whether the forms are saved", true)
                intent.putExtra("The previous page the backspace leads to", true)
                startActivity(intent) }
        }
    }

    companion object {
        const val TAG = "DashBoardActivity"

        const val READING_ACTIVITY_DONE = 12345
        const val OPACITY_HALF = 0.5f
        const val OPACITY_FULL = 1.0f
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java",
        ReplaceWith("super.onBackPressed()", "androidx.appcompat.app.AppCompatActivity")
    )
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
