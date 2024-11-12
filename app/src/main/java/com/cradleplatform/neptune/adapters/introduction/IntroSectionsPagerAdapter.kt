package com.cradleplatform.neptune.adapters.introduction

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.utilities.Util
import com.cradleplatform.neptune.fragments.introduction.PermissionsFragment
import com.cradleplatform.neptune.fragments.introduction.PrivacyPolicyFragment

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class IntroSectionsPagerAdapter(
    private val mContext: Context,
    fm: FragmentManager?
) : FragmentPagerAdapter(fm!!) {
    private val fragments =
        arrayOfNulls<Fragment>(tabTitles.size)

    override fun getItem(position: Int): Fragment {
        // cache created fragments so we can call their onMyBeingDisplayed() and onMyBeingHidden() methods.
        var thisFragment = fragments[position]
        if (thisFragment == null) {
            // getItem is called to instantiate the fragment for the given page.
            // Return instance of required fragments
            thisFragment = when (position) {
                TAB_NUMBER_PERMISSIONS -> PermissionsFragment.newInstance()
                TAB_NUMBER_PRIVACY_POLICY -> PrivacyPolicyFragment.newInstance()
                else -> {
                    Util.ensure(false)
                    PermissionsFragment.newInstance()
                }
            }
            fragments[position] = thisFragment
        }
        return thisFragment!!
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return mContext.resources
            .getString(tabTitles[position])
    }

    override fun getCount(): Int {
        // Show # accessible pages.
        return tabTitles.size
    }

    companion object {
        const val TAB_NUMBER_PERMISSIONS = 0
        const val TAB_NUMBER_PRIVACY_POLICY = 1

        @StringRes
        private val tabTitles = intArrayOf(
            R.string.intro_tab_permissions,
            R.string.intro_tab_privacy_policy
        )
    }
}
