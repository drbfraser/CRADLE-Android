package com.cradleplatform.neptune.model

object CommonReferralJsons {
    val allReferralsJsonExpectedPair = Pair(
        first = """
            [{
                "id": "42286a4a-f3be-4875-8b0f-3955ef65ec30",
                "comment": "Hello World!",
                "referralHealthFacilityName": "H0000",
                "dateReferred": 1605753210,
                "userId": 1,
                "patientId": "123456",
                "isAssessed": false,
                "isCancelled": false,
                "lastEdited": 1605753210,
                "notAttended": false 
            },{
                "id": "690c89ae-3097-4277-9913-08a24c9929dc",
                "comment": "Lorem Ipsum",
                "referralHealthFacilityName": "H0000",
                "dateReferred": 1656552823,
                "userId": 2,
                "patientId": "111111",
                "isAssessed": true, 
                "isCancelled": false,
                "lastEdited": 1656552823,
                "notAttended": true
            }]
        """.trimIndent(),
        second = listOf(
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
            ),
            Referral(
                id = "690c89ae-3097-4277-9913-08a24c9929dc",
                comment =  "Lorem Ipsum",
                healthFacilityName = "H0000",
                dateReferred = 1656552823,
                userId = 2,
                patientId = "111111",
                isAssessed = true,
                isCancelled = false,
                lastEdited = 1656552823,
                notAttended = true,
                actionTaken = null,
                cancelReason = null,
                notAttendReason = null
            )
        )
    )
}