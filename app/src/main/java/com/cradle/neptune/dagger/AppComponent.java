package com.cradle.neptune.dagger;

import com.cradle.neptune.view.HelpActivity;
import com.cradle.neptune.view.IntroActivity;
import com.cradle.neptune.view.LoginActivity;
import com.cradle.neptune.view.PatientProfileActivity;
import com.cradle.neptune.view.PatientsActivity;
import com.cradle.neptune.view.ReadingActivity;
import com.cradle.neptune.view.StatsActivity;
import com.cradle.neptune.view.UploadActivity;
import com.cradle.neptune.view.ui.network_volley.MultiReadingUploader;
import com.cradle.neptune.view.ui.reading.ConfirmDataFragment;
import com.cradle.neptune.view.ui.reading.ReferralDialogFragment;
import com.cradle.neptune.view.ui.settings.AdvancedSettingsFragment;
import com.cradle.neptune.view.ui.settings.SettingsFragment;
import com.cradle.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity;

import javax.inject.Singleton;

import dagger.Component;

/**
 * Injector support for dagger
 * source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Singleton
@Component(modules = {AppModule.class, DataModule.class})
public interface AppComponent {

    void inject(ReadingActivity activity);

    void inject(PatientsActivity activity);

    void inject(UploadActivity activity);

    void inject(HelpActivity activity);

    void inject(ReferralDialogFragment fragment);

    void inject(SettingsFragment fragment);

    void inject(AdvancedSettingsFragment fragment);

    void inject(IntroActivity activity);

    void inject(ConfirmDataFragment activity);

    void inject(PatientProfileActivity activity);

    void inject(StatsActivity statsActivity);

    void inject(LoginActivity loginActivity);

    void inject(MultiReadingUploader multiReadingUploader);

    void inject(HealthFacilitiesActivity healthFacilitiesActivity);
}