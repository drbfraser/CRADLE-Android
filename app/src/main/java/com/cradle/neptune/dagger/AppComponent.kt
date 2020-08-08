package com.cradle.neptune.dagger

import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.view.GlobalPatientProfileActivity
import com.cradle.neptune.view.GlobalPatientSearchActivity
import com.cradle.neptune.view.VideoActivity
import com.cradle.neptune.view.IntroActivity
import com.cradle.neptune.view.LoginActivity
import com.cradle.neptune.view.PatientProfileActivity
import com.cradle.neptune.view.PatientsActivity
import com.cradle.neptune.view.ReadingActivity
import com.cradle.neptune.view.StatsActivity
import com.cradle.neptune.sync.ListUploader
import com.cradle.neptune.view.SyncActivity
import com.cradle.neptune.sync.SyncStepperImplementation
import com.cradle.neptune.view.ui.reading.ConfirmDataFragment
import com.cradle.neptune.view.ui.reading.ReferralDialogFragment
import com.cradle.neptune.view.ui.settings.AdvancedSettingsFragment
import com.cradle.neptune.view.ui.settings.SettingsFragment
import com.cradle.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity
import com.cradle.neptune.viewmodel.HealthFacilityViewModel
import dagger.Component
import javax.inject.Singleton

/**
 * Injector support for dagger
 * source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Singleton
@Component(modules = [AppModule::class, DataModule::class])
interface AppComponent {
    fun inject(activity: ReadingActivity?)
    fun inject(activity: PatientsActivity?)
    fun inject(activity: VideoActivity?)
    fun inject(fragment: ReferralDialogFragment?)
    fun inject(fragment: SettingsFragment?)
    fun inject(fragment: AdvancedSettingsFragment?)
    fun inject(activity: IntroActivity?)
    fun inject(activity: ConfirmDataFragment?)
    fun inject(activity: PatientProfileActivity?)
    fun inject(globalPatientProfileActivity: GlobalPatientProfileActivity?)
    fun inject(statsActivity: StatsActivity?)
    fun inject(loginActivity: LoginActivity?)
    fun inject(healthFacilitiesActivity: HealthFacilitiesActivity?)
    fun inject(globalPatientSearchActivity: GlobalPatientSearchActivity?)
    fun inject(healthFacilityViewModel: HealthFacilityViewModel?)
    fun inject(volleyRequestManager: VolleyRequestManager?)
    fun inject(listUploader: ListUploader?)
    fun inject(syncStepperImplementation: SyncStepperImplementation?)
    fun inject(syncActivity: SyncActivity)
    fun inject(myApp: MyApp)
}