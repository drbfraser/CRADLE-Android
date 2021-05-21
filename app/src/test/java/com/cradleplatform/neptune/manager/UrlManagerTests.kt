package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.model.Settings
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
