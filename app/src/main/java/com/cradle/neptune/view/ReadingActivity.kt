package com.cradle.neptune.view

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager.widget.ViewPager
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.utilitiles.Util
import com.cradle.neptune.view.ui.reading.BaseFragment
import com.cradle.neptune.view.ui.reading.MyFragmentInteractionListener
import com.cradle.neptune.view.ui.reading.SectionsPagerAdapter
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import com.google.android.material.tabs.TabLayout
import java.util.ArrayList
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.threeten.bp.ZonedDateTime

@SuppressWarnings("LargeClass")
class ReadingActivity : AppCompatActivity(), MyFragmentInteractionListener {
    // Data Model
    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var patientManager: PatientManager
    private lateinit var mPager: ViewPager
    private lateinit var mPagerAdapter: SectionsPagerAdapter

    // Reading object shared by all fragments:
    private var reasonForLaunch = LaunchReason.LAUNCH_REASON_NONE
    private lateinit var patient: Patient
    private lateinit var reading: Reading
    private val viewModel: PatientReadingViewModel by viewModels()

    private var lastKnownTab = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        // inject:
        (application as MyApp).appComponent.inject(this)
        (application as MyApp).appComponent.inject(viewModel)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading)
        setupModelData()
        setupTabs()
        setupToolBar()
        setupBottomBar()
    }

    private fun setupModelData() = runBlocking {
        // Need to block until coroutine is done, or else ViewModel can be uninitialized when
        // not creating a new patient
        // TODO: Remove this workaround when the Fragments are rearchitected
        // TODO: Move this setup code into the ViewModel. This involves a migration of all the other
        //       code, including the code for saving readings. What should result in the end is that
        //       ReadingActivity has a minimum amount of code that deals with the model/data.
        Log.d("ReadingActivity", "setupModelData")
        val intent = intent

        // why did we launch this activity?
        Util.ensure(intent.hasExtra(EXTRA_LAUNCH_REASON))
        reasonForLaunch = intent.getSerializableExtra(EXTRA_LAUNCH_REASON) as LaunchReason

        if (reasonForLaunch == LaunchReason.LAUNCH_REASON_NEW) {
            viewModel.isInitialized = true
            return@runBlocking
        }

        // Get the intended patient and reading
        val readingId: String = intent.getStringExtra(EXTRA_READING_ID) ?: ""
        Util.ensure(readingId != "")
        launch {
            withContext(Dispatchers.IO) {
                // We expect that the reading and the patient is there, since ReadingActivity has
                // to be launched for not creating a new patient + reading at this point.
                reading = readingManager.getReadingById(readingId)
                    ?: throw AssertionError("no reading associated with given id")
                patient = patientManager.getPatientById(reading.patientId)
                    ?: throw AssertionError("no patient associated with given reading")
            }

            when (reasonForLaunch) {
                LaunchReason.LAUNCH_REASON_EDIT -> {
                    viewModel.decompose(patient, reading)
                }
                LaunchReason.LAUNCH_REASON_RECHECK -> {
                    viewModel.decompose(patient)

                    // Add the old reading to the previous list of the new reading.
                    if (viewModel.previousReadingIds == null) {
                        viewModel.previousReadingIds = ArrayList()
                    }
                    viewModel.previousReadingIds?.add(reading.id)
                }
                LaunchReason.LAUNCH_REASON_EXISTINGNEW -> {
                    viewModel.decompose(patient)
                }
                else -> Util.ensure(false)
            }
            viewModel.isInitialized = true
        }
    }

    /*
        Tabs
     */
    private fun setupTabs() {
        // configure tabs
        mPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        mPager = findViewById(R.id.view_pager)
        mPager.adapter = mPagerAdapter
        val tabs = findViewById<TabLayout>(R.id.tabs)
        tabs.setupWithViewPager(mPager)
        tabs.tabMode = TabLayout.MODE_SCROLLABLE
        mPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageSelected(i: Int) {
                callOnMyBeingHiddenForCurrentTab()
                val nextFragment = mPagerAdapter.getItem(i) as BaseFragment
                nextFragment.onMyBeingDisplayed()
                lastKnownTab = i
            }

            @SuppressWarnings("EmptyFunctionBlock")
            override fun onPageScrollStateChanged(i: Int) {}

            @SuppressWarnings("EmptyFunctionBlock")
            override fun onPageScrolled(i: Int, v: Float, i1: Int) {}
        })

        // set tab to start on
        // jump through other tabs to ensure change listener is called on first display.
        val startTab = intent.getIntExtra(EXTRA_START_TAB, 0)
        mPager.apply {
            currentItem = 0
            currentItem = mPagerAdapter.count
            currentItem = startTab
        }
    }

    private fun callOnMyBeingHiddenForCurrentTab() {
        if (lastKnownTab != -1) {
            val lastFragment = mPagerAdapter.getItem(lastKnownTab) as BaseFragment
            lastFragment.onMyBeingHidden()
        }
    }

    /*
        Top Toolbar & Back Button
     */
    private fun setupToolBar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.baseline_close_white_36dp)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_reading, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                confirmCancel()
                return true
            }
            R.id.action_save -> {
                if (saveCurrentReading()) {
                    finish()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        confirmCancel()
    }

    private fun confirmCancel() {
        callOnMyBeingHiddenForCurrentTab()
        when (reasonForLaunch) {
            LaunchReason.LAUNCH_REASON_NEW -> confirmDiscardAndFinish(R.string.discard_dialog_new_reading)
            LaunchReason.LAUNCH_REASON_EDIT -> {
                if (reading == viewModel.maybeConstructModels()?.second) {
                    finish()
                } else {
                    confirmDiscardAndFinish(R.string.discard_dialog_changes)
                }
            }
            LaunchReason.LAUNCH_REASON_RECHECK -> confirmDiscardAndFinish(R.string.discard_dialog_rechecking)
            LaunchReason.LAUNCH_REASON_EXISTINGNEW -> confirmDiscardAndFinish(R.string.discard_dialog_new_reading)
            else -> Util.ensure(false)
        }
    }

    private fun displayMissingDataDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.missing_info_title)
            .setMessage(R.string.missing_info_body)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, null)
        dialog.show()
    }

    private fun confirmDiscardAndFinish(messageId: Int) {
        val dialog = AlertDialog.Builder(this)
            .setMessage(messageId)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.discard_dialog_discard) { _: DialogInterface?, _: Int -> finish() }
            .setNegativeButton(R.string.discard_dialog_cancel, null)
        dialog.show()
    }

    private fun displayValidDataDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.invalid_data)
            .setMessage(R.string.invalid_data_message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, null)
        dialog.show()
    }

    /*
        Bottom Bar
     */
    private fun setupBottomBar() {
        // Attach the page change listener to update when tab switches page
        mPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            // This method will be invoked when a new page becomes selected.
            override fun onPageSelected(position: Int) {
                updateBottomBar()
            }

            // This method will be invoked when the current page is scrolled
            @SuppressWarnings("EmptyFunctionBlock")
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {}

            // Called when the scroll state changes:
            // SCROLL_STATE_IDLE, SCROLL_STATE_DRAGGING, SCROLL_STATE_SETTLING
            @SuppressWarnings("EmptyFunctionBlock")
            override fun onPageScrollStateChanged(state: Int) {}
        })

        // Clicks
        findViewById<View>(R.id.ivPrevious).setOnClickListener { onClickPrevious() }
        findViewById<View>(R.id.ivNext).setOnClickListener { onClickNext() }
        findViewById<View>(R.id.txtNext).setOnClickListener { onClickNext() }
        findViewById<View>(R.id.txtDone).setOnClickListener {
            if (saveCurrentReading()) {
                finish()
            }
        }
        updateBottomBar()
    }

    private fun onClickPrevious() {
        // If user taps PREVIOUS faster than UI can update, we can have multiple
        // clicks queued up and incorrectly want to go past first page.
        if (mPager.currentItem > 0) {
            mPager.currentItem = mPager.currentItem - 1
        }
    }

    private fun onClickNext() {
        // If user taps NEXT faster than UI can update, we can have multiple
        // clicks queued up and incorrectly want to go past last page.
        if (mPager.currentItem < mPagerAdapter.count - 1) {
            mPager.currentItem = mPager.currentItem + 1
        }
    }

    private fun updateBottomBar() {
        val position = mPager.currentItem
        val lastPosition = mPagerAdapter.count - 1

        // No previous on first:
        findViewById<View>(R.id.ivPrevious).visibility =
            if (position > 0) View.VISIBLE else View.INVISIBLE

        // No next on last:
        findViewById<View>(R.id.ivNext).visibility =
            if (position < lastPosition) View.VISIBLE else View.INVISIBLE
        findViewById<View>(R.id.txtNext).visibility =
            if (position < lastPosition) View.VISIBLE else View.INVISIBLE

        // Bottom 'save' button only on last:
        findViewById<View>(R.id.txtDone).visibility =
            if (position == lastPosition) View.VISIBLE else View.INVISIBLE
    }

    // Return true if saved; false if rejected
    // TODO: Move this into the ViewModel
    override fun saveCurrentReading(): Boolean {
        // called from:
        // - activity's SAVE button(s)
        // - fragment's saving data as needed (send SMS)
        callOnMyBeingHiddenForCurrentTab()
        if (viewModel.isMissingAnything(this)) {
            displayMissingDataDialog()
            return false
        }

        // check for valid data
        if (viewModel.isDataInvalid) {
            displayValidDataDialog()
            Log.i("validation", "Data out of range")
            return false
        }

        // If dateTimeTaken has not been filled in for the reading, for example
        // in the case of creating a new reading instead of updating an
        // existing one, we set it to the current time.
        if (viewModel.dateTimeTaken == null) {
            viewModel.dateTimeTaken = ZonedDateTime.now().toEpochSecond()
        }
        val models = viewModel.constructModels()
        when (reasonForLaunch) {
            LaunchReason.LAUNCH_REASON_NEW, LaunchReason.LAUNCH_REASON_RECHECK,
            LaunchReason.LAUNCH_REASON_EXISTINGNEW -> {
                readingManager.addReading(models.second)
                patientManager.add(models.first)
            }
            LaunchReason.LAUNCH_REASON_EDIT ->
                // overwrite if any changes
                if (models.second != reading) {
                    readingManager.updateReading(models.second)
                    patientManager.add(models.first)
                }
            else -> Util.ensure(false)
        }

        // prep for continuing to work with data after save
        // after SMS is sent we save.
        reasonForLaunch = LaunchReason.LAUNCH_REASON_EDIT
        reading = models.second
        patient = models.first
        return true
    }

    override fun advanceToNextPage() {
        onClickNext()
    }

    override fun finishActivity() {
        finish()
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
            intent.putExtra(EXTRA_START_TAB, SectionsPagerAdapter.TAB_NUMBER_SUMMARY)
            return intent
        }

        @JvmStatic
        fun makeIntentForRecheck(context: Context?, readingId: String?): Intent {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_RECHECK)
            intent.putExtra(EXTRA_READING_ID, readingId)
            intent.putExtra(EXTRA_START_TAB, SectionsPagerAdapter.TAB_NUMBER_SYMPTOMS)
            return intent
        }

        @JvmStatic
        fun makeIntentForNewReadingExistingPatient(context: Context?, readingID: String?): Intent {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_EXISTINGNEW)
            intent.putExtra(EXTRA_READING_ID, readingID)
            intent.putExtra(EXTRA_START_TAB, SectionsPagerAdapter.TAB_NUMBER_SYMPTOMS)
            return intent
        }
    }
}
