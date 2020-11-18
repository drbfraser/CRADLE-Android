package com.cradleVSA.neptune.utilitiles.jackson

import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.PatientAndReadings
import com.cradleVSA.neptune.model.Reading
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object JacksonMapper {
    /**
     * Stores one mapper to use for the entire app.
     * https://stackoverflow.com/questions/3907929/
     * should-i-declare-jacksons-objectmapper-as-a-static-field#comment26559628_16197551
     */
    private val mapper by lazy {
        jacksonObjectMapper()
    }

    val readerForStringList: ObjectReader by lazy { mapper.readerForListOf(String::class.java) }

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
}
