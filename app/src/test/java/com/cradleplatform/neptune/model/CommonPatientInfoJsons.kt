package com.cradleplatform.neptune.model

/**
 * Made on August 4th, 2022 according to response retrieved using "get.sh" under ./scripts/ in cradlemobile's directory
 */
object CommonPatientInfoJsons {

    val TEST_PATIENT_AA = Pair<String,Patient>(
        first = """
            {
              "patientId":"49300028162",
              "patientName":"AA",
              "patientSex":"FEMALE",
              "dob":"1992-01-01",
              "isExactDob":false,
              "villageNumber":"1002",
              "isPregnant":true,
              "pregnancyId":1,
              "pregnancyStartDate":1610925778,
              "gestationalAgeUnit":"WEEKS",
              "medicalHistoryId":1,
              "medicalHistory":"Pregnancy induced hypertension\nStarted on Labetalol 200mg three times daily two weeks ago",
              "drugHistoryId":2,
              "drugHistory":"Aspirin 75mg\nLabetalol 200mg three times daily",
              "lastEdited":1659612290,
              "base":1659612290
           }
        """.trimIndent(),
        second = Patient(
            id = "49300028162",
            name = "AA",
            sex = Sex.FEMALE,
            dob = "1992-01-01",
            isExactDob = false,
            villageNumber = "1002",
            isPregnant = true,
            pregnancyId = 1,
            //TODO: PregnancyStartDate mismatches android's Patients (#refer to issue 62)

        )
    )

    private const val GET_ALL_PATIENT_RESPONSE =
        """
            [
               {
                  "patientId":"49300028162",
                  "patientName":"AA",
                  "patientSex":"FEMALE",
                  "dob":"1992-01-01",
                  "isExactDob":false,
                  "villageNumber":"1002",
                  "isPregnant":true,
                  "pregnancyId":1,
                  "pregnancyStartDate":1610925778,
                  "gestationalAgeUnit":"WEEKS",
                  "medicalHistoryId":1,
                  "medicalHistory":"Pregnancy induced hypertension\nStarted on Labetalol 200mg three times daily two weeks ago",
                  "drugHistoryId":2,
                  "drugHistory":"Aspirin 75mg\nLabetalol 200mg three times daily",
                  "lastEdited":1659612290,
                  "base":1659612290
               },
               {
                  "patientId":"49300028161",
                  "patientName":"BB",
                  "patientSex":"MALE",
                  "dob":"1994-01-01",
                  "isExactDob":false,
                  "villageNumber":"1001",
                  "isPregnant":false,
                  "lastEdited":1659612290,
                  "base":1659612290
               },
               {
                  "patientId":"49300028163",
                  "patientName":"AB",
                  "patientSex":"FEMALE",
                  "dob":"1998-01-01",
                  "isExactDob":false,
                  "villageNumber":"1002",
                  "isPregnant":false,
                  "lastEdited":1659612290,
                  "base":1659612290
               }
            ]
        """

    private const val PATIENT_INFO_FOR_49300028162 =
        """
            {
               "lastEdited":1659612290,
               "patientId":"49300028162",
               "patientName":"AA",
               "dob":"1992-01-01",
               "patientSex":"FEMALE",
               "isExactDob":false,
               "isPregnant":true,
               "villageNumber":"1002",
               "gestationalAgeUnit":"WEEKS",
               "gestationalTimestamp":1610925778,
               "created":1659612290,
               "base":1659612290
            }
        """

    private const val PATIENT_FULL_FOR_49300028162 =
        """
            {
               "patientId":"49300028162",
               "patientName":"AA",
               "patientSex":"FEMALE",
               "dob":"1992-01-01",
               "isExactDob":false,
               "villageNumber":"1002",
               "isPregnant":true,
               "pregnancyId":1,
               "pregnancyStartDate":1610925778,
               "gestationalAgeUnit":"WEEKS",
               "medicalHistoryId":1,
               "medicalHistory":"Pregnancy induced hypertension\nStarted on Labetalol 200mg three times daily two weeks ago",
               "drugHistoryId":2,
               "drugHistory":"Aspirin 75mg\nLabetalol 200mg three times daily",
               "lastEdited":1659612290,
               "base":1659612290,
               "readings":[
                  {
                     "heartRateBPM":70,
                     "bpSystolic":50,
                     "patientId":"49300028162",
                     "lastEdited":1659612290,
                     "dateTimeTaken":1621204421,
                     "symptoms":[
                        "FEVERISH"
                     ],
                     "bpDiastolic":60,
                     "readingId":"11111111-d974-4059-a0a2-4b0a9c8e3a10",
                     "userId":3,
                     "trafficLightStatus":"YELLOW_DOWN"
                  }
               ],
               "referrals":[
                  {
                     "patientId":"49300028162",
                     "isCancelled":false,
                     "referralHealthFacilityName":"H0000",
                     "id":"f64ed8b3-6a2d-4901-851f-127590ddd1e9",
                     "dateReferred":1621204421,
                     "notAttended":false,
                     "isAssessed":false,
                     "lastEdited":1659612290,
                     "userId":3
                  }
               ]
            }
            
        """
    private const val SEARCH_RESULT_A =
        """
            [
               {
                  "patientName":"AA",
                  "patientId":"49300028162",
                  "villageNumber":"1002",
                  "readings":[
                     {
                        "dateReferred":null,
                        "dateTimeTaken":1621204421,
                        "trafficLightStatus":"YELLOW_DOWN"
                     }
                  ],
                  "state":"Add"
               },
               {
                  "patientName":"AB",
                  "patientId":"49300028163",
                  "villageNumber":"1002",
                  "readings":[
                     {
                        "dateReferred":null,
                        "dateTimeTaken":1610836421,
                        "trafficLightStatus":"YELLOW_DOWN"
                     }
                  ],
                  "state":"Add"
               }
            ]
    """

}