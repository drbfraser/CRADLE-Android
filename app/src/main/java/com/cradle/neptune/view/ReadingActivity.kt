package com.cradle.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.view.ui.reading.PatientInfoFragment
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import com.cradle.neptune.viewmodel.PatientReadingViewModelFactory
import javax.inject.Inject

@SuppressWarnings("LargeClass")
class ReadingActivity : AppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: PatientReadingViewModelFactory

    // ViewModel shared by all Fragments.
    private val viewModel: PatientReadingViewModel by viewModels() {
        viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as MyApp).appComponent.inject(this)
        super.onCreate(savedInstanceState)
        // TODO: Use activity_reading when done design
        setContentView(R.layout.activity_placeholder)

        // TODO: Remove this when navigation is added back
        if (savedInstanceState == null) {
            val fragment = PatientInfoFragment()
            supportFragmentManager
                .beginTransaction()
                .add(R.id.container_placeholder, fragment)
                .commit()
        }
    }

    private enum class LaunchReason {
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
