package com.cradle.neptune.model

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ReferralTest {

    @Test
    fun `create reading from shared preferences`() {
        val fakeSharedPreferenceMap = mutableMapOf<String, Any?>()
        val mockSharedPref = mockk<SharedPreferences> {
            every { getInt(any(), any()) } answers {
                val key = firstArg<String?>() ?: error("no key supplied")
                if (fakeSharedPreferenceMap.containsKey(key)) {
                    fakeSharedPreferenceMap[key] as Int
                } else {
                    // default value
                    secondArg()
                }
            }
        }
        fakeSharedPreferenceMap["userId"] = 5

        val testReferral = Referral(
            comment = "this is a comment",
            healthFacilityName = "H23234",
            dateReferred = 16456665L,
            patientId = "400003232",
            readingId = UUID.randomUUID().toString(),
            mockSharedPref
        )

        assertEquals(5, testReferral.userId)
    }
}