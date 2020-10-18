package com.cradle.neptune.dagger

import com.cradle.neptune.sync.SyncStepperImplementation
import com.cradle.neptune.view.GlobalPatientProfileActivity
import com.cradle.neptune.view.GlobalPatientSearchActivity
import com.cradle.neptune.view.IntroActivity
import com.cradle.neptune.view.LoginActivity
import com.cradle.neptune.view.PatientProfileActivity
import com.cradle.neptune.view.PatientsActivity
import com.cradle.neptune.view.ReadingActivity
import com.cradle.neptune.view.StatsActivity
import com.cradle.neptune.view.SyncActivity
import com.cradle.neptune.view.VideoActivity
import com.cradle.neptune.view.ui.reading.BaseFragment
import com.cradle.neptune.view.ui.reading.ReferralDialogFragment
import com.cradle.neptune.view.ui.reading.VitalSignsFragment
import com.cradle.neptune.view.ui.settings.AdvancedSettingsFragment
import com.cradle.neptune.view.ui.settings.SettingsFragment
import com.cradle.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity
import com.cradle.neptune.viewmodel.HealthFacilityViewModel
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import dagger.Component
import dagger.android.AndroidInjectionModule
import javax.inject.Singleton

/**
 * Injector support for dagger
 * source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Singleton
@Component(modules = [
    AndroidInjectionModule::class,
    AppModule::class,
    DataModule::class,
    FragmentModule::class,
    ViewModelModule::class])
interface AppComponent {
    fun inject(activity: ReadingActivity?)
    fun inject(fragment: BaseFragment?)
    fun inject(activity: PatientsActivity?)
    fun inject(activity: VideoActivity?)
    fun inject(fragment: ReferralDialogFragment?)
    fun inject(fragment: SettingsFragment?)
    fun inject(fragment: AdvancedSettingsFragment?)
    fun inject(activity: IntroActivity?)
    fun inject(activity: VitalSignsFragment?)
    fun inject(activity: PatientProfileActivity?)
    fun inject(globalPatientProfileActivity: GlobalPatientProfileActivity?)
    fun inject(statsActivity: StatsActivity?)
    fun inject(loginActivity: LoginActivity?)
    fun inject(healthFacilitiesActivity: HealthFacilitiesActivity?)
    fun inject(globalPatientSearchActivity: GlobalPatientSearchActivity?)
    fun inject(healthFacilityViewModel: HealthFacilityViewModel?)
    fun inject(patientReadingViewModel: PatientReadingViewModel?)
    fun inject(syncStepperImplementation: SyncStepperImplementation?)
    fun inject(syncActivity: SyncActivity)
    fun inject(myApp: MyApp)
}
