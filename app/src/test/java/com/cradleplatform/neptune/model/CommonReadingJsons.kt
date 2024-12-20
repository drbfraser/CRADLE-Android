package com.cradleplatform.neptune.model

object CommonReadingJsons {
    val allReadingsJsonExpectedPair = Pair(
        first = """
            [{
                "id": "25255191-05b1-47f3-a7c7-2a0321ea3588",
                "systolicBloodPressure": 114,
                "diastolicBloodPressure": 95,
                "heartRate": 85,
                "symptoms": [
                    "Feverish",
                    "Muscle ache"
                ],
                "trafficLightStatus": "YELLOW_UP",
                "dateTaken": 1605753210,
                "lastEdited": 1605753210,
                "dateRetestNeeded": 1605754111,
                "retestOfPreviousReadingIds": "",
                "patientId": "123456",
                "isFlaggedForFollowUp": false,
                "referral": {
                    "id": "120",
                    "comment": "",
                    "isAssessed": false,
                    "healthFacilityName": "H0000",
                    "patientId": "123456",
                    "dateReferred": 1605753210,
                    "isCancelled": false,
                    "lastEdited": 1605753210,
                    "notAttended": false
                },
                "followUp": null,
                "urineTests": null,
                "userId": 12
            },
            {
                "id": "777850f0-dc71-4501-a440-1871ecea6381",
                "systolicBloodPressure": 119,
                "diastolicBloodPressure": 98,
                "heartRate": 87,
                "symptoms": [
                    "NONE"
                ],
                "trafficLightStatus": "YELLOW_UP",
                "dateTaken": 1604883648,
                "lastEdited": 1604883648,
                "dateRetestNeeded": 1605754111,
                "retestOfPreviousReadingIds": "",
                "patientId": "123456",
                "isFlaggedForFollowUp": false,
                "referral": null,
                "followUp": null,
                "urineTests": null,
                "userId": 12
            },
            {
                "diastolicBloodPressure": 97,
                "heartRate": 78,
                "patientId": "3459834789348",
                "id": "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94",
                "symptoms": [
                    "BLURRED VISION",
                    "FEVERISH",
                    "LOSS of SENSE",
                    "Other symptoms"
                ],
                "systolicBloodPressure": 119,
                "trafficLightStatus": "YELLOW_UP",
                "dateTaken": 1604883580,
                "lastEdited": 1604883580,
                "urineTests": {
                    "nitrites": "NAD",
                    "id": 18,
                    "protein": "NAD",
                    "blood": "NAD",
                    "glucose": "+++",
                    "leukocytes": "++",
                    "id": "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94"
                }
            },
            {
                "id": "b1e9d431-0265-484d-a4df-695dd6aa827e",
                "systolicBloodPressure": 114,
                "diastolicBloodPressure": 95,
                "heartRate": 85,
                "symptoms": [
                    "Unwell",
                    "Shortness of breath"
                ],
                "trafficLightStatus": "YELLOW_UP",
                "dateTaken": 1604981072,
                "lastEdited": 1604981072,
                "dateRetestNeeded": 1604981973,
                "retestOfPreviousReadingIds": "",
                "patientId": "66665",
                "isFlaggedForFollowUp": false,
                "referral": {
                    "id": "104",
                    "comment": "",
                    "isAssessed": true,
                    "healthFacilityName": "H0000",
                    "patientId": "66665",
                    "dateReferred": 1604981072,
                    "isCancelled": false,
                    "lastEdited": 1604981072,
                    "notAttended": false
                },
                "followUp": {
                    "id": "22",
                    "followupInstructions": "This is my follow up that is needed",
                    "specialInvestigations": "This is a message",
                    "diagnosis": "",
                    "treatment": "Treatments applied",
                    "medicationPrescribed": "",
                    "dateAssessed": 1604981359,
                    "followupNeeded": true,
                    "patientId": "66665",
                    "healthcareWorkerId": 1
                },
                "urineTests": {
                    "id": 19,
                    "leukocytes": "NAD",
                    "nitrites": "+++",
                    "glucose": "+",
                    "protein": "++",
                    "blood": "+"
                },
                "userId": 12
            },
            {
                "id": "1d242c26-5252-4187-bc82-9e4473c5a8c6",
                "systolicBloodPressure": 123,
                "diastolicBloodPressure": 89,
                "heartRate": 85,
                "symptoms": "",
                "trafficLightStatus": "GREEN",
                "dateTaken": 1605778643,
                "lastEdited": 1605778643,
                "dateRetestNeeded": 1603347681,
                "retestOfPreviousReadingIds": "",
                "patientId": "6454875454",
                "isFlaggedForFollowUp": true,
                "referral": null,
                "followUp": null,
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
                dateTaken = 1605753210,
                lastEdited = 1605753210,
                dateRetestNeeded = 1605754111,
                previousReadingIds = listOf(),
                patientId = "123456",
                isFlaggedForFollowUp = false,
                referral = Referral(
                    id = "120",
                    comment = "",
                    isAssessed = false,
                    healthFacilityName = "H0000",
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
                dateTaken = 1604883648L,
                lastEdited = 1604883648L,
                patientId = "123456",
                id = "777850f0-dc71-4501-a440-1871ecea6381",
                symptoms = listOf("NONE"),
                dateRetestNeeded = 1605754111,
                followUp = null,
                referral = null,
                isFlaggedForFollowUp = false,
                urineTest = null,
                previousReadingIds = listOf(),
                userId = 12
            ),
            Reading(
                bloodPressure = BloodPressure(119, 97, 78),
                dateTaken = 1604883580L,
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
                dateRetestNeeded = null,
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
                dateTaken = 1604981072L,
                lastEdited = 1604981072L,
                previousReadingIds = emptyList(),
                patientId = "66665",
                isFlaggedForFollowUp = false,
                referral = Referral(
                    id = "104",
                    comment = "",
                    isAssessed = true,
                    healthFacilityName = "H0000",
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
                    followUpInstructions = "This is my follow up that is needed",
                    specialInvestigations = "This is a message",
                    diagnosis = "",
                    treatment = "Treatments applied",
                    medicationPrescribed = "",
                    dateAssessed = 1604981359L,
                    followUpNeeded = true,
                    healthcareWorkerId = 1,
                    patientId = "66665"
                ),
                urineTest = UrineTest(
                    leukocytes = "NAD",
                    nitrites = "+++",
                    glucose = "+",
                    protein = "++",
                    blood = "+"
                ),
                dateRetestNeeded = 1604981973L,
                userId = 12
            ),
            Reading(
                id = "1d242c26-5252-4187-bc82-9e4473c5a8c6",
                bloodPressure = BloodPressure(123, 89, 85),
                symptoms = emptyList(),
                dateTaken = 1605778643,
                lastEdited = 1605778643,
                dateRetestNeeded = 1603347681,
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
