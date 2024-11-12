package com.cradleplatform.neptune.activities.introduction

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.cradleplatform.neptune.BuildConfig
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.utilities.Util
import com.cradleplatform.neptune.activities.dashboard.DashBoardActivity
import com.cradleplatform.neptune.fragments.introduction.IntroBaseFragment
import com.cradleplatform.neptune.adapters.introduction.IntroSectionsPagerAdapter
import com.cradleplatform.neptune.custom.introduction.MyIntroFragmentInteractionListener
import com.cradleplatform.neptune.fragments.introduction.PermissionsFragment.Companion.areAllPermissionsGranted
import com.cradleplatform.neptune.custom.introduction.SwipeControlViewPager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IntroActivity : AppCompatActivity(), MyIntroFragmentInteractionListener {
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    private lateinit var mPager: ViewPager
    private var mPagerAdapter: IntroSectionsPagerAdapter? = null

    // Reading object shared by all fragments:
    private var lastKnownTab = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)
        setupTabs()
        setupBottomBar()

        // disable swipe
        val swipePager = findViewById<SwipeControlViewPager>(R.id.view_pager)
        swipePager.isSwipeEnabled = false

        // skip intro entirely?
        val verCodeCompleted = sharedPreferences.getLong(LAST_VERSION_TO_COMPLETE_WIZARD, -1)
        if (verCodeCompleted >= 0 && areAllPermissionsGranted(this)) {
            closeWizard()
        }
    }

    /*
        Tabs
     */
    private fun setupTabs() {
        // configure tabs
        mPagerAdapter = IntroSectionsPagerAdapter(this, supportFragmentManager)
        mPager = findViewById(R.id.view_pager)
        mPager.apply {
            adapter = mPagerAdapter
            addOnPageChangeListener(
                object : ViewPager.OnPageChangeListener {
                    override fun onPageSelected(i: Int) {
                        callOnMyBeingHiddenForCurrentTab()
                        val nextFragment = mPagerAdapter!!.getItem(i) as IntroBaseFragment
                        nextFragment.onMyBeingDisplayed()
                        lastKnownTab = i
                    }

                    @SuppressWarnings("EmptyFunctionBlock")
                    override fun onPageScrollStateChanged(i: Int) {}

                    @SuppressWarnings("EmptyFunctionBlock")
                    override fun onPageScrolled(i: Int, v: Float, i1: Int) {}
                }
            )
            // set tab to start on
            // jump through other tabs to ensure change listener is called on first display.
            currentItem = mPagerAdapter!!.count
            currentItem = 0
        }
        setNextButtonEnabled(true)
    }

    private fun callOnMyBeingHiddenForCurrentTab() {
        if (lastKnownTab != -1) {
            val lastFragment = mPagerAdapter!!.getItem(lastKnownTab) as IntroBaseFragment
            lastFragment.onMyBeingHidden()
        }
        setNextButtonEnabled(true)
    }

    /*
        Bottom Bar
     */
    private fun setupBottomBar() {
        // Attach the page change listener to update when tab switches page
        mPager.addOnPageChangeListener(
            object : ViewPager.OnPageChangeListener {
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
            }
        )

        // Clicks
        findViewById<View>(R.id.ivPrevious).setOnClickListener { onClickPrevious() }
        findViewById<View>(R.id.ivNext).setOnClickListener { onClickNext() }
        findViewById<View>(R.id.txtNext).setOnClickListener { onClickNext() }
        findViewById<View>(R.id.txtDone).setOnClickListener { closeWizard() }
        updateBottomBar()
    }

    private fun closeWizard() {
        // record done
        val verCode = BuildConfig.VERSION_CODE.toLong()
        sharedPreferences.edit().putLong(LAST_VERSION_TO_COMPLETE_WIZARD, verCode).apply()

        // Intent intent = ReadingsListActivity.makeIntent(IntroActivity.this);
        val intent = Intent(this, DashBoardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun onClickPrevious() {
        Util.ensure(mPager.currentItem > 0)
        mPager.currentItem = mPager.currentItem - 1
    }

    private fun onClickNext() {
        Util.ensure(mPager.currentItem < mPagerAdapter!!.count - 1)
        mPager.currentItem = mPager.currentItem + 1
    }

    private fun updateBottomBar() {
        val position = mPager.currentItem
        val lastPosition = mPagerAdapter!!.count - 1

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

    /*
        Permissions Support
        Relay to fragments
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val i = mPager.currentItem
        mPagerAdapter!!.getItem(i)
            .onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /*
        Callback from Fragments
    */
    override fun advanceToNextPage() {
        onClickNext()
    }

    override fun setNextButtonEnabled(enabled: Boolean) {
        findViewById<View>(R.id.ivNext).apply {
            isEnabled = enabled
            @Suppress("MagicNumber")
            alpha = if (enabled) 1.0f else 0.5f
        }
        findViewById<View>(R.id.txtNext).isEnabled = enabled
    }

    companion object {
        // Shared Pref for this activity
        const val LAST_VERSION_TO_COMPLETE_WIZARD = "last ver complete intro"
    }
}
