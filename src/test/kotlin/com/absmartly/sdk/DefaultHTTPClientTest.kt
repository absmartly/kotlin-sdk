package com.absmartly.sdk

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultHTTPClientTest {

    @Test
    fun closeShutdownsDefaultExecutor() {
        val client = DefaultHTTPClient.create()
        client.close()
    }

    @Test
    fun closeShutdownsProvidedExecutorService() {
        val executor = Executors.newFixedThreadPool(2)
        val client = DefaultHTTPClient.create(executor)
        assertFalse(executor.isShutdown)
        client.close()
        assertTrue(executor.isShutdown)
    }

    @Test
    fun closeIsNoOpForNonExecutorServiceExecutor() {
        val executor = java.util.concurrent.Executor { it.run() }
        val client = DefaultHTTPClient.create(executor)
        client.close()
    }

    @Test
    fun createUsesDefaultBoundedThreadPool() {
        val client = DefaultHTTPClient.create()
        client.close()
    }

    @Test
    fun createAcceptsCustomExecutor() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val client = DefaultHTTPClient.create(executor)
            client.close()
        } finally {
            executor.shutdownNow()
        }
    }
}
