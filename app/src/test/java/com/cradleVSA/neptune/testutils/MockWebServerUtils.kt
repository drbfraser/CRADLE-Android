package com.cradleVSA.neptune.testutils

import android.content.SharedPreferences
import com.cradleVSA.neptune.manager.UrlManager
import com.cradleVSA.neptune.model.Settings
import com.cradleVSA.neptune.net.Http
import com.cradleVSA.neptune.net.RestApi
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockWebServer

object MockWebServerUtils {
    fun createRestApiWithMockedServer(
        webServerBlock: MockWebServer.() -> Unit
    ): Pair<RestApi, MockWebServer> {
        val mockServer = MockWebServer().apply { webServerBlock() }

        val mockSharedPrefs = mockk<SharedPreferences>()

        val mockSettings = mockk<Settings> {
            every { networkHostname } returns mockServer.url("").host
            every { networkPort } returns mockServer.port.toString()
            every { networkUseHttps } returns false
        }

        val fakeUrlManager = UrlManager(mockSettings)

        val restApi = RestApi(
            sharedPreferences = mockSharedPrefs,
            urlManager = fakeUrlManager,
            http = Http()
        )

        return restApi to mockServer
    }
}