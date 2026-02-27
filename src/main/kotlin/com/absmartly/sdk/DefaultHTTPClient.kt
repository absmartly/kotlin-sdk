package com.absmartly.sdk

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class DefaultHTTPClient private constructor(private val executor: Executor) : HTTPClient {

    companion object {
        @JvmStatic
        fun create(): DefaultHTTPClient {
            return DefaultHTTPClient(Executors.newCachedThreadPool())
        }

        @JvmStatic
        fun create(executor: Executor): DefaultHTTPClient {
            return DefaultHTTPClient(executor)
        }
    }

    override fun get(url: String, query: Map<String, String>?, headers: Map<String, String>?): CompletableFuture<HTTPClient.Response> {
        return CompletableFuture.supplyAsync({
            val fullUrl = buildUrl(url, query)
            val connection = URL(fullUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                applyHeaders(connection, headers)
                readResponse(connection)
            } finally {
                connection.disconnect()
            }
        }, executor)
    }

    override fun put(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<HTTPClient.Response> {
        return CompletableFuture.supplyAsync({
            val fullUrl = buildUrl(url, query)
            val connection = URL(fullUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "PUT"
                connection.doOutput = true
                applyHeaders(connection, headers)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body) }
                readResponse(connection)
            } finally {
                connection.disconnect()
            }
        }, executor)
    }

    override fun post(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<HTTPClient.Response> {
        return CompletableFuture.supplyAsync({
            val fullUrl = buildUrl(url, query)
            val connection = URL(fullUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                applyHeaders(connection, headers)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body) }
                readResponse(connection)
            } finally {
                connection.disconnect()
            }
        }, executor)
    }

    override fun close() {}

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
