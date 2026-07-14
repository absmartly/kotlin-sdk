package com.absmartly.sdk

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

class ABsmartlyTest {

    private class MockHTTPClient : HTTPClient {
        val closeCount = AtomicInteger(0)

        override fun get(url: String, query: Map<String, String>?, headers: Map<String, String>?) =
            CompletableFuture.completedFuture(
                HTTPClient.Response(200, "OK", "application/json", """{"experiments":[]}""".toByteArray())
            )

        override fun put(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray) =
            CompletableFuture.completedFuture(HTTPClient.Response(200, "OK", "", ByteArray(0)))

        override fun post(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray) =
            CompletableFuture.completedFuture(HTTPClient.Response(200, "OK", "", ByteArray(0)))

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    @Test
    fun concurrentCloseIsThreadSafe() {
        val mockHttp = MockHTTPClient()
        val clientConfig = ClientConfig.create()
            .setEndpoint("https://test.example.com")
            .setAPIKey("test-key")
            .setApplication("test-app")
            .setEnvironment("test-env")

        val client = Client.create(clientConfig, mockHttp)
        val config = ABSmartlyConfig.create().setClient(client)
        val absmartly = ABsmartly.create(config)

        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val threads = (1..threadCount).map {
            Thread {
                latch.countDown()
                latch.await()
                absmartly.close()
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }

        assertTrue(mockHttp.closeCount.get() <= 1)
    }

    @Test
    fun doubleCloseOnlyClosesClientOnce() {
        val mockHttp = MockHTTPClient()
        val clientConfig = ClientConfig.create()
            .setEndpoint("https://test.example.com")
            .setAPIKey("test-key")
            .setApplication("test-app")
            .setEnvironment("test-env")

        val client = Client.create(clientConfig, mockHttp)
        val config = ABSmartlyConfig.create().setClient(client)
        val absmartly = ABsmartly.create(config)

        absmartly.close()
        absmartly.close()

        assertTrue(mockHttp.closeCount.get() <= 1)
    }
}
