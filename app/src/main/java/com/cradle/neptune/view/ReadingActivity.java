package com.cradle.neptune.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Patient.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.utilitiles.GsonUtil;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.view.ui.reading.BaseFragment;
import com.cradle.neptune.view.ui.reading.MyFragmentInteractionListener;
import com.cradle.neptune.view.ui.reading.SectionsPagerAdapter;

import org.threeten.bp.ZonedDateTime;

import javax.inject.Inject;

public class ReadingActivity
        extends AppCompatActivity
        implements MyFragmentInteractionListener {
    private static final String EXTRA_LAUNCH_REASON = "enum of why we launched";
    private static final String EXTRA_READING_ID = "ID of reading to load";
    private static final String EXTRA_START_TAB = "idx of tab to start on";
    // Data Model
    @Inject
    ReadingManager readingManager;
    private ViewPager mPager;
    private SectionsPagerAdapter mPagerAdapter;
    // Reading object shared by all fragments:
    private LaunchReason reasonForLaunch = LaunchReason.LAUNCH_REASON_NONE;
    private Reading originalReading;
    private Reading currentReading;
    private int lastKnownTab = -1;

    static public Intent makeIntentForNewReading(Context context) {
        Intent intent = new Intent(context, ReadingActivity.class);
        intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_NEW);
        return intent;
    }

    static public Intent makeIntentForEdit(Context context, long readingId) {
        Intent intent = new Intent(context, ReadingActivity.class);
        intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_EDIT);
        intent.putExtra(EXTRA_READING_ID, readingId);
        intent.putExtra(EXTRA_START_TAB, SectionsPagerAdapter.TAB_NUMBER_SUMMARY);
        return intent;
    }

    public static Intent makeIntentForRecheck(Context context, long readingId) {
        Intent intent = new Intent(context, ReadingActivity.class);
        intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_RECHECK);
        intent.putExtra(EXTRA_READING_ID, readingId);
        intent.putExtra(EXTRA_START_TAB, SectionsPagerAdapter.TAB_NUMBER_CAMERA);
        return intent;
    }

    public static Intent makeIntentForNewReadingExistingPatient(Context context, long readingID) {
        Intent intent = new Intent(context, ReadingActivity.class);
        intent.putExtra(EXTRA_LAUNCH_REASON, LaunchReason.LAUNCH_REASON_EXISTINGNEW);
        intent.putExtra(EXTRA_READING_ID, readingID);
        intent.putExtra(EXTRA_START_TAB, SectionsPagerAdapter.TAB_NUMBER_SYMPTOMS);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // inject:
        ((MyApp) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading);

        setupModelData();

        setupTabs();
        setupToolBar();
        setupBottomBar();
    }

    private void setupModelData() {
        Intent intent = getIntent();

        // why did we launch this activity?
        Util.ensure(intent.hasExtra(EXTRA_LAUNCH_REASON));
        reasonForLaunch = (LaunchReason) intent.getSerializableExtra(EXTRA_LAUNCH_REASON);

        long readingId = 0;
        switch (reasonForLaunch) {
            case LAUNCH_REASON_NEW:
                originalReading = null;
                currentReading = Reading.makeNewReading(ZonedDateTime.now());
                currentReading.dateTimeTaken = ZonedDateTime.now();
                break;
            case LAUNCH_REASON_EDIT:
                readingId = getIntent().getLongExtra(EXTRA_READING_ID, -1);
                Util.ensure(readingId >= 0);
                originalReading = readingManager.getReadingById(this, readingId);
                currentReading = GsonUtil.cloneViaGson(originalReading, Reading.class);
                break;
            case LAUNCH_REASON_RECHECK:
                readingId = getIntent().getLongExtra(EXTRA_READING_ID, -1);
                Util.ensure(readingId >= 0);
                originalReading = readingManager.getReadingById(this, readingId);
                currentReading = Reading.makeToConfirmReading(originalReading, ZonedDateTime.now());
                break;
            case LAUNCH_REASON_EXISTINGNEW:
                readingId = getIntent().getLongExtra(EXTRA_READING_ID, -1);
                Util.ensure(readingId >= 0);
                originalReading = readingManager.getReadingById(this, readingId);
                currentReading = Reading.makeNewExistingPatientReading(originalReading, ZonedDateTime.now());
                break;
            default:
                Util.ensure(false);
        }
    }

    /*
        Tabs
     */
    private void setupTabs() {
        // configure tabs
        mPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        mPager = findViewById(R.id.view_pager);
        mPager.setAdapter(mPagerAdapter);
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(mPager);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);

        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int i) {
                callOnMyBeingHiddenForCurrentTab();

                BaseFragment nextFragment = (BaseFragment) mPagerAdapter.getItem(i);
                nextFragment.onMyBeingDisplayed();
                lastKnownTab = i;
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }

            @Override
            public void onPageScrolled(int i, float v, int i1) {
            }
        });

        // set tab to start on
        // jump through other tabs to ensure change listener is called on first display.
        int startTab = getIntent().getIntExtra(EXTRA_START_TAB, 0);
        mPager.setCurrentItem(0);
        mPager.setCurrentItem(mPagerAdapter.getCount());
        mPager.setCurrentItem(startTab);
    }

    private void callOnMyBeingHiddenForCurrentTab() {
        if (lastKnownTab != -1) {
            BaseFragment lastFragment = (BaseFragment) mPagerAdapter.getItem(lastKnownTab);
            lastFragment.onMyBeingHidden();
        }
    }

    /*
        Top Toolbar & Back Button
     */
    private void setupToolBar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.baseline_close_white_36dp);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_reading, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                confirmCancel();
                return true;

            case R.id.action_save:
                if (saveCurrentReading()) {
                    finish();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        confirmCancel();
    }

    private void confirmCancel() {
        callOnMyBeingHiddenForCurrentTab();

        switch (reasonForLaunch) {
            case LAUNCH_REASON_NEW:
                confirmDiscardAndFinish(R.string.discard_dialog_new_reading);
                break;
            case LAUNCH_REASON_EDIT:
                if (GsonUtil.identicalContentViaGson(originalReading, currentReading)) {
                    finish();
                } else {
                    confirmDiscardAndFinish(R.string.discard_dialog_changes);
                }
                break;
            case LAUNCH_REASON_RECHECK:
                confirmDiscardAndFinish(R.string.discard_dialog_rechecking);
                break;
            case LAUNCH_REASON_EXISTINGNEW:
                confirmDiscardAndFinish(R.string.discard_dialog_new_reading);
                break;
            default:
                Util.ensure(false);
        }
    }

    private void displayMissingDataDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.missing_info_title)
                .setMessage(R.string.missing_info_body)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, null);
        dialog.show();
    }

    private void confirmDiscardAndFinish(int messageId) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setMessage(messageId)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.discard_dialog_discard, (dlg, btn) -> finish())
                .setNegativeButton(R.string.discard_dialog_cancel, null);
        dialog.show();
    }

    /*
        Bottom Bar
     */
    private void setupBottomBar() {
        // Attach the page change listener to update when tab switches page
        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            // This method will be invoked when a new page becomes selected.
            @Override
            public void onPageSelected(int position) {
                updateBottomBar();
            }

            // This method will be invoked when the current page is scrolled
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            // Called when the scroll state changes:
            // SCROLL_STATE_IDLE, SCROLL_STATE_DRAGGING, SCROLL_STATE_SETTLING
            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        // Clicks
        findViewById(R.id.ivPrevious).setOnClickListener(this::onClickPrevious);
        findViewById(R.id.ivNext).setOnClickListener(this::onClickNext);
        findViewById(R.id.txtNext).setOnClickListener(this::onClickNext);
        findViewById(R.id.txtDone).setOnClickListener(view -> {
            if (saveCurrentReading()) {
                finish();
            }
        });

        updateBottomBar();
    }

    private void onClickPrevious(View v) {
        // If user taps PREVIOUS faster than UI can update, we can have multiple
        // clicks queued up and incorrectly want to go past first page.
        if (mPager.getCurrentItem() > 0) {
            mPager.setCurrentItem(mPager.getCurrentItem() - 1);

        }
    }

    private void onClickNext(View v) {
        // If user taps NEXT faster than UI can update, we can have multiple
        // clicks queued up and incorrectly want to go past last page.
        if (mPager.getCurrentItem() < mPagerAdapter.getCount() - 1) {
            mPager.setCurrentItem(mPager.getCurrentItem() + 1);
        }
    }

    private void updateBottomBar() {
        int position = mPager.getCurrentItem();
        int lastPosition = mPagerAdapter.getCount() - 1;

        // No previous on first:
        findViewById(R.id.ivPrevious).setVisibility(position > 0 ? View.VISIBLE : View.INVISIBLE);

        // No next on last:
        findViewById(R.id.ivNext).setVisibility(position < lastPosition ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.txtNext).setVisibility(position < lastPosition ? View.VISIBLE : View.INVISIBLE);

        // Bottom 'save' button only on last:
        findViewById(R.id.txtDone).setVisibility(position == lastPosition ? View.VISIBLE : View.INVISIBLE);

    }

    /*
        Callback from Fragments
    */
    @Override
    public Reading getCurrentReading() {
        return currentReading;
    }

    @Override
    public ReadingManager getReadingManager() {
        return readingManager;
    }

    // Return true if saved; false if rejected
    @Override
    public boolean saveCurrentReading() {
        // called from:
        // - activity's SAVE button(s)
        // - fragment's saving data as needed (send SMS)

        callOnMyBeingHiddenForCurrentTab();

        if (currentReading.isMissingRequiredData()) {
            displayMissingDataDialog();
            return false;
        }

        switch (reasonForLaunch) {
            case LAUNCH_REASON_NEW:
                readingManager.addNewReading(this, currentReading);
                break;
            case LAUNCH_REASON_EDIT:
                // overwrite if any changes
                if (!GsonUtil.identicalContentViaGson(originalReading, currentReading)) {
                    readingManager.updateReading(this, currentReading);
                }
                break;
            case LAUNCH_REASON_RECHECK:
                readingManager.addNewReading(this, currentReading);
                // database clears all dataRecheckVitalsNeeded flags as needed
                break;
            case LAUNCH_REASON_EXISTINGNEW:
                readingManager.addNewReading(this, currentReading);
                break;
            default:
                Util.ensure(false);
        }

        // prep for continuing to work with data after save
        // after SMS is sent we save.
        reasonForLaunch = LaunchReason.LAUNCH_REASON_EDIT;
        originalReading = GsonUtil.cloneViaGson(currentReading, Reading.class);

        return true;
    }

    @Override
    public void advanceToNextPage() {
        onClickNext(null);
    }

    private enum LaunchReason {
        LAUNCH_REASON_NEW,
        LAUNCH_REASON_EDIT,
        LAUNCH_REASON_RECHECK,
        LAUNCH_REASON_NONE,
        LAUNCH_REASON_EXISTINGNEW
    }
}
