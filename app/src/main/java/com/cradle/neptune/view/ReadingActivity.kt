package com.cradle.neptune.view

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.databinding.ActivityPlaceholderBinding
import com.cradle.neptune.view.ui.reading.BaseFragment
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import com.cradle.neptune.viewmodel.PatientReadingViewModelFactory
import com.cradle.neptune.viewmodel.ReadingFlowError
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressWarnings("LargeClass")
class ReadingActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration

    private var buttonJob: Job? = null

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

        onBackPressedDispatcher.addCallback(this) { onSupportNavigateUp() }

        // TODO: Use activity_reading when done design
        binding = DataBindingUtil.setContentView(this, R.layout.activity_placeholder)
        binding?.viewModel = viewModel
        binding?.lifecycleOwner = this

        val toolbar = findViewById<Toolbar>(R.id.toolbar3)
        setSupportActionBar(toolbar)

        val host: NavHostFragment = supportFragmentManager
            .findFragmentById(R.id.reading_nav_host) as NavHostFragment? ?: return
        val navController = host.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // TODO: remove this when done
        // adapted from https://github.com/googlecodelabs/android-navigation
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val dest: String = try {
                resources.getResourceName(destination.id)
            } catch (e: Resources.NotFoundException) {
                destination.id.toString()
            }
            Log.d("ReadingActivity", "Navigated to $dest")

            viewModel.updateNextButtonCriteriaBasedOnDestination(currentDestinationId = destination.id)
        }

        check(intent.hasExtra(EXTRA_LAUNCH_REASON))
        viewModel.initialize(
            launchReason = intent.getSerializableExtra(EXTRA_LAUNCH_REASON) as LaunchReason,
            readingId = intent.getStringExtra(EXTRA_READING_ID)
        )

        viewModel.isInitialized.observe(this) {
            if (!it) {
                return@observe
            }
            viewModel.isInitialized.removeObservers(this)

            if (navController.currentDestination?.id == R.id.loadingFragment) {
                navController.navigate(R.id.action_loadingFragment_to_patientInfoFragment)
            }
        }

        findViewById<Button>(R.id.next_button2).setOnClickListener {
            buttonJob?.cancel()
            buttonJob = lifecycleScope.launch {
                val error: ReadingFlowError = viewModel.onNextButtonClicked(
                    navController.currentDestination?.id ?: return@launch
                )

                when (error) {
                    ReadingFlowError.NO_ERROR -> onNextButtonClickedWithNoErrors(navController)
                    ReadingFlowError.ERROR_PATIENT_ID_IN_USE -> {
                        Toast.makeText(
                            this@ReadingActivity,
                            "This ID is already in use",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    ReadingFlowError.ERROR_INVALID_FIELDS -> return@launch
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

    override fun onSupportNavigateUp(): Boolean {
        Toast.makeText(this, "onSupportNavigateUp", Toast.LENGTH_SHORT).show()
        val navController = findNavController(R.id.reading_nav_host)

        return if (navController.currentDestination?.id == R.id.patientInfoFragment) {
            // TODO: add dialog
            finish()
            true
        } else {
            navController.navigateUp(appBarConfiguration)
        }
    }

    enum class LaunchReason {
        LAUNCH_REASON_NEW, LAUNCH_REASON_EDIT, LAUNCH_REASON_RECHECK, LAUNCH_REASON_NONE,
        LAUNCH_REASON_EXISTINGNEW
    }

    companion object {
        private const val EXTRA_LAUNCH_REASON = "enum of why we launched"
        private const val EXTRA_READING_ID = "ID of reading to load"
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
