package com.cradletrial.cradlevhtapp.dagger;

import com.cradletrial.cradlevhtapp.view.HelpActivity;
import com.cradletrial.cradlevhtapp.view.IntroActivity;
import com.cradletrial.cradlevhtapp.view.PatientProfileActivity;
import com.cradletrial.cradlevhtapp.view.PatientsActivity;
import com.cradletrial.cradlevhtapp.view.ReadingActivity;
import com.cradletrial.cradlevhtapp.view.ReadingsListActivity;
import com.cradletrial.cradlevhtapp.view.UploadActivity;
import com.cradletrial.cradlevhtapp.view.ui.reading.ConfirmDataFragment;
import com.cradletrial.cradlevhtapp.view.ui.reading.ReferralDialogFragment;
import com.cradletrial.cradlevhtapp.view.ui.settings.SettingsFragment;
import com.cradletrial.cradlevhtapp.view.ui.settings.ui.settingnamedpairs.SettingNamedPairsFragment;

import javax.inject.Singleton;

import dagger.Component;

/**
 * Injector support for dagger
 * source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Singleton
@Component(modules={AppModule.class, DataModule.class})
public interface AppComponent {
    void inject(ReadingsListActivity activity);
    void inject(ReadingActivity activity);
    void inject(PatientsActivity activity);
    void inject(UploadActivity activity);
    void inject(HelpActivity activity);
    void inject(ReferralDialogFragment fragment);
    void inject(SettingsFragment fragment);
    void inject(SettingNamedPairsFragment fragment);
    void inject(IntroActivity activity);
    void inject(ConfirmDataFragment activity);
    void inject(PatientProfileActivity activity);

    // void inject(MyFragment fragment);
    // void inject(MyService service);
}