package com.absmartly.sdk

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DefaultHTTPClient private constructor(private val executor: Executor) : HTTPClient {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 10000

        @JvmStatic
        fun create(): DefaultHTTPClient {
            val maxThreads = Runtime.getRuntime().availableProcessors() * 2
            val executor = ThreadPoolExecutor(
                0, maxThreads,
                60L, TimeUnit.SECONDS,
                LinkedBlockingQueue()
            )
            return DefaultHTTPClient(executor)
        }

        @JvmStatic
        fun create(executor: Executor): DefaultHTTPClient {
            return DefaultHTTPClient(executor)
        }
    }

    override fun get(url: String, query: Map<String, String>?, headers: Map<String, String>?): CompletableFuture<HTTPClient.Response> {
        return execute(url, query, headers, "GET", null)
    }

    override fun put(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<HTTPClient.Response> {
        return execute(url, query, headers, "PUT", body)
    }

    override fun post(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<HTTPClient.Response> {
        return execute(url, query, headers, "POST", body)
    }

    override fun close() {
        (executor as? ExecutorService)?.shutdown()
    }

    private fun execute(
        url: String,
        query: Map<String, String>?,
        headers: Map<String, String>?,
        method: String,
        body: ByteArray?
    ): CompletableFuture<HTTPClient.Response> {
        return CompletableFuture.supplyAsync({
            val fullUrl = buildUrl(url, query)
            val connection = URI(fullUrl).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = method
                applyHeaders(connection, headers)
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body) }
                }
                readResponse(connection)
            } finally {
                connection.disconnect()
            }
        }, executor)
    }

    private fun buildUrl(base: String, query: Map<String, String>?): String {
        if (query.isNullOrEmpty()) return base
        val queryString = query.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val separator = if (base.contains("?")) "&" else "?"
        return "$base$separator$queryString"
    }

    private fun applyHeaders(connection: HttpURLConnection, headers: Map<String, String>?) {
        headers?.forEach { (k, v) -> connection.setRequestProperty(k, v) }
    }

    private fun readResponse(connection: HttpURLConnection): HTTPClient.Response {
        val statusCode = connection.responseCode
        val statusMessage = connection.responseMessage ?: ""
        val contentType = connection.contentType ?: ""
        val inputStream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val content = inputStream?.use { stream ->
            val buffer = ByteArrayOutputStream()
            stream.copyTo(buffer)
            buffer.toByteArray()
        } ?: ByteArray(0)
        return HTTPClient.Response(statusCode, statusMessage, contentType, content)
    }
}
