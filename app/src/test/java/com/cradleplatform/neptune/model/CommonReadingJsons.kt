package com.cradleplatform.neptune.model

object CommonReadingJsons {
    val allReadingsJsonExpectedPair = Pair(
        first = """
            [{
                "readingId": "25255191-05b1-47f3-a7c7-2a0321ea3588",
                "bpSystolic": 114,
                "bpDiastolic": 95,
                "heartRateBPM": 85,
                "symptoms": [
                    "Feverish",
                    "Muscle ache"
                ],
                "trafficLightStatus": "YELLOW_UP",
                "dateTimeTaken": 1605753210,
                "lastEdited": 1605753210,
                "dateRecheckVitalsNeeded": 1605754111,
                "retestOfPreviousReadingIds": "",
                "patientId": "123456",
                "isFlaggedForFollowup": false,
                "referral": {
                    "id": "120",
                    "comment": "",
                    "isAssessed": false,
                    "referralHealthFacilityName": "H0000",
                    "patientId": "123456",
                    "dateReferred": 1605753210,
                    "isCancelled": false,
                    "lastEdited": 1605753210,
                    "notAttended": false
                },
                "followup": null,
                "urineTests": null,
                "userId": 12
            },
            {
                "readingId": "777850f0-dc71-4501-a440-1871ecea6381",
                "bpSystolic": 119,
                "bpDiastolic": 98,
                "heartRateBPM": 87,
                "symptoms": [
                    "NONE"
                ],
                "trafficLightStatus": "YELLOW_UP",
                "dateTimeTaken": 1604883648,
                "lastEdited": 1604883648,
                "dateRecheckVitalsNeeded": 1605754111,
                "retestOfPreviousReadingIds": "",
                "patientId": "123456",
                "isFlaggedForFollowup": false,
                "referral": null,
                "followup": null,
                "urineTests": null,
                "userId": 12
            },
            {
                "bpDiastolic": 97,
                "heartRateBPM": 78,
                "patientId": "3459834789348",
                "readingId": "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94",
                "symptoms": [
                    "BLURRED VISION",
                    "FEVERISH",
                    "LOSS of SENSE",
                    "Other symptoms"
                ],
                "bpSystolic": 119,
                "trafficLightStatus": "YELLOW_UP",
                "dateTimeTaken": 1604883580,
                "lastEdited": 1604883580,
                "urineTests": {
                    "urineTestNit": "NAD",
                    "id": 18,
                    "urineTestPro": "NAD",
                    "urineTestBlood": "NAD",
                    "urineTestGlu": "+++",
                    "urineTestLeuc": "++",
                    "readingId": "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94"
                }
            },
            {
                "readingId": "b1e9d431-0265-484d-a4df-695dd6aa827e",
                "bpSystolic": 114,
                "bpDiastolic": 95,
                "heartRateBPM": 85,
                "symptoms": [
                    "Unwell",
                    "Shortness of breath"
                ],
                "trafficLightStatus": "YELLOW_UP",
                "dateTimeTaken": 1604981072,
                "lastEdited": 1604981072,
                "dateRecheckVitalsNeeded": 1604981973,
                "retestOfPreviousReadingIds": "",
                "patientId": "66665",
                "isFlaggedForFollowup": false,
                "referral": {
                    "id": "104",
                    "comment": "",
                    "isAssessed": true,
                    "referralHealthFacilityName": "H0000",
                    "patientId": "66665",
                    "dateReferred": 1604981072,
                    "isCancelled": false,
                    "lastEdited": 1604981072,
                    "notAttended": false
                },
                "followup": {
                    "id": "22",
                    "followupInstructions": "This is my follow up that is needded",
                    "specialInvestigations": "This is a messgage",
                    "diagnosis": "",
                    "treatment": "Treatmnents applied",
                    "medicationPrescribed": "",
                    "dateAssessed": 1604981359,
                    "followupNeeded": true,
                    "patientId": "66665",
                    "healthcareWorkerId": 1
                },
                "urineTests": {
                    "id": 19,
                    "urineTestLeuc": "NAD",
                    "urineTestNit": "+++",
                    "urineTestGlu": "+",
                    "urineTestPro": "++",
                    "urineTestBlood": "+"
                },
                "userId": 12
            },
            {
                "readingId": "1d242c26-5252-4187-bc82-9e4473c5a8c6",
                "bpSystolic": 123,
                "bpDiastolic": 89,
                "heartRateBPM": 85,
                "symptoms": "",
                "trafficLightStatus": "GREEN",
                "dateTimeTaken": 1605778643,
                "lastEdited": 1605778643,
                "dateRecheckVitalsNeeded": 1603347681,
                "retestOfPreviousReadingIds": "",
                "patientId": "6454875454",
                "isFlaggedForFollowup": true,
                "referral": null,
                "followup": null,
                "urineTests": null,
                "userId": 1
            }]
        """.trimIndent(),
        second = listOf(
            Reading(
                id = "25255191-05b1-47f3-a7c7-2a0321ea3588",
                bloodPressure = BloodPressure(
                    systolic = 114,
                    diastolic = 95,
                    heartRate = 85
                ),
                symptoms = listOf("Feverish", "Muscle ache"),
                dateTimeTaken = 1605753210,
                lastEdited = 1605753210,
                dateRecheckVitalsNeeded = 1605754111,
                previousReadingIds = listOf(),
                patientId = "123456",
                isFlaggedForFollowUp = false,
                referral = Referral(
                    id = "120",
                    comment = "",
                    isAssessed = false,
                    referralHealthFacilityName = "H0000",
                    patientId = "123456",
                    actionTaken = null,
                    cancelReason = null,
                    isCancelled = false,
                    lastEdited = 1605753210,
                    notAttendReason = null,
                    notAttended = false,
                    dateReferred = 1605753210,
                    userId = null /* not sent back by server */
                ),
                followUp = null,
                urineTest = null,
                userId = 12
            ),
            Reading(
                bloodPressure = BloodPressure(
                    systolic = 119,
                    diastolic = 98,
                    heartRate = 87
                ),
                dateTimeTaken = 1604883648L,
                lastEdited = 1604883648L,
                patientId = "123456",
                id = "777850f0-dc71-4501-a440-1871ecea6381",
                symptoms = listOf("NONE"),
                dateRecheckVitalsNeeded = 1605754111,
                followUp = null,
                referral = null,
                isFlaggedForFollowUp = false,
                urineTest = null,
                previousReadingIds = listOf(),
                userId = 12
            ),
            Reading(
                bloodPressure = BloodPressure(119, 97, 78),
                dateTimeTaken = 1604883580L,
                lastEdited = 1604883580L,
                patientId = "3459834789348",
                id = "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94",
                symptoms = listOf(
                    "BLURRED VISION", "FEVERISH", "LOSS of SENSE",
                    "Other symptoms"
                ),
                urineTest = UrineTest(
                    leukocytes = "++", glucose = "+++", protein = "NAD", nitrites = "NAD",
                    blood = "NAD"
                ),
                dateRecheckVitalsNeeded = null,
                followUp = null,
                referral = null,
                isFlaggedForFollowUp = false,
                userId = null
            ),
            Reading(
                id = "b1e9d431-0265-484d-a4df-695dd6aa827e",
                bloodPressure = BloodPressure(
                    systolic = 114,
                    diastolic = 95,
                    heartRate = 85
                ),
                symptoms = listOf("Unwell", "Shortness of breath"),
                dateTimeTaken = 1604981072L,
                lastEdited = 1604981072L,
                previousReadingIds = emptyList(),
                patientId = "66665",
                isFlaggedForFollowUp = false,
                referral = Referral(
                    id = "104",
                    comment = "",
                    isAssessed = true,
                    referralHealthFacilityName = "H0000",
                    patientId = "66665",
                    actionTaken = null,
                    cancelReason = null,
                    isCancelled = false,
                    lastEdited = 1604981072L,
                    notAttendReason = null,
                    notAttended = false,
                    dateReferred = 1604981072L,
                    userId = null
                ),
                followUp = Assessment(
                    id = "22",
                    followupInstructions = "This is my follow up that is needded",
                    specialInvestigations = "This is a messgage",
                    diagnosis = "",
                    treatment = "Treatmnents applied",
                    medicationPrescribed = "",
                    dateAssessed = 1604981359L,
                    followupNeeded = true,
                    healthCareWorkerId = 1,
                    patientId = "66665"
                ),
                urineTest = UrineTest(
                    leukocytes = "NAD",
                    nitrites = "+++",
                    glucose = "+",
                    protein = "++",
                    blood = "+"
                ),
                dateRecheckVitalsNeeded = 1604981973L,
                userId = 12
            ),
            Reading(
                id = "1d242c26-5252-4187-bc82-9e4473c5a8c6",
                bloodPressure = BloodPressure(123, 89, 85),
                symptoms = emptyList(),
                dateTimeTaken = 1605778643,
                lastEdited = 1605778643,
                dateRecheckVitalsNeeded = 1603347681,
                previousReadingIds = emptyList(),
                patientId = "6454875454",
                isFlaggedForFollowUp = true,
                referral = null,
                followUp = null,
                urineTest = null,
                userId = 1
            )
        )
    )
}
