package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.model.Settings
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger

class UrlManagerTests {

    @Test
    fun base_portIsNull_constructUrlWithoutPort() {
        val mockSettings: Settings = mockk {
            every { networkUseHttps } returns true
            every { networkHostname } returns "sample.domain.com"
            every { networkPort } returns null
        }

        val url = UrlManager(mockSettings)

        assertEquals("https://sample.domain.com/api", url.base)
    }

    @Test
    fun base_portIsBlank_constructUrlWithoutPort() {
        val mockSettings: Settings = mockk {
            every { networkUseHttps } returns true
            every { networkHostname } returns "sample.domain.com"
            every { networkPort } returns null
        }

        val url = UrlManager(mockSettings)

        assertEquals("https://sample.domain.com/api", url.base)
    }

    @Test
    fun base_portIsNotNull_constructUrlWithPort() {
        val mockSettings: Settings = mockk {
            every { networkUseHttps } returns false
            every { networkHostname } returns "sample.domain.com"
            every { networkPort } returns "8080"
        }

        val url = UrlManager(mockSettings)

        assertEquals("http://sample.domain.com:8080/api", url.base)
    }

    @Test
    fun getWorkflowTemplatesSync_constructsSyncUrlWithTimestamp() {
        val mockSettings: Settings = mockk {
            every { networkUseHttps } returns true
            every { networkHostname } returns "sample.domain.com"
            every { networkPort } returns null
        }

        val url = UrlManager(mockSettings)

        assertEquals(
            "https://sample.domain.com/api/sync/workflow_templates?since=1604530201",
            url.getWorkflowTemplatesSync(BigInteger.valueOf(1604530201L))
        )
    }

    @Test
    fun getWorkflowInstancesSync_constructsSyncUrlWithTimestamp() {
        val mockSettings: Settings = mockk {
            every { networkUseHttps } returns true
            every { networkHostname } returns "sample.domain.com"
            every { networkPort } returns null
        }

        val url = UrlManager(mockSettings)

        assertEquals(
            "https://sample.domain.com/api/sync/workflow_instances?since=1604530201",
            url.getWorkflowInstancesSync(BigInteger.valueOf(1604530201L))
        )
    }
}
