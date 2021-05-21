package com.cradleplatform.neptune.database

import org.junit.jupiter.api.Test

class DatabaseTypeConvertersTests {
    private val typeConverter = DatabaseTypeConverters()

    @Test
    fun `string list is preserved`() {

        val listsToTest: Array<List<String>?> = arrayOf(
            null,
            listOf(),
            listOf(""),
            listOf("abc"),
            listOf(
                "df4565\"asd", "SHAdkjahkasjhkasjlk{asdsa, asdas}", "ij32809 OI po fiopu",
                "uysadkja sjkh ,,.m.m49p932][po]80=-*/-*+631276%@#^%#@5831876419878967341"
            ),
            listOf(
                """
                \"comment\":\"These are my referral comments\",
                \"dateReferred\":1604530201,
                \"healthFacilityName\":\"H5123\",
                \"id\":96,
                \"isAssessed\":false,
                \"patientId\":\"3295976464\",
                \"readingId\":\"46ba023a-b85d-4aad-8fa4-efe2e61ffe5e\",
                \"userId\":5}
            """.trimIndent()
            )
        )

        listsToTest.forEach { originalList ->
            val convertedList = runStringListConversion(originalList)
            assert(originalList == convertedList) {
                "expected $originalList, but got $convertedList"
            }
        }
    }

    private fun runStringListConversion(list: List<String>?): List<String>? =
        typeConverter.toStringList(typeConverter.fromStringList(list))
}