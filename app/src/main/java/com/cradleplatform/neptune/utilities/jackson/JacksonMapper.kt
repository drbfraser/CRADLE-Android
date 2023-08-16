package com.cradleplatform.neptune.utilities.jackson

import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.GestationalAge
import com.cradleplatform.neptune.model.GlobalPatient
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReadings
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.RelayPhoneNumberResponse
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal object JacksonMapper {
    /**
     * Stores one mapper to use for the entire app.
     * https://stackoverflow.com/questions/3907929/
     * should-i-declare-jacksons-objectmapper-as-a-static-field#comment26559628_16197551
     */
    val mapper by lazy {
        jacksonObjectMapper()
    }

    inline fun <reified T> createReader(): ObjectReader = mapper.readerFor(T::class.java)

    inline fun <reified T> createWriter(): ObjectWriter = mapper.writerFor(T::class.java)

    val readerForPatientAndReadings: ObjectReader by lazy {
        mapper.readerFor(PatientAndReadings::class.java)
    }

    val readerForPatientAndReferrals: ObjectReader by lazy {
        mapper.readerFor(PatientAndReferrals::class.java)
    }

    val readerForHealthFacility: ObjectReader by lazy {
        mapper.readerFor(HealthFacility::class.java)
    }

    val readerForReading: ObjectReader by lazy { mapper.readerFor(Reading::class.java) }

    val writerForReading: ObjectWriter by lazy { mapper.writerFor(Reading::class.java) }

    val readerForPatient: ObjectReader by lazy { mapper.readerFor(Patient::class.java) }

    val writerForPatient: ObjectWriter by lazy { mapper.writerFor(Patient::class.java) }

    val readerForm: ObjectReader by lazy { mapper.readerFor(FormTemplate::class.java) }

    val writerForm: ObjectWriter by lazy { mapper.writerFor(FormTemplate::class.java) }

    val readerForGestAge: ObjectReader by lazy { mapper.readerFor(GestationalAge::class.java) }

    val writerForGestAge: ObjectWriter by lazy { mapper.writerFor(GestationalAge::class.java) }

    val readerForReferral: ObjectReader by lazy { mapper.readerFor(Referral::class.java) }

    val writerForReferral: ObjectWriter by lazy { mapper.writerFor(Referral::class.java) }

    val readerForAssessment: ObjectReader by lazy { mapper.readerFor(Assessment::class.java) }

    val writerForAssessment: ObjectWriter by lazy { mapper.writerFor(Assessment::class.java) }

    val readerForRelayPhoneNumberResponse: ObjectReader by lazy {
        mapper.readerFor(RelayPhoneNumberResponse::class.java)
    }

    fun createGlobalPatientsListReader(): ObjectReader =
        mapper.readerForListOf(GlobalPatient::class.java)
}
