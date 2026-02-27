package com.absmartly.sdk

import java.io.Closeable
import java.util.concurrent.CompletableFuture

interface HTTPClient : Closeable {
    data class Response(
        val statusCode: Int,
        val statusMessage: String,
        val contentType: String,
        val content: ByteArray
    )

    fun get(url: String, query: Map<String, String>?, headers: Map<String, String>?): CompletableFuture<Response>
    fun put(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<Response>
    fun post(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<Response>
}
