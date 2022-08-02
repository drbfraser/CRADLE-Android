package com.cradleplatform.neptune.testutils

import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.utilities.GzipCompressor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class GzipTests {
    @Test
    fun `test_compression_decompression`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val compressedMsg = GzipCompressor.compress(originalMsg)

        assertTrue(compressedMsg.size < originalMsg.toByteArray(Charsets.UTF_8).size)

        val decompressedMsg = GzipCompressor.decompress(compressedMsg)
        assertEquals(originalMsg, decompressedMsg)
    }
}