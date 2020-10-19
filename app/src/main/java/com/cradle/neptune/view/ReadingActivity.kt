package com.cradle.neptune.view

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.cradle.neptune.R
import com.cradle.neptune.databinding.ActivityPlaceholderBinding
import com.cradle.neptune.utilitiles.dismissKeyboard
import com.cradle.neptune.view.ui.reading.PatientIdConflictDialogFragment
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import com.cradle.neptune.viewmodel.PatientReadingViewModelFactory
import com.cradle.neptune.viewmodel.ReadingFlowError
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressWarnings("LargeClass")
@AndroidEntryPoint
class ReadingActivity : AppCompatActivity() {
    private lateinit var launchReason: LaunchReason

    private var nextButtonJob: Job? = null

    private var binding: ActivityPlaceholderBinding? = null

    @Inject
    lateinit var viewModelFactory: PatientReadingViewModelFactory

    // ViewModel shared by all Fragments.
    private val viewModel: PatientReadingViewModel by viewModels {
        viewModelFactory
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ReadingActivity", "onDestroy()")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Use activity_reading when done design
        binding = DataBindingUtil.setContentView(this, R.layout.activity_placeholder)
        binding?.viewModel = viewModel
        binding?.lifecycleOwner = this

        check(intent.hasExtra(EXTRA_LAUNCH_REASON))

        launchReason = intent.getSerializableExtra(EXTRA_LAUNCH_REASON) as LaunchReason
        viewModel.initialize(
            launchReason = launchReason,
            readingId = intent.getStringExtra(EXTRA_READING_ID),
            patientId = intent.getStringExtra(EXTRA_PATIENT_ID)
        )

        val toolbar = findViewById<Toolbar>(R.id.toolbar3)
        setSupportActionBar(toolbar)

        val host: NavHostFragment = supportFragmentManager
            .findFragmentById(R.id.reading_nav_host) as NavHostFragment? ?: return
        val navController = host.navController

        // TODO: remove this when done
        // adapted from https://github.com/googlecodelabs/android-navigation
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val dest: String = try {
                resources.getResourceName(destination.id)
            } catch (e: Resources.NotFoundException) {
                destination.id.toString()
            }
            Log.d("ReadingActivity", "Navigated to $dest")

            viewModel.onDestinationChange(destination.id)

            val bottomNavBar = findViewById<ConstraintLayout>(R.id.nav_button_bottom_layout)
            if (destination.id == R.id.cameraFragment) {
                supportActionBar?.apply {
                    if (isShowing) {
                        hide()
                    }
                }
                (bottomNavBar?.getViewById(R.id.reading_next_button) as? Button)?.apply {
                    if (visibility != View.GONE) {
                        visibility = View.GONE
                    }
                }
            } else {
                supportActionBar?.apply {
                    if (!isShowing) {
                        show()
                    }
                }
                (bottomNavBar?.getViewById(R.id.reading_next_button) as? Button)?.apply {
                    if (visibility != View.VISIBLE) {
                        visibility = View.VISIBLE
                    }
                }
            }
        }

        viewModel.actionBarTitleAndSubtitle.observe(this@ReadingActivity) { titlePair ->
            supportActionBar?.apply {
                title = titlePair.first
                subtitle = titlePair.second
            }
        }

        viewModel.isInitialized.observe(this) {
            if (!it) {
                return@observe
            }
            viewModel.isInitialized.removeObservers(this)

            if (navController.currentDestination?.id == R.id.loadingFragment) {
                val actionId = when (getStartDestinationId()) {
                    R.id.patientInfoFragment -> R.id.action_loadingFragment_to_patientInfoFragment
                    R.id.symptomsFragment -> R.id.action_loadingFragment_to_symptomsFragment
                    else -> error("unsupported navigation action right now")
                }
                // force fading animations to appear
                val navOptions = navOptions {
                    anim {
                        enter = R.anim.fade_in
                        exit = R.anim.fade_out
                        popEnter = R.anim.fade_in
                        popExit = R.anim.fade_out
                    }
                }
                navController.navigate(actionId, null, navOptions)
            }
        }

        findViewById<Button>(R.id.reading_next_button).setOnClickListener {
            onNextButtonClicked(it)
        }

        onBackPressedDispatcher.addCallback(this) { onBackButtonPressed() }
        findViewById<Button>(R.id.reading_back_button).setOnClickListener { onBackButtonPressed() }
    }

    private fun onNextButtonClicked(button: View) {
        button.dismissKeyboard()
        val navController = findNavController(R.id.reading_nav_host)

        nextButtonJob?.cancel()
        nextButtonJob = lifecycleScope.launch {
            val (error, patient) = viewModel.onNextButtonClicked(
                navController.currentDestination?.id ?: return@launch
            )
            viewModel.clearBottomNavBarMessage()

            when (error) {
                ReadingFlowError.NO_ERROR -> onNextButtonClickedWithNoErrors(navController)
                ReadingFlowError.ERROR_PATIENT_ID_IN_USE_LOCAL -> {
                    check(patient != null)

                    PatientIdConflictDialogFragment.makeInstance(
                        isPatientLocal = true, patient = patient
                    ).run {
                        show(supportFragmentManager, PATIENT_ID_IN_USE_DIALOG_FRAGMENT_TAG)
                    }
                }
                ReadingFlowError.ERROR_PATIENT_ID_IN_USE_ON_SERVER -> {
                    viewModel.clearBottomNavBarMessage()
                    check(patient != null)

                    PatientIdConflictDialogFragment.makeInstance(
                        isPatientLocal = false, patient = patient
                    ).run {
                        show(supportFragmentManager, PATIENT_ID_IN_USE_DIALOG_FRAGMENT_TAG)
                    }
                }
                ReadingFlowError.ERROR_INVALID_FIELDS -> {
                    Toast.makeText(
                        this@ReadingActivity,
                        R.string.reading_activity_errors_left_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.setInputEnabledState(true)
                }
            }
        }
    }

    private fun onNextButtonClickedWithNoErrors(navController: NavController) {
        when (navController.currentDestination?.id) {
            R.id.loadingFragment, null -> return
            R.id.patientInfoFragment -> {
                navController.navigate(R.id.action_patientInfoFragment_to_symptomsFragment)
            }
            R.id.symptomsFragment -> {
                navController.navigate(R.id.action_symptomsFragment_to_vitalSignsFragment)
            }
            R.id.vitalSignsFragment -> {
                Toast.makeText(this, "TODO: not supported yet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onBackButtonPressed() {
        findViewById<Button>(R.id.reading_back_button)?.dismissKeyboard()

        val navController = findNavController(R.id.reading_nav_host)

        if (navController.currentDestination?.id == getStartDestinationId()) {
            launchDiscardChangesDialogIfNeeded()
        } else {
            navController.navigateUp()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        launchDiscardChangesDialogIfNeeded()
        return false
    }

    private fun launchDiscardChangesDialogIfNeeded() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getDiscardTitleId())
            .setNegativeButton(android.R.string.cancel) { _, _ -> /* noop */ }
            .setPositiveButton(R.string.discard_dialog_discard) { _, _ ->
                findNavController(R.id.reading_nav_host).popBackStack(R.id.loadingFragment, true)
                finish()
            }
            .show()
    }

    @IdRes
    private fun getStartDestinationId(): Int = when (launchReason) {
        LaunchReason.LAUNCH_REASON_NEW -> {
            R.id.patientInfoFragment
        }
        LaunchReason.LAUNCH_REASON_RECHECK, LaunchReason.LAUNCH_REASON_EXISTINGNEW,
        LaunchReason.LAUNCH_REASON_EDIT_READING -> {
            R.id.symptomsFragment
        }
        else -> error("need a launch reason to be in ReadingActivity")
    }

    @StringRes
    private fun getDiscardTitleId(): Int = when (launchReason) {
        LaunchReason.LAUNCH_REASON_NEW, LaunchReason.LAUNCH_REASON_EXISTINGNEW -> {
            R.string.discard_dialog_new_reading
        }
        LaunchReason.LAUNCH_REASON_EDIT_READING -> R.string.discard_dialog_changes
        LaunchReason.LAUNCH_REASON_RECHECK -> R.string.discard_dialog_rechecking
        else -> error("need a launch reason to be in ReadingActivity")
    }

    fun downloadPatientAndReadingsFromServer(patientId: String) {
        Toast.makeText(
            this,
            R.string.reading_activity_downloading_patient_toast,
            Toast.LENGTH_SHORT
        ).show()
        val downloadLiveData = viewModel.downloadPatientFromServer(patientId)
        downloadLiveData.observe(this) {
            val newPatient = it.getOrElse {
                // TODO: downloading and associating needs to be refactored into the patient manager
                viewModel.setInputEnabledState(true)
                viewModel.clearBottomNavBarMessage()
                downloadLiveData.removeObservers(this)
                Toast.makeText(
                    this,
                    R.string.reading_activity_downloading_patient_failed_toast,
                    Toast.LENGTH_SHORT
                ).show()
                return@observe
            }

            Toast.makeText(
                this,
                R.string.reading_activity_downloading_patient_successful_toast,
                Toast.LENGTH_SHORT
            ).show()

            val newReadingIntent = makeIntentForNewReadingExistingPatient(
                context = this,
                patientId = newPatient.id
            )

            startActivity(newReadingIntent)
            finish()
        }
    }

    enum class LaunchReason {
        LAUNCH_REASON_NEW, LAUNCH_REASON_EDIT_READING, LAUNCH_REASON_RECHECK, LAUNCH_REASON_NONE,
        LAUNCH_REASON_EXISTINGNEW
    }

    companion object {
        private const val EXTRA_LAUNCH_REASON = "enum of why we launched"
        private const val EXTRA_READING_ID = "ID of reading to load"
        private const val EXTRA_PATIENT_ID = "patient_id"

        private const val PATIENT_ID_IN_USE_DIALOG_FRAGMENT_TAG = "patientIdInUse"

        @JvmStatic
        fun makeIntentForNewReading(context: Context?): Intent {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_NEW)
            return intent
        }

        @JvmStatic
        fun makeIntentForEditReading(context: Context?, readingId: String?): Intent {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_EDIT_READING)
            intent.putExtra(EXTRA_READING_ID, readingId)
            return intent
        }

        @JvmStatic
        fun makeIntentForRecheck(context: Context?, readingId: String?): Intent {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_RECHECK)
            intent.putExtra(EXTRA_READING_ID, readingId)
            return intent
        }

        @JvmStatic
        fun makeIntentForNewReadingExistingPatient(
            context: Context?,
            patientId: String
        ): Intent =
            Intent(context, ReadingActivity::class.java).apply {
                putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_EXISTINGNEW)
                putExtra(EXTRA_PATIENT_ID, patientId)
            }
    }
}
