package com.cradle.neptune.view.ui.reading

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.cradle.neptune.R
import com.cradle.neptune.utilitiles.Util

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class SectionsPagerAdapter(
    private val mContext: Context,
    fm: FragmentManager?
) : FragmentPagerAdapter(fm!!) {
    private val fragments =
        arrayOfNulls<Fragment>(TAB_TITLES.size)

    override fun getItem(position: Int): Fragment {
        // cache created fragments so we can call their onMyBeingDisplayed() and onMyBeingHidden() methods.
        var thisFragment = fragments[position]
        if (thisFragment == null) {
            // getItem is called to instantiate the fragment for the given page.
            // Return instance of required fragments
            thisFragment = when (position) {
                TAB_NUMBER_PATIENT -> PatientInfoFragment()
                TAB_NUMBER_SYMPTOMS -> SymptomsFragment.newInstance()
                TAB_NUMBER_CAMERA -> CameraFragment.newInstance()
                TAB_NUMBER_CONFIRM_DATA -> ConfirmDataFragment.newInstance()
                TAB_NUMBER_SUMMARY -> SummaryFragment.newInstance()
                else -> {
                    Util.ensure(false)
                    return PatientInfoFragment()
                }
            }
            fragments[position] = thisFragment
        }
        return thisFragment!!
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return mContext.resources
            .getString(TAB_TITLES[position])
    }

    override fun getCount(): Int {
        // Show # total pages.
        return TAB_TITLES.size
    }

    companion object {
        const val TAB_NUMBER_PATIENT = 0
        const val TAB_NUMBER_SYMPTOMS = 1
        const val TAB_NUMBER_CAMERA = 2
        const val TAB_NUMBER_CONFIRM_DATA = 3
        const val TAB_NUMBER_SUMMARY = 4

        @StringRes
        private val TAB_TITLES = intArrayOf(
            R.string.reading_tab_patient_info,
            R.string.reading_tab_symptoms,
            R.string.reading_tab_camera,
            R.string.reading_tab_confirm_data,
            R.string.reading_tab_summary
        )
    }
}