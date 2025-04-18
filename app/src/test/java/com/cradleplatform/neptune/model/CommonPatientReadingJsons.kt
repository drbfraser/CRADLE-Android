package com.cradleplatform.neptune.model

import java.math.BigInteger

/**
 * Contains JSONObjects and JSONArrays for [PatientAndReadings].
 *
 * If needed, use the script in `scripts/get.sh` to perform a GET request to the server
 * to update the JSON format.
 *
 * All these JSON strings are from the for-android-unit-tests@example.com
 * user, so to get updates for these JSONs, look at the comments and run
 * the appropriate command with the $PASSWORD environment variable set.
 * The $PASSWORD can be found on the staging server via the Users screen;
 * it's in the first name of the email.
 */
object CommonPatientReadingJsons {
    /**
     * A patient without gestational age
     * Patient id is 123456 on the staging server
     *
     * If needed, use the script in `scripts/get.sh` to perform a GET request to the server
     * to update the JSON format. This JSON was taken from the /api/mobile/patients
     * endpoint. Command to run:
     *
     *     ./get.sh https://staging.cradleplatform.com /api/mobile/patients \
     *         for-android-unit-tests@example.com $PASSWORD
     *
     * where $PASSWORD can be seen from the admin user on the staging server.
     *
     * first: A JSONObject string of the [PatientAndReadings] object
     * second: The corresponding expected [PatientAndReadings] object.
     */
    val patientNoGestAgeJsonAndExpected = Pair(
        first = """
{
    "id": "123456",
    "name": "Another patient",
    "sex": "MALE",
    "isPregnant": false,
    "isArchived": false,
    "medicalHistory": "",
    "drugHistory": "History",
    "allergy": "Seasonal allergies",
    "zone": "354",
    "dateOfBirth": "1974-11-08",
    "isExactDateOfBirth": false,
    "villageNumber": "4555",
    "householdNumber": "111",
    "created": 1604883668,
    "base": 1605687954,
    "lastEdited": 1605687954,
    "userId": 12,
    "readings": [
        {
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
        }
    ]
}
        """.trimIndent(),
        second = PatientAndReadings(
            Patient(
                isPregnant = false,
                isArchived = false,
                name = "Another patient",
                id = "123456",
                gestationalAge = null,
                drugHistory = "History",
                dateOfBirth = "1974-11-08",
                villageNumber = "4555",
                sex = Sex.MALE,
                medicalHistory = "",
                allergy = "Seasonal allergies",
                zone = "354",
                isExactDateOfBirth = false,
                householdNumber = "111",
                lastEdited = 1605687954,
                lastServerUpdate = 1605687954
            ),
            listOf(
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
                )
            )
        )
    )

    /**
     * A patient with gestational age
     * Patient id is 3459834789348 on the staging server.
     *
     * If needed, use the script in `scripts/get.sh` to perform a GET request to the server
     * to update the JSON format.
     *
     * This JSON was taken from the /api/patients/3459834789348
     * endpoint on the staging server. Command to run:
     *
     *     ./get.sh https://staging.cradleplatform.com /api/patients/3459834789348 \
     *         for-android-unit-tests@example.com $PASSWORD
     *
     * where $PASSWORD is in the first name of the for-android-unit-tests user. If you have
     * a JSON reformatting command, can pipe it using `json_reformat` or similar.
     *
     * first: A JSONObject string of the [PatientAndReadings] object
     * second: The corresponding expected [PatientAndReadings] object.
     */
    val patientWithGestAgeJsonAndExpected = Pair(
        first = """
{
    "name": "Test patient",
    "sex": "FEMALE",
    "medicalHistory": "Some med history.",
    "allergy": "Seasonal allergies",
    "zone": "634",
    "isExactDateOfBirth": true,
    "householdNumber": "95682385",
    "lastEdited": 1604883600,
    "isPregnant": true,
    "isArchived": true,
    "id": "3459834789348",
    "pregnancyStartDate": 1590969549,
    "drugHistory": "Some drug history",
    "dateOfBirth": "2002-01-08",
    "villageNumber": "133",
    "created": 1604883600,
    "base": 1604883600,
    "readings": [
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
                "readingId": "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94"
            }
        }
    ]
}
    """.trimIndent(),
        second = PatientAndReadings(
            Patient(
                isPregnant = true,
                isArchived = true,
                name = "Test patient",
                id = "3459834789348",
                gestationalAge = GestationalAgeWeeks(BigInteger.valueOf(1590969549L)),
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
                )
            )
        )
    )

    /**
     * A patient with a referral, follow up, urine tests
     * Patient id is 66665 on the staging server.
     *
     * If needed, use the script in `scripts/get.sh` to perform a GET request to the server
     * to update the JSON format.
     *
     * This JSON was taken from the /api/mobile/patients
     * endpoint on the staging server from the for-android-unit-tests@example.com user.
     * Command to run:
     *
     *     ./get.sh https://staging.cradleplatform.com /api/mobile/patients \
     *         for-android-unit-tests@example.com $PASSWORD
     *
     * where $PASSWORD is in the first name of the for-android-unit-tests user. If you have
     * a JSON reformatting command, can pipe it using `json_reformat` or similar.
     *
     * first: A JSONObject string of the [PatientAndReadings] object
     * second: The corresponding expected [PatientAndReadings] object.
     *
     */
    val patientWithReferralAndFollowup = Pair(
        first = """
{
    "id": "66665",
    "name": "Name",
    "sex": "OTHER",
    "isPregnant": true,
    "isArchived": false,
    "pregnancyStartDate": 1584245042,
    "medicalHistory": "Medical history.",
    "allergy": "Seasonal allergies",
    "drugHistory": "Drug history.",
    "zone": "8828",
    "dateOfBirth": "1955-11-09",
    "isExactDateOfBirth": false,
    "villageNumber": "998",
    "householdNumber": "135",
    "created": 1604981097,
    "base": 1604981097,
    "lastEdited": 1604981097,
    "userId": 12,
    "readings": [
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
                "followUpNeeded": true,
                "medicationPrescribed": "",
                "diagnosis": "",
                "specialInvestigations": "This is a message",
                "id": "22",
                "healthcareWorkerId": 1,
                "dateAssessed": 1604981359,
                "treatment": "Treatments applied",
                "followUpInstructions": "This is my follow up that is needed",
                "patientId": "66665"
                
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
        }
    ]
}
        """.trimIndent(),

        second = PatientAndReadings(
            patient = Patient(
                id = "66665",
                name = "Name",
                sex = Sex.OTHER,
                isPregnant = true,
                isArchived = false,
                gestationalAge = GestationalAgeWeeks(timestamp = BigInteger.valueOf(1584245042L)),
                medicalHistory = "Medical history.",
                allergy = "Seasonal allergies",
                drugHistory = "Drug history.",
                zone = "8828",
                dateOfBirth = "1955-11-09",
                isExactDateOfBirth = false,
                villageNumber = "998",
                householdNumber = "135",
                lastServerUpdate = 1604981097L,
                lastEdited = 1604981097L,
            ),
            readings = listOf(
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
                )
            )
        )
    )

    /**
     * A patient that has no symptoms.
     *
     * This JSON was taken from the /api/mobile/patients
     * endpoint on the staging server from the for-android-unit-tests@example.com user.
     * Command to run:
     *
     *     ./get.sh https://staging.cradleplatform.com /api/mobile/patients \
     *         for-android-unit-tests@example.com $PASSWORD
     */
    val patientWithNoSymptoms = Pair(
        first = """
{
    "id": "6454875454",
    "name": "PatientNoSymptoms",
    "sex": "MALE",
    "isPregnant": false,
    "isArchived": true,
    "medicalHistory": "",
    "allergy": "",
    "drugHistory": "Morphine",
    "zone": "7188473",
    "dateOfBirth": "1982-09-09",
    "isExactDateOfBirth": true,
    "villageNumber": "566454518",
    "householdNumber": "6594",
    "created": 1605778651,
    "base": 1605778651,
    "lastEdited": 1605778651,
    "userId": 12,
    "readings": [
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
        }
    ]
}
        """,
        second = PatientAndReadings(
            Patient(
                id = "6454875454",
                name = "PatientNoSymptoms",
                sex = Sex.MALE,
                isPregnant = false,
                isArchived = true,
                gestationalAge = null,
                medicalHistory = "",
                allergy = "",
                drugHistory = "Morphine",
                zone = "7188473",
                dateOfBirth = "1982-09-09",
                isExactDateOfBirth = true,
                villageNumber = "566454518",
                householdNumber = "6594",
                lastServerUpdate = 1605778651,
                lastEdited = 1605778651,
            ),
            listOf(
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
    )

    /**
     * first: JSONArray string of [PatientAndReadings] objects
     * second: The expected [PatientAndReadings] object for the JSONArray string. The order
     * of the list corresponds to the order in the JSONArray string.
     */
    val allPatientsJsonExpectedPair = Pair(
        first = """
[
    ${patientWithGestAgeJsonAndExpected.first},
    ${patientNoGestAgeJsonAndExpected.first},
    ${patientWithReferralAndFollowup.first},
    ${patientWithNoSymptoms.first}
]
        """.trimIndent(),
        second = listOf(
            patientWithGestAgeJsonAndExpected.second,
            patientNoGestAgeJsonAndExpected.second,
            patientWithReferralAndFollowup.second,
            patientWithNoSymptoms.second
        )
    )
}
