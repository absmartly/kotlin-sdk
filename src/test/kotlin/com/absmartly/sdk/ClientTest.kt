package com.absmartly.sdk

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClientTest {

    private class MockHTTPClient : HTTPClient {
        val lastGetHeaders = AtomicReference<Map<String, String>?>()
        val lastPutHeaders = AtomicReference<Map<String, String>?>()

        override fun get(url: String, query: Map<String, String>?, headers: Map<String, String>?): CompletableFuture<HTTPClient.Response> {
            lastGetHeaders.set(headers)
            return CompletableFuture.completedFuture(
                HTTPClient.Response(200, "OK", "application/json", """{"experiments":[]}""".toByteArray())
            )
        }

        override fun put(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<HTTPClient.Response> {
            lastPutHeaders.set(headers)
            return CompletableFuture.completedFuture(
                HTTPClient.Response(200, "OK", "application/json", ByteArray(0))
            )
        }

        override fun post(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<HTTPClient.Response> {
            return CompletableFuture.completedFuture(
                HTTPClient.Response(200, "OK", "application/json", ByteArray(0))
            )
        }

        override fun close() {}
    }

    private fun createConfig(): ClientConfig {
        return ClientConfig.create()
            .setEndpoint("https://test.example.com")
            .setAPIKey("test-api-key")
            .setApplication("test-app")
            .setEnvironment("test-env")
    }

    @Test
    fun getContextDataSendsAuthHeaders() {
        val mockHttp = MockHTTPClient()
        val client = Client.create(createConfig(), mockHttp)
        client.getContextData().join()

        val headers = mockHttp.lastGetHeaders.get()
        assertNotNull(headers)
        assertEquals("test-api-key", headers["X-API-Key"])
        assertEquals("test-app", headers["X-Application"])
        assertEquals("test-env", headers["X-Environment"])
    }

    @Test
    fun publishSendsAuthHeaders() {
        val mockHttp = MockHTTPClient()
        val client = Client.create(createConfig(), mockHttp)

        val event = PublishEvent(
            hashed = true,
            publishedAt = System.currentTimeMillis(),
            units = listOf(Unit("session_id", "abc")),
            exposures = null,
            goals = null,
            attributes = null
        )
        client.publish(event).join()

        val headers = mockHttp.lastPutHeaders.get()
        assertNotNull(headers)
        assertEquals("test-api-key", headers["X-API-Key"])
    }

    @Test
    fun completionChainsPreserveHttpAndTransportFailures() {
        val transportError = IllegalStateException("transport failed")
        var failTransport = false
        val http = object : HTTPClient {
            private fun response(): CompletableFuture<HTTPClient.Response> {
                if (failTransport) return CompletableFuture<HTTPClient.Response>().also {
                    it.completeExceptionally(transportError)
                }
                return CompletableFuture.completedFuture(
                    HTTPClient.Response(503, "Unavailable", "text/plain", "retry later".toByteArray())
                )
            }
            override fun get(url: String, query: Map<String, String>?, headers: Map<String, String>?) = response()
            override fun put(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray) = response()
            override fun post(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray) = response()
            override fun close() {}
        }
        val client = Client.create(createConfig(), http)
        val event = PublishEvent(hashed = true, publishedAt = 1L, units = emptyList(), exposures = null, goals = null, attributes = null)
        for (operation in listOf<() -> CompletableFuture<*>>({ client.getContextData() }, { client.publish(event) })) {
            val failure = assertFailsWith<CompletionException> { operation().join() }
            assertTrue(failure.cause!!.message!!.contains("HTTP 503: Unavailable - retry later"))
        }
        failTransport = true
        for (operation in listOf<() -> CompletableFuture<*>>({ client.getContextData() }, { client.publish(event) })) {
            assertSame(transportError, assertFailsWith<CompletionException> { operation().join() }.cause)
        }
    }

    @Test
    fun closeCallsHTTPClientClose() {
        var closeCalled = false
        val mockHttp = object : HTTPClient {
            override fun get(url: String, query: Map<String, String>?, headers: Map<String, String>?) =
                CompletableFuture.completedFuture(HTTPClient.Response(200, "OK", "", ByteArray(0)))
            override fun put(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray) =
                CompletableFuture.completedFuture(HTTPClient.Response(200, "OK", "", ByteArray(0)))
            override fun post(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray) =
                CompletableFuture.completedFuture(HTTPClient.Response(200, "OK", "", ByteArray(0)))
            override fun close() { closeCalled = true }
        }
        val client = Client.create(createConfig(), mockHttp)
        client.close()
        assertTrue(closeCalled)
    }
}
