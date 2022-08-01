package com.cradleplatform.neptune.net

import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.AsyncHttpResponseHandler
import com.loopj.android.http.RequestParams

object MyHttpClient {
    private val client = AsyncHttpClient()
    operator fun get(
        url: String?,
        params: RequestParams?,
        responseHandler: AsyncHttpResponseHandler?
    ) {
        client[url, params, responseHandler]
    }

    fun post(url: String?, params: RequestParams?, responseHandler: AsyncHttpResponseHandler?) {
        client.post(url, params, responseHandler)
    }
}
