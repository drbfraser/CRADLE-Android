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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.databinding.ActivityPlaceholderBinding
import com.cradle.neptune.view.ui.reading.BaseFragment
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import com.cradle.neptune.viewmodel.PatientReadingViewModelFactory
import com.cradle.neptune.viewmodel.ReadingFlowError
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressWarnings("LargeClass")
class ReadingActivity : AppCompatActivity() {
    private lateinit var launchReason: LaunchReason

    private var nextButtonJob: Job? = null

    private var binding: ActivityPlaceholderBinding? = null

    @Inject
    lateinit var viewModelFactory: PatientReadingViewModelFactory

    // ViewModel shared by all Fragments.
    private val viewModel: PatientReadingViewModel by viewModels() {
        viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Adapted from https://github.com/android/architecture-components-samples/blob/
        //     7686abc4bba087c8ee02f0ac569093bf304245e6/GithubBrowserSample/app/src/main/java/com/
        //     android/example/github/di/AppInjector.kt
        (application as MyApp).appComponent.inject(this)
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    savedInstanceState: Bundle?
                ) {
                    if (f is BaseFragment) {
                        (application as MyApp).appComponent.inject(f)
                    }
                }
            },
            true /* recursive */
        )

        super.onCreate(savedInstanceState)
        // TODO: Use activity_reading when done design
        binding = DataBindingUtil.setContentView(this, R.layout.activity_placeholder)
        binding?.viewModel = viewModel
        binding?.lifecycleOwner = this

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

            viewModel.updateNextButtonCriteriaBasedOnDestination(destination.id)

            val bottomNavBar = findViewById<ConstraintLayout>(R.id.nav_button_bottom_layout)
            if (destination.id == R.id.cameraFragment) {
                supportActionBar?.apply {
                    if (isShowing) {
                        hide()
                    }
                }
                (bottomNavBar?.getViewById(R.id.next_button2) as? Button)?.apply {
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
                (bottomNavBar?.getViewById(R.id.next_button2) as? Button)?.apply {
                    if (visibility != View.VISIBLE) {
                        visibility = View.VISIBLE
                    }
                }
            }
        }

        check(intent.hasExtra(EXTRA_LAUNCH_REASON))

        launchReason = intent.getSerializableExtra(EXTRA_LAUNCH_REASON) as LaunchReason
        viewModel.initialize(
            launchReason = launchReason,
            readingId = intent.getStringExtra(EXTRA_READING_ID)
        )
        supportActionBar?.apply {
            title = getActionBarTitle()
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

        findViewById<Button>(R.id.next_button2).setOnClickListener {
            nextButtonJob?.cancel()
            nextButtonJob = lifecycleScope.launch {
                val error: ReadingFlowError = viewModel.onNextButtonClicked(
                    navController.currentDestination?.id ?: return@launch
                )

                when (error) {
                    ReadingFlowError.NO_ERROR -> onNextButtonClickedWithNoErrors(navController)
                    // TODO: Handle this better. Maybe have another Fragment or some dialog that
                    //  pops up that does the validation. Of course, only show the dialog for the
                    //  network checks if the user has internet.
                    ReadingFlowError.ERROR_PATIENT_ID_IN_USE_LOCAL -> {
                        Toast.makeText(
                            this@ReadingActivity,
                            "This ID is already in use",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    ReadingFlowError.ERROR_PATIENT_ID_IN_USE_ON_SERVER -> {
                        Toast.makeText(
                            this@ReadingActivity,
                            "This ID is already in use on the server",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    ReadingFlowError.ERROR_INVALID_FIELDS -> {
                        Toast.makeText(
                            this@ReadingActivity,
                            "There are still errors left to correct!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) { onBackButtonPressed() }
        findViewById<Button>(R.id.back_button).setOnClickListener { onBackButtonPressed() }
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
            LaunchReason.LAUNCH_REASON_NEW, LaunchReason.LAUNCH_REASON_EDIT -> {
                R.id.patientInfoFragment
            }
            LaunchReason.LAUNCH_REASON_RECHECK, LaunchReason.LAUNCH_REASON_EXISTINGNEW -> {
                R.id.symptomsFragment
            }
            else -> error("need a launch reason to be in ReadingActivity")
        }

    @StringRes

    private fun getDiscardTitleId(): Int = when (launchReason) {
        LaunchReason.LAUNCH_REASON_NEW, LaunchReason.LAUNCH_REASON_EXISTINGNEW -> {
            R.string.discard_dialog_new_reading
        }
        LaunchReason.LAUNCH_REASON_EDIT -> R.string.discard_dialog_changes
        LaunchReason.LAUNCH_REASON_RECHECK -> R.string.discard_dialog_rechecking
        else -> error("need a launch reason to be in ReadingActivity")
    }

    private fun getActionBarTitle(): String = when (launchReason) {
        LaunchReason.LAUNCH_REASON_NEW -> getString(R.string.reading_flow_title_new_patient)
        LaunchReason.LAUNCH_REASON_EDIT -> getString(R.string.reading_flow_title_editing_reading)
        LaunchReason.LAUNCH_REASON_EXISTINGNEW -> {
            getString(R.string.reading_flow_title_creating_new_reading_for_existing_patient)
        }
        LaunchReason.LAUNCH_REASON_RECHECK -> getString(R.string.reading_flow_title_recheck_vitals)
        else -> error("need a launch reason to be in ReadingActivity")
    }

    enum class LaunchReason {
        LAUNCH_REASON_NEW, LAUNCH_REASON_EDIT, LAUNCH_REASON_RECHECK, LAUNCH_REASON_NONE,
        LAUNCH_REASON_EXISTINGNEW
    }

    companion object {
        private const val EXTRA_LAUNCH_REASON = "enum of why we launched"
        private const val EXTRA_READING_ID = "ID of reading to load"
        // TODO: remove this legacy extra
        private const val EXTRA_START_TAB = "idx of tab to start on"

        @JvmStatic
        fun makeIntentForNewReading(context: Context?): Intent {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_NEW)
            return intent
        }

        @JvmStatic
        fun makeIntentForEdit(context: Context?, readingId: String?): Intent {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_EDIT)
            intent.putExtra(EXTRA_READING_ID, readingId)
            intent.putExtra(EXTRA_START_TAB, 0)
            return intent
        }

        @JvmStatic
        fun makeIntentForRecheck(context: Context?, readingId: String?): Intent {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_RECHECK)
            intent.putExtra(EXTRA_READING_ID, readingId)
            intent.putExtra(EXTRA_START_TAB, 0)
            return intent
        }

        @JvmStatic
        fun makeIntentForNewReadingExistingPatient(context: Context?, readingID: String?): Intent {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_EXISTINGNEW)
            intent.putExtra(EXTRA_READING_ID, readingID)
            intent.putExtra(EXTRA_START_TAB, 0)
            // TODO("Do tabs indexing")
            return intent
        }
    }
}
