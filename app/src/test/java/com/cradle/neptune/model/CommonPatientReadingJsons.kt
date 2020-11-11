package com.cradle.neptune.model

/**
 * Contains JSONObjects and JSONArrays for [PatientAndReadings].
 *
 * If needed, use the script in `scripts/get.sh` to perform a GET request to the server
 * to update the JSON format.
 */
class CommonPatientReadingJsons {
    companion object {

        /**
         * A patient without gestational age
         * Patient id is 123456 on the staging server
         *
         * If needed, use the script in `scripts/get.sh` to perform a GET request to the server
         * to update the JSON format.
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
    "gestationalTimestamp": 0,
    "drugHistory": "History",
    "dob": "1974-11-08",
    "villageNumber": "4555",
    "created": 1604883668,
    "gestationalAgeUnit": "GESTATIONAL_AGE_UNITS_WEEKS",
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
                    gestationalAge = GestationalAgeWeeks(timestamp = 0),
                    drugHistoryList = listOf("History"),
                    dob = "1974-11-08",
                    villageNumber = "4555",
                    sex = Sex.MALE,
                    medicalHistoryList = emptyList(),
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
                        urineTest = null
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
         * first: A JSONObject string of the [PatientAndReadings] object
         * second: The corresponding expected [PatientAndReadings] object.
         */
        val patientWithGestAgeJsonAndExpected = Pair(
            first = """
{
    "isPregnant": true,
    "patientName": "Test patient",
    "patientId": "3459834789348",
    "gestationalTimestamp": 1590969549,
    "drugHistory": "Some drug history",
    "dob": "2002-01-08",
    "villageNumber": "133",
    "created": 1604883600,
    "gestationalAgeUnit": "GESTATIONAL_AGE_UNITS_WEEKS",
    "patientSex": "FEMALE",
    "medicalHistory": "Some med history.",
    "zone": "634",
    "isExactDob": true,
    "householdNumber": "95682385",
    "lastEdited": 1604883600,
    "base": 1604883600,
    "readings": [
        {
            "bpSystolic": 119,
            "dateTimeTaken": 1604883580,
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
            "trafficLightStatus": "YELLOW_UP",
            "urineTests": {
                "urineTestLeuc": "++",
                "urineTestGlu": "+++",
                "urineTestPro": "NAD",
                "readingId": "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94",
                "urineTestNit": "NAD",
                "id": 18,
                "urineTestBlood": "NAD"
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
                    drugHistoryList = listOf("Some drug history"),
                    dob = "2002-01-08",
                    villageNumber = "133",
                    sex = Sex.FEMALE,
                    medicalHistoryList = listOf("Some med history."),
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
         * first: JSONArray string of [PatientAndReadings] objects, where the objects are both
         *  [patientWithGestAgeJsonAndExpected] and [patientNoGestAgeJsonAndExpected]
         * second: The expected [PatientAndReadings] object for the JSONArray string.
         */
        val bothPatientsInArray = Pair(
            first = """
[${patientWithGestAgeJsonAndExpected.first}, ${patientNoGestAgeJsonAndExpected.first}]
        """.trimIndent(),
            second = listOf(
                patientWithGestAgeJsonAndExpected.second,
                patientNoGestAgeJsonAndExpected.second
            )
        )
    }
}
