package com.cradleVSA.neptune.model

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
    "isPregnant": false,
    "patientName": "Another patient",
    "patientId": "123456",
    "drugHistory": "History",
    "dob": "1974-11-08",
    "villageNumber": "4555",
    "created": 1604883668,
    "patientSex": "MALE",
    "medicalHistory": "",
    "zone": "354",
    "isExactDob": false,
    "householdNumber": "111",
    "lastEdited": 1604883668,
    "base": 1604883668,
    "readings": [
        {
            "bpSystolic": 119,
            "dateTimeTaken": 1604883648,
            "bpDiastolic": 98,
            "heartRateBPM": 87,
            "userId": 10,
            "patientId": "123456",
            "readingId": "777850f0-dc71-4501-a440-1871ecea6381",
            "symptoms": [
                "NONE"
            ],
            "trafficLightStatus": "YELLOW_UP"
        }
    ]
}
        """.trimIndent(),
        second = PatientAndReadings(
            Patient(
                isPregnant = false,
                name = "Another patient",
                id = "123456",
                gestationalAge = null,
                drugHistory = "History",
                dob = "1974-11-08",
                villageNumber = "4555",
                sex = Sex.MALE,
                medicalHistory = "",
                zone = "354",
                isExactDob = false,
                householdNumber = "111",
                lastEdited = 1604883668L,
                base = 1604883668L
            ),
            listOf(
                Reading(
                    bloodPressure = BloodPressure(
                        systolic = 119,
                        diastolic = 98,
                        heartRate = 87
                    ),
                    dateTimeTaken = 1604883648L,
                    patientId = "123456",
                    id = "777850f0-dc71-4501-a440-1871ecea6381",
                    symptoms = listOf("NONE"),
                    dateRecheckVitalsNeeded = null,
                    followUp = null,
                    referral = null,
                    isFlaggedForFollowUp = false,
                    urineTest = null,
                    previousReadingIds = listOf()
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
"patientName": "Test patient",
"patientSex": "FEMALE",
"gestationalAgeUnit": "GESTATIONAL_AGE_UNITS_WEEKS",
"medicalHistory": "Some med history.",
"zone": "634",
"isExactDob": true,
"householdNumber": "95682385",
"lastEdited": 1604883600,
"patientId": "3459834789348",
"isPregnant": true,
"gestationalTimestamp": 1590969549,
"drugHistory": "Some drug history",
"dob": "2002-01-08",
"villageNumber": "133",
"created": 1604883600,
"base": 1604883600,
"readings": [
    {
        "bpDiastolic": 97,
        "heartRateBPM": 78,
        "respiratoryRate": 65,
        "oxygenSaturation": 98,
        "userId": 10,
        "temperature": 35,
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
        "urineTests": {
            "urineTestNit": "NAD",
            "id": 18,
            "urineTestPro": "NAD",
            "urineTestBlood": "NAD",
            "urineTestLeuc": "++",
            "urineTestGlu": "+++",
            "readingId": "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94"
        }
    }
]
}
    """.trimIndent(),
        second = PatientAndReadings(
            Patient(
                isPregnant = true,
                name = "Test patient",
                id = "3459834789348",
                gestationalAge = GestationalAgeWeeks(1590969549L),
                drugHistory = "Some drug history",
                dob = "2002-01-08",
                villageNumber = "133",
                sex = Sex.FEMALE,
                medicalHistory = "Some med history.",
                zone = "634",
                isExactDob = true,
                householdNumber = "95682385",
                lastEdited = 1604883600L,
                base = 1604883600L,
            ),
            listOf(
                Reading(
                    bloodPressure = BloodPressure(119, 97, 78),
                    dateTimeTaken = 1604883580L,
                    respiratoryRate = 65,
                    oxygenSaturation = 98,
                    temperature = 35,
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
                    isFlaggedForFollowUp = false
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
    "patientId": "66665",
    "patientName": "Name",
    "patientSex": "OTHER",
    "isPregnant": true,
    "gestationalAgeUnit": "GESTATIONAL_AGE_UNITS_MONTHS",
    "gestationalTimestamp": 1584245042,
    "medicalHistory": "med hustory",
    "drugHistory": "History",
    "zone": "8828",
    "dob": "1955-11-09",
    "isExactDob": false,
    "villageNumber": "998",
    "householdNumber": "135",
    "created": 1604981097,
    "base": 1604981097,
    "lastEdited": 1604981097,
    "userId": 12,
    "readings": [
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
            "patientId": "66665",
            "referral": {
                "id": 104,
                "comment": "",
                "isAssessed": true,
                "referralHealthFacilityName": "H0000",
                "patientId": "66665",
                "readingId": "b1e9d431-0265-484d-a4df-695dd6aa827e",
                "dateReferred": 1604981072
            },
            "followup": {
                "id": 22,
                "followupInstructions": "This is my follow up that is needded",
                "specialInvestigations": "This is a messgage",
                "diagnosis": "",
                "treatment": "Treatmnents applied",
                "medicationPrescribed": "",
                "dateAssessed": 1604981359,
                "followupNeeded": true,
                "readingId": "b1e9d431-0265-484d-a4df-695dd6aa827e",
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
            "respiratoryRate": 65,
            "oxygenSaturation": 95,
            "temperature": 44,
            "retestOfPreviousReadingIds": "",
            "isFlaggedForFollowup": false,
            "dateRecheckVitalsNeeded": 1604981973
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
                gestationalAge = GestationalAgeMonths(timestamp = 1584245042L),
                medicalHistory = "med hustory",
                drugHistory = "History",
                zone = "8828",
                dob = "1955-11-09",
                isExactDob = false,
                villageNumber = "998",
                householdNumber = "135",
                base = 1604981097L,
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
                    dateTimeTaken = 1604981072L,
                    previousReadingIds = emptyList(),
                    patientId = "66665",
                    isFlaggedForFollowUp = false,
                    referral = Referral(
                        id = 104,
                        comment = "",
                        isAssessed = true,
                        healthFacilityName = "H0000",
                        patientId = "66665",
                        readingId = "b1e9d431-0265-484d-a4df-695dd6aa827e",
                        dateReferred = 1604981072L,
                        userId = null
                    ),
                    followUp = Assessment(
                        id = 22,
                        followupInstructions = "This is my follow up that is needded",
                        specialInvestigations = "This is a messgage",
                        diagnosis = "",
                        treatment = "Treatmnents applied",
                        medicationPrescribed = "",
                        dateAssessed = 1604981359L,
                        followupNeeded = true,
                        readingId = "b1e9d431-0265-484d-a4df-695dd6aa827e",
                        healthCareWorkerId = 1
                    ),
                    urineTest = UrineTest(
                        leukocytes = "NAD",
                        nitrites = "+++",
                        glucose = "+",
                        protein = "++",
                        blood = "+"
                    ),
                    dateRecheckVitalsNeeded = 1604981973L,
                    respiratoryRate = 65,
                    oxygenSaturation = 95,
                    temperature = 44
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
    ${patientWithReferralAndFollowup.first}
]
        """.trimIndent(),
        second = listOf(
            patientWithGestAgeJsonAndExpected.second,
            patientNoGestAgeJsonAndExpected.second,
            patientWithReferralAndFollowup.second,
        )
    )
}
