package com.cradle.neptune.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import com.cradle.neptune.BuildConfig;
import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.view.ui.intro.IntroBaseFragment;
import com.cradle.neptune.view.ui.intro.IntroSectionsPagerAdapter;
import com.cradle.neptune.view.ui.intro.MyIntroFragmentInteractionListener;
import com.cradle.neptune.view.ui.intro.PermissionsFragment;
import com.cradle.neptune.view.ui.intro.SwipeControlViewPager;

import javax.inject.Inject;


public class IntroActivity
        extends AppCompatActivity
        implements MyIntroFragmentInteractionListener {
    // Shared Pref for this activity
    private static final String LAST_VERSION_TO_COMPLETE_WIZARD = "last ver complete intro";
    @Inject
    SharedPreferences sharedPreferences;
    private ViewPager mPager;
    private IntroSectionsPagerAdapter mPagerAdapter;
    // Reading object shared by all fragments:
    private int lastKnownTab = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // inject:
        ((MyApp) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        setupTabs();
        setupBottomBar();

        // disable swipe
        SwipeControlViewPager swipePager = findViewById(R.id.view_pager);
        swipePager.setSwipeEnabled(false);

        // skip intro entirely?
        long verCodeCompleted = sharedPreferences.getLong(LAST_VERSION_TO_COMPLETE_WIZARD, -1);
        if (verCodeCompleted >= 0 && PermissionsFragment.areAllPermissionsGranted(this)) {
            closeWizard();
        }
    }


    /*
        Tabs
     */
    private void setupTabs() {
        // configure tabs
        mPagerAdapter = new IntroSectionsPagerAdapter(this, getSupportFragmentManager());
        mPager = findViewById(R.id.view_pager);
        mPager.setAdapter(mPagerAdapter);
//        TabLayout tabs = findViewById(R.id.tabs);
//        tabs.setupWithViewPager(mPager);
//        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);

        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int i) {
                callOnMyBeingHiddenForCurrentTab();

                IntroBaseFragment nextFragment = (IntroBaseFragment) mPagerAdapter.getItem(i);
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
        mPager.setCurrentItem(mPagerAdapter.getCount());
        mPager.setCurrentItem(0);

        setNextButtonEnabled(true);
    }

    private void callOnMyBeingHiddenForCurrentTab() {
        if (lastKnownTab != -1) {
            IntroBaseFragment lastFragment = (IntroBaseFragment) mPagerAdapter.getItem(lastKnownTab);
            lastFragment.onMyBeingHidden();
        }

        setNextButtonEnabled(true);
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
        findViewById(R.id.txtDone).setOnClickListener(view -> closeWizard());

        updateBottomBar();
    }

    private void closeWizard() {
        // record done
        long verCode = BuildConfig.VERSION_CODE;
        sharedPreferences.edit().putLong(LAST_VERSION_TO_COMPLETE_WIZARD, verCode).apply();


        //Intent intent = ReadingsListActivity.makeIntent(IntroActivity.this);
        Intent intent = new Intent(this, DashBoardActivity.class);
        startActivity(intent);
        finish();
    }

    private void onClickPrevious(View v) {
        Util.ensure(mPager.getCurrentItem() > 0);
        mPager.setCurrentItem(mPager.getCurrentItem() - 1);
    }

    private void onClickNext(View v) {
        Util.ensure(mPager.getCurrentItem() < mPagerAdapter.getCount() - 1);
        mPager.setCurrentItem(mPager.getCurrentItem() + 1);
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
        Permissions Support
        Relay to fragments
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        int i = mPager.getCurrentItem();
        mPagerAdapter.getItem(i).onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /*
        Callback from Fragments
    */
    @Override
    public void advanceToNextPage() {
        onClickNext(null);
    }

    @Override
    public void setNextButtonEnabled(boolean enabled) {
        findViewById(R.id.ivNext).setEnabled(enabled);
        findViewById(R.id.txtNext).setEnabled(enabled);

    }
}