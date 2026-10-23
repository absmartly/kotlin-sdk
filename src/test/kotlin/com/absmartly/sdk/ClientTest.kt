package com.absmartly.sdk

import java.util.concurrent.CompletableFuture
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
