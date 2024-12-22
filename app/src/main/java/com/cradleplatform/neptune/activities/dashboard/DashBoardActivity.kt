package com.cradleplatform.neptune.activities.dashboard
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.ViewModelProvider
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.model.SmsKeyResponse
import com.cradleplatform.neptune.sync.SyncReminderHelper
import com.cradleplatform.neptune.sync.views.SyncActivity
import com.cradleplatform.neptune.utilities.CustomToast
import com.cradleplatform.neptune.utilities.Util
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.activities.education.EducationActivity
import com.cradleplatform.neptune.activities.patients.PatientsActivity
import com.cradleplatform.neptune.activities.newPatient.ReadingActivity
import com.cradleplatform.neptune.activities.forms.SavedFormsActivity
import com.cradleplatform.neptune.activities.statistics.StatsActivity
import com.cradleplatform.neptune.activities.settings.SettingsActivity.Companion.makeSettingsActivityLaunchIntent
import com.cradleplatform.neptune.manager.SmsKey
import com.cradleplatform.neptune.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DashBoardActivity : AppCompatActivity(), View.OnClickListener {
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var networkStateManager: NetworkStateManager
    private lateinit var userViewModel: UserViewModel

    @Inject
    lateinit var smsKeyManager: SmsKeyManager
    @Inject
    lateinit var restApi: RestApi

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash_board)
        checkPinIfPinSet()
        setupOnClickListener()

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true)
            actionBar.setDisplayUseLogoEnabled(true)
            actionBar.title = ""
        }

        userViewModel = ViewModelProvider(this)[UserViewModel::class.java]
        userViewModel.getNewNumber().let {
            if (it.isNotEmpty()) {
                showPhoneChangedDialog(it)
            }
            // else: getNewNumber returned null meaning new phone number has not been detected
        }

        networkCheck()
        setVersionName()
        validateSmsKeyAndPerformActions()
    }

    private fun networkCheck() {
        // Disable entering StatsActivity without network connectivity.
        val statView = findViewById<View>(R.id.statConstraintLayout)
        val statImg = statView.findViewById<ImageButton>(R.id.statImg)
        val statCardview: CardView = statView.findViewById(R.id.statCardView)
        val isNetworkAvailable = networkStateManager.getInternetConnectivityStatus()

        isNetworkAvailable.observe(this) {
            when (it) {
                true -> {
                    statImg.alpha = OPACITY_FULL
                    statCardview.alpha = OPACITY_FULL
                    statView.isClickable = true
                    statCardview.isClickable = true
                    statImg.isClickable = true
                }
                false -> {
                    statImg.alpha = OPACITY_HALF
                    statCardview.alpha = OPACITY_HALF
                    statView.isClickable = false
                    statCardview.isClickable = false
                    statImg.isClickable = false
                }
            }
        }
    }

    private fun validateSmsKeyAndPerformActions() {
        // check SMS key validity only when there is internet connection
        val isNetworkAvailable = networkStateManager.getInternetConnectivityStatus().value
        if ((isNetworkAvailable != null) && isNetworkAvailable) {
            val smsKeyStatus = smsKeyManager.validateSmsKey()
            val userId = sharedPreferences.getInt(UserViewModel.USER_ID_KEY, -1)
            if (smsKeyStatus == SmsKeyManager.KeyState.NOTFOUND) {
                // User doesn't have a valid sms key
                coroutineScope.launch {
                    val response = restApi.getNewSmsKey(userId)
                    handleSmsKeyUpdateResult(response)
                }
            }
            if (smsKeyStatus == SmsKeyManager.KeyState.EXPIRED) {
                // User's sms key is expired
                if (userId != -1) {
                    coroutineScope.launch {
                        val response = restApi.refreshSmsKey(userId)
                        handleSmsKeyUpdateResult(response)
                    }
                }
            } else if (smsKeyStatus == SmsKeyManager.KeyState.WARN) {
                // User's sms key is stale - Warn the user to refresh their SmsKey
                val daysUntilExpiry = smsKeyManager.getDaysUntilExpiry()
                showExpiryWarning(applicationContext, daysUntilExpiry)
            }
        }
    }

    private fun handleSmsKeyUpdateResult(result: NetworkResult<SmsKey>) {
        when (result) {
            is NetworkResult.Success -> {
                smsKeyManager.storeSmsKey(result.value)
                showToast("Key update was successful")
            }
            else -> showToast("Network Error: Key update unsuccessful")
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
            userViewModel.updateUserPhoneNumbers(newPhoneNumber)
        }

        builder.setPositiveButton("No") { _, _ ->
            // Do nothing as user as clicked no
        }
        builder.show()
    }

    private fun setVersionName() {
        val textView: TextView = findViewById(R.id.versionNameTextView)
        textView.text = Util.getVersionName(this)
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
        val patientView =
            findViewById<View>(R.id.patientConstraintLayout)
        val patientCardView: CardView = patientView.findViewById(R.id.patientCardView)
        val patientImg = patientView.findViewById<ImageButton>(R.id.patientImg)
        patientCardView.setOnClickListener(this)
        patientImg.setOnClickListener(this)

        val statView = findViewById<View>(R.id.statConstraintLayout)
        val statCardview: CardView = statView.findViewById(R.id.statCardView)
        val statImg = statView.findViewById<ImageButton>(R.id.statImg)
        statCardview.setOnClickListener(this)
        statImg.setOnClickListener(this)

        val syncView =
            findViewById<View>(R.id.syncConstraintlayout)
        val syncCardview: CardView = syncView.findViewById(R.id.syncCardView)
        val syncImg = syncView.findViewById<ImageButton>(R.id.syncImg)
        syncCardview.setOnClickListener(this)
        syncImg.setOnClickListener(this)

        val readingLayout =
            findViewById<View>(R.id.readingConstraintLayout)
        val readingCardView: CardView = readingLayout.findViewById(R.id.readingCardView)
        val readImg = readingLayout.findViewById<ImageButton>(R.id.readingImg)
        readingCardView.setOnClickListener(this)
        readImg.setOnClickListener(this)

        val helpLayout =
            findViewById<View>(R.id.educationConstraintLayout)
        val helpCardView: CardView = helpLayout.findViewById(R.id.educationCardView)
        val helpImg = helpLayout.findViewById<ImageButton>(R.id.educationImg)
        helpCardView.setOnClickListener(this)
        helpImg.setOnClickListener(this)

        val formsLayout =
            findViewById<View>(R.id.formsConstraintLayout)
        val formsCardView: CardView = formsLayout.findViewById(R.id.formsCardView)
        val formsImg = formsLayout.findViewById<ImageButton>(R.id.formsImg)
        formsCardView.setOnClickListener(this)
        formsImg.setOnClickListener(this)
    }

    private fun checkPinIfPinSet() {
        val pinCodePrefKey = getString(R.string.key_pin_shared_key)
        val pinPassSharedPreferences = getString(R.string.key_pin_shared_pref)
        val defaultPinCode = getString(R.string.key_pin_default_pin)
        val sharedPref = getSharedPreferences(pinPassSharedPreferences, Context.MODE_PRIVATE) ?: return
        if (sharedPref.getString(pinCodePrefKey, defaultPinCode) == defaultPinCode) {
            AlertDialog.Builder(this@DashBoardActivity)
                .setMessage(R.string.dash_pin_not_set)
                .setCancelable(true)
                .setTitle(R.string.warning)
                .setPositiveButton(R.string.ok) { _, _ ->
                }
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
