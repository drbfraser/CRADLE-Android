package com.cradleplatform.neptune.model

import java.math.BigInteger

object CommonPatientReferralJsons {
    /**
     * A patient with a standalone referral
     *
     * first: A JSONObject string of the [PatientAndReferrals] object
     * second: The corresponding expected [PatientAndReferrals] object.
     */
    val patientWithStandaloneReferral = Pair(
        first = """
{
    "patientName": "Test patient",
    "patientSex": "FEMALE",
    "medicalHistory": "Some med history.",
    "allergy": "Seasonal allergies",
    "zone": "634",
    "isExactDateOfBirth": true,
    "householdNumber": "95682385",
    "lastEdited": 1604883600,
    "isPregnant": true,
    "id": "3459834789348",
    "pregnancyStartDate": 1590969549,
    "drugHistory": "Some drug history",
    "dateOfBirth": "2002-01-08",
    "villageNumber": "133",
    "created": 1604883600,
    "base": 1604883600,
    "referrals": [
        {
            "id": "42286a4a-f3be-4875-8b0f-3955ef65ec30",
            "comment": "Hello World!",
            "healthFacilityName": "H0000",
            "dateReferred": 1605753210,
            "userId": 1,
            "patientId": "123456",
            "isAssessed": false,
            "isCancelled": false,
            "lastEdited": 1605753210,
            "notAttended": false 
        }
    ]
}
    """.trimIndent(),
        second = PatientAndReferrals(
            Patient(
                isPregnant = true,
                name = "Test patient",
                id = "3459834789348",
                gestationalAge = GestationalAgeMonths(BigInteger.valueOf(1590969549L)),
                drugHistory = "Some drug history",
                dateOfBirth = "2002-01-08",
                villageNumber = "133",
                sex = Sex.FEMALE,
                medicalHistory = "Some med history.",
                allergy = "Seasonal allergies",
                zone = "634",
                isExactDateOfBirth = true,
                householdNumber = "95682385",
                lastEdited = 1604883600L,
                lastServerUpdate = 1604883600L,
            ),
            listOf(
                Referral(
                    id = "42286a4a-f3be-4875-8b0f-3955ef65ec30",
                    comment = "Hello World!",
                    healthFacilityName = "H0000",
                    dateReferred = 1605753210,
                    userId = 1,
                    patientId = "123456",
                    isAssessed = false,
                    isCancelled = false,
                    lastEdited = 1605753210,
                    notAttended = false,
                    actionTaken = null,
                    cancelReason = null,
                    notAttendReason = null
                )
            )
        )
    )
}