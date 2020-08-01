package com.cradle.neptune.network

import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.cradle.neptune.manager.NetworkManager
import com.cradle.neptune.model.JsonArray
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.utilitiles.functional.uncons

enum class HttpMethod(val volleyMethodCode: Int) {
    GET(Request.Method.GET),
    POST(Request.Method.POST),
    PUT(Request.Method.PUT),
    DELETE(Request.Method.DELETE)
}

sealed class NetworkRequest {
    private var continuations: MutableList<(NetworkResult<NetworkPayload>) -> NetworkRequest> = mutableListOf()

    /**
     * Binds the result of the last continuation of this request to a new request.
     *
     * For those familiar with functional programming, this method is analogous
     * to the monadic `bind` operation. It sequences another operation after this
     * one using the result of this operation to construct the next one.
     *
     * # Abstract Sense
     *
     * The simplistic body of this method is rather misleading. From an abstract
     * point of view it is better to think of this method as one which maps the
     * result of network request into another network request and then returns a
     * new request representing the sequence of performing the first request,
     * calling the closure to generate the second one and then performing it.
     * Thinking about this operation in those terms makes it clear how you can
     * chain a sequence of binds at the top level to generate a sequence of
     * requests instead of having to nest each request within the previous one.
     *
     * # Example
     *
     * ```
     * val url = "http://sample.com
     * val r = JsonObjectRequest(HttpMethod.GET, "$url/api/foo", null)
     *     // Bind to the result of "/api/foo".
     *     .bind { result ->
     *         when(result) {
     *             // Use the NetworkSequenceAbort class to abort a request sequence.
     *             is Failure -> NetworkSequenceAbort()
     *
     *             is Success -> {
     *                 val barId = result.value.jsonObject.getString("barId")
     *
     *                 // Return a new request to be executed after this one.
     *                 JsonObjectRequest(HttpMethod.GET, "$url/api/bar/$barId", null)
     *             }
     *         }
     *     }
     *     // Bind to the result of the request returned in the previous `bind`
     *     // expression, in this case it is "/api/bar/$barId".
     *     //
     *     // Note that since we used `NetworkSequenceAbort` in the previous
     *     // bind to abort the sequence if the first request failed, this
     *     // binding will only take place if that first request succeeded and
     *     // the second request (the "/api/bar/$barId" one) is sent.
     *     .bind { result ->
     *         // and so on...
     *     }
     * ```
     *
     * @param continuation a closure to generate a new request based on the
     *  result of this request
     * @return in an abstract sense, a network request which represents the
     *  action of performing this request and then a second one in sequence;
     *  in actuality however, this object is returned
     */
    fun bind(continuation: (NetworkResult<NetworkPayload>) -> NetworkRequest): NetworkRequest {
        continuations.add(continuation)
        return this
    }

    infix fun andThen(request: NetworkRequest): NetworkRequest = bind { request }

    infix fun andThenIfSuccessful(request: NetworkRequest): NetworkRequest = bind { result ->
        when (result) {
            is Success -> request
            is Failure -> NetworkSequenceAbort()
        }
    }

    /**
     * Converts this [NetworkRequest] into a Volley [Request].
     *
     * The response type (e.g., `JSONObject` or `JSONArray` cannot be statically
     * known as it is determined by the implementors of this class hence the
     * return type of `Request<*>`.
     *
     * @param manager the network manger used to enqueue continuations for this
     *  request; should be the same instance that will be used to enqueue this
     *  request, but it doesn't need to be
     * @param bearerToken a bearer token to include in the request's header
     * @return a volley request representing this request and all of its
     *  continuations
     */
    abstract fun asVolleyRequest(manager: NetworkManager, bearerToken: String?): Request<*>?

    /**
     * Constructs a closure which enqueues the next request in the continuation
     * sequence. The resultant is meant to be used as the response listener for
     * this request's volley request.
     *
     * @param manager the network manager used to enqueue the requests
     * @return a closure which can be used as a volley request's response listener
     */
    protected fun makeContinuationCallback(manager: NetworkManager): (NetworkResult<NetworkPayload>) -> Unit =
        cc@{ result ->
            // If there are no more requests in the sequence, then we're done.
            if (continuations.isEmpty()) {
                return@cc
            }

            // Pop off the next continuation binding in the sequence.
            val (first, rest) = continuations.uncons()

            // Use the result of this request to generate the next request via
            // the continuation binding.
            val nextRequest = first(result)

            // Add the remaining bindings to this new request. If the new
            // request already has bindings of it's own, then these additional
            // bindings will be invoked after the existing ones.
            nextRequest.continuations.addAll(rest)

            // Enqueue the request so that it can be sent starting the whole
            // process over again.
            manager.enqueue(nextRequest)
        }
}

class NetworkSequenceAbort : NetworkRequest() {
    override fun asVolleyRequest(manager: NetworkManager, bearerToken: String?): Request<*>? = null
}

class JsonObjectRequest(
    val method: HttpMethod,
    val url: String,
    val payload: JsonObjectPayload?
) : NetworkRequest() {

    override fun asVolleyRequest(manager: NetworkManager, bearerToken: String?): Request<*>? {
        val cc = makeContinuationCallback(manager)
        val successListener = Response.Listener<JsonObject> { cc(Success(JsonObjectPayload(it))) }
        val errorListener = Response.ErrorListener { cc(Failure(it)) }

        return object :
            JsonObjectRequest(method.volleyMethodCode, url, payload?.json, successListener, errorListener) {
            override fun getHeaders(): Map<String, String>? {
                if (bearerToken == null) {
                    return mapOf()
                }

                return mapOf("Authorization" to "Bearer $bearerToken")
            }
        }
    }
}

class JsonArrayRequest(
    val method: HttpMethod,
    val url: String,
    val payload: JsonArrayPayload?
) : NetworkRequest() {

    override fun asVolleyRequest(manager: NetworkManager, bearerToken: String?): Request<*>? {
        val cc = makeContinuationCallback(manager)
        val successListener = Response.Listener<JsonArray> { cc(Success(JsonArrayPayload(it))) }
        val errorListener = Response.ErrorListener { cc(Failure(it)) }

        return object :
            JsonArrayRequest(method.volleyMethodCode, url, payload?.json, successListener, errorListener) {
            override fun getHeaders(): Map<String, String>? {
                if (bearerToken == null) {
                    return mapOf()
                }

                return mapOf("Authorization" to "Bearer $bearerToken")
            }
        }
    }
}
