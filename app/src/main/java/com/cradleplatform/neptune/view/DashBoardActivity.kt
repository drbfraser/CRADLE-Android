package com.cradleplatform.neptune.view

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.utilities.CustomToast
import com.cradleplatform.neptune.utilities.Util
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import com.cradleplatform.neptune.view.ui.settings.SettingsActivity.Companion.makeSettingsActivityLaunchIntent
import com.cradleplatform.neptune.viewmodel.SyncRemainderHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DashBoardActivity : AppCompatActivity(), View.OnClickListener {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private val userPhoneNumberKey = "user_phone_number"
    private lateinit var userPhoneNumber: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dash_board)
        checkPinIfPinSet()
        setupOnClickListner()

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true)
            actionBar.setDisplayUseLogoEnabled(true)
            actionBar.title = ""
        }
        // To detect change in user's phone number
        userPhoneNumber = sharedPreferences.getString(userPhoneNumberKey, "") ?: ""

        networkCheck()
        setVersionName()
        updateUserNumber()
    }

    private fun updateUserNumber() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) ==
            PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val telManager = this.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val newPhoneNumber = telManager.line1Number

            if (newPhoneNumber != userPhoneNumber) {
                userPhoneNumber = newPhoneNumber
                sharedPreferences.edit().putString(userPhoneNumberKey, userPhoneNumber).apply()

                // TODO: update the user's phone number in the database
            }
        }
        // else: either the phone number doesn't exist
        // or permission is not granted - userPhoneNumber would remain equal to ""
        // TODO: check before sending SMS
    }

    private fun networkCheck() {
        // Disable entering StatsActivity without network connectivity.

        val statView = findViewById<View>(R.id.statconstraintLayout)
        val statImg = statView.findViewById<ImageButton>(R.id.statImg)
        val statCardview: CardView = statView.findViewById(R.id.statCardView)
        val isNetworkAvailable = NetworkAvailableLiveData(this)

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

    private fun setVersionName() {
        val textView: TextView = findViewById(R.id.versionNameTextView)
        textView.text = Util.getVersionName(this)
    }

    override fun onRestart() {
        super.onRestart()
        reminderUserToSync()
    }

    private fun reminderUserToSync() {
        if (SyncRemainderHelper.checkIfOverTime(this, sharedPreferences))
            CustomToast.longToast(
                this,
                getString(R.string.remind_user_to_sync)
            )
    }

    private fun setupOnClickListner() {
        val patientView =
            findViewById<View>(R.id.patientConstraintLayout)
        val patientCardView: CardView = patientView.findViewById(R.id.patientCardview)
        val patientImg = patientView.findViewById<ImageButton>(R.id.patientImg)
        val statView = findViewById<View>(R.id.statconstraintLayout)
        val statCardview: CardView = statView.findViewById(R.id.statCardView)
        val statImg = statView.findViewById<ImageButton>(R.id.statImg)
        val uploadCard =
            findViewById<View>(R.id.syncConstraintlayout)
        val syncCardview: CardView = uploadCard.findViewById(R.id.syncCardView)
        val syncImg = uploadCard.findViewById<ImageButton>(R.id.syncImg)
        val readingLayout =
            findViewById<View>(R.id.readingConstraintLayout)
        val readingCardView: CardView = readingLayout.findViewById(R.id.readingCardView)
        val readImg = readingLayout.findViewById<ImageButton>(R.id.readingImg)
        val helpButton =
            findViewById<FloatingActionButton>(R.id.fabEducationDashboard)
        readingCardView.setOnClickListener(this)
        readImg.setOnClickListener(this)
        syncCardview.setOnClickListener(this)
        syncImg.setOnClickListener(this)
        patientCardView.setOnClickListener(this)
        patientImg.setOnClickListener(this)
        statCardview.setOnClickListener(this)
        statImg.setOnClickListener(this)
        helpButton.setOnClickListener(this)
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
                startActivityForResult(intent, READING_ACTIVITY_DONE)
            }
            R.id.patientCardview, R.id.patientImg -> startActivity(PatientsActivity.makeIntent(this))
            R.id.syncCardView, R.id.syncImg -> startActivity(Intent(this, SyncActivity::class.java))
            R.id.fabEducationDashboard -> startActivity(Intent(this, EducationActivity::class.java))
            R.id.statCardView, R.id.statImg -> startActivity(
                Intent(
                    this,
                    StatsActivity::class.java
                )
            )
        }
    }

    companion object {
        const val READING_ACTIVITY_DONE = 12345
        const val OPACITY_HALF = 0.5f
        const val OPACITY_FULL = 1.0f
    }

    override fun onBackPressed() {
    }
}
