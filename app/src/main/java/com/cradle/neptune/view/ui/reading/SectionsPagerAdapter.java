package com.cradle.neptune.view.ui.reading;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.cradle.neptune.R;
import com.cradle.neptune.utilitiles.Util;

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class SectionsPagerAdapter extends FragmentPagerAdapter {
    public static final int TAB_NUMBER_PATIENT = 0;
    public static final int TAB_NUMBER_SYMPTOMS = 1;
    public static final int TAB_NUMBER_CAMERA = 2;
    public static final int TAB_NUMBER_CONFIRM_DATA = 3;
    public static final int TAB_NUMBER_SUMMARY = 4;

    @StringRes
    private static final int[] TAB_TITLES = new int[]{
            R.string.reading_tab_patient_info,
            R.string.reading_tab_symptoms,
            R.string.reading_tab_camera,
            R.string.reading_tab_confirm_data,
            R.string.reading_tab_summary
    };
    private final Context mContext;
    private Fragment[] fragments = new Fragment[TAB_TITLES.length];

    public SectionsPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        mContext = context;
    }

    @Override
    public Fragment getItem(int position) {
        // cache created fragments so we can call their onMyBeingDisplayed() and onMyBeingHidden() methods.
        Fragment thisFragment = fragments[position];

        if (thisFragment == null) {
            // getItem is called to instantiate the fragment for the given page.
            // Return instance of required fragments
            switch (position) {
                case TAB_NUMBER_PATIENT:
                    thisFragment = PatientInfoFragment.newInstance();
                    break;
                case TAB_NUMBER_SYMPTOMS:
                    thisFragment = SymptomsFragment.newInstance();
                    break;
                case TAB_NUMBER_CAMERA:
                    thisFragment = CameraFragment.newInstance();
                    break;
                case TAB_NUMBER_CONFIRM_DATA:
                    thisFragment = ConfirmDataFragment.newInstance();
                    break;
                case TAB_NUMBER_SUMMARY:
                    thisFragment = SummaryFragment.newInstance();
                    break;
                default:
                    Util.ensure(false);
                    return null;
            }

            fragments[position] = thisFragment;
        }

        return thisFragment;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return mContext.getResources().getString(TAB_TITLES[position]);
    }

    @Override
    public int getCount() {
        // Show # total pages.
        return TAB_TITLES.length;
    }


}
