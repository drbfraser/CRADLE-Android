package com.cradle.neptune.view.ui.intro;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import androidx.core.app.Fragment;
import androidx.core.app.FragmentManager;
import androidx.core.app.FragmentPagerAdapter;

import com.cradle.neptune.R;
import com.cradle.neptune.utilitiles.Util;

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
public class IntroSectionsPagerAdapter extends FragmentPagerAdapter {
    public static final int TAB_NUMBER_WELCOME = 0;
    public static final int TAB_NUMBER_PERMISSIONS = 1;
    public static final int TAB_NUMBER_PRIVACY_POLICY = 2;

    @StringRes
    private static final int[] TAB_TITLES = new int[]{
            R.string.intro_tab_welcome,
            R.string.intro_tab_permissions,
            R.string.intro_tab_privacy_policy
    };
    private final Context mContext;
    private Fragment[] fragments = new Fragment[TAB_TITLES.length];

    public IntroSectionsPagerAdapter(Context context, FragmentManager fm) {
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
                case TAB_NUMBER_WELCOME:
                    thisFragment = WelcomeFragment.newInstance();
                    break;
                case TAB_NUMBER_PERMISSIONS:
                    thisFragment = PermissionsFragment.newInstance();
                    break;
                case TAB_NUMBER_PRIVACY_POLICY:
                    thisFragment = PrivacyPolicyFragment.newInstance();
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
        // Show # accessible pages.
        return TAB_TITLES.length;
    }
}
