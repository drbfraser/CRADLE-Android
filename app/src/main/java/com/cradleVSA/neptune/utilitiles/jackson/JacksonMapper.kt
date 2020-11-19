package com.cradleVSA.neptune.utilitiles.jackson

import androidx.annotation.VisibleForTesting
import com.cradleVSA.neptune.model.GlobalPatient
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.PatientAndReadings
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.model.SyncUpdate
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object JacksonMapper {
    /**
     * Stores one mapper to use for the entire app.
     * https://stackoverflow.com/questions/3907929/
     * should-i-declare-jacksons-objectmapper-as-a-static-field#comment26559628_16197551
     *
     * Should not use this directly.
     */
    @PublishedApi
    @VisibleForTesting
    internal val mapper by lazy {
        jacksonObjectMapper()
    }

    inline fun <reified T> createReader(): ObjectReader = mapper.readerFor(T::class.java)

    inline fun <reified T> createWriter(): ObjectWriter = mapper.writerFor(T::class.java)

    val readerForSyncUpdate: ObjectReader by lazy { mapper.readerFor(SyncUpdate::class.java) }

    val readerForPatientAndReadings: ObjectReader by lazy {
        mapper.readerFor(PatientAndReadings::class.java)
    }

    val readerForHealthFacility: ObjectReader by lazy {
        mapper.readerFor(HealthFacility::class.java)
    }

    val readerForReading: ObjectReader by lazy { mapper.readerFor(Reading::class.java) }

    val writerForReading: ObjectWriter by lazy { mapper.writerFor(Reading::class.java) }

    val readerForPatient: ObjectReader by lazy { mapper.readerFor(Patient::class.java) }

    val writerForPatient: ObjectWriter by lazy { mapper.writerFor(Patient::class.java) }

    fun createGlobalPatientsListReader(): ObjectReader =
        mapper.readerForListOf(GlobalPatient::class.java)
}
