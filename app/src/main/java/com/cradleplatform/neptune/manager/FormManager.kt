package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.net.RestApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager to submit the form with user's answers.
 */
@Singleton
class FormManager @Inject constructor(
    private val mRestApi: RestApi
) {
    suspend fun putFormTemplate(form: FormTemplate?) {
        form?.run {
            val result = mRestApi.putFormTemplate(form)
            //Use (result is NetworkResult.Success) to check if success
        }
    }
}
