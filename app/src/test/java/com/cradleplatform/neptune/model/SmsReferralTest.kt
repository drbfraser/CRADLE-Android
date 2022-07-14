package com.cradleplatform.neptune.model

import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SmsReferralTest {
    @Test
    fun `create sms reading with referral and write it and then parse it back `() {

        // Changing this will affect other tests
        val patientAndReadings = deepCopyPatientAndReadings(
            CommonPatientReadingJsons.patientWithGestAgeJsonAndExpected.second
        )
        val firstReading = patientAndReadings.readings.first()
        firstReading.referral = Referral(
            comment = "this is a comment",
            referralHealthFacilityName = "H23234",
            dateReferred = 16456665L,
            patientId = "400003232",
            isAssessed = false,
            actionTaken = null,
            cancelReason = null,
            isCancelled = false,
            lastEdited = 0L,
            notAttendReason = null,
            notAttended = false,
            userId = null
        )


        val referralId = UUID.randomUUID().toString()

        val smsReferral = SmsReadingWithReferral(referralId, patientAndReadings)

        val writer = JacksonMapper.createWriter<SmsReadingWithReferral>()
        val json = writer.writeValueAsString(smsReferral)

        val reader = JacksonMapper.createReader<SmsReadingWithReferral>()
        val parsedSmsReferral = reader.readValue<SmsReadingWithReferral>(json)
        assertEquals(smsReferral, parsedSmsReferral)
    }

    private fun deepCopyPatientAndReadings(patientAndReadings: PatientAndReadings): PatientAndReadings {
        val bytes = JacksonMapper.createWriter<PatientAndReadings>()
            .writeValueAsBytes(patientAndReadings)
        return JacksonMapper.readerForPatientAndReadings.readValue(bytes)
    }

    @Test
    fun `create sms referral and write it and then parse it back `() {

        // Changing this will affect other tests
        val patientAndReferrals = deepCopyPatientAndReferrals(
            CommonPatientReferralJsons.patientWithStandaloneReferral.second
        )

        val referralId = UUID.randomUUID().toString()

        val smsReferral = SmsReferral(referralId, patientAndReferrals)

        val writer = JacksonMapper.createWriter<SmsReferral>()
        val json = writer.writeValueAsString(smsReferral)

        val reader = JacksonMapper.createReader<SmsReferral>()
        val parsedSmsReferral = reader.readValue<SmsReferral>(json)
        assertEquals(smsReferral, parsedSmsReferral)
    }

    private fun deepCopyPatientAndReferrals(patientAndReferrals: PatientAndReferrals): PatientAndReferrals {
        val bytes = JacksonMapper.createWriter<PatientAndReferrals>()
            .writeValueAsBytes(patientAndReferrals)
        return JacksonMapper.readerForPatientAndReferrals.readValue(bytes)
    }
}