package com.absmartly.sdk

import java.io.Closeable
import java.util.concurrent.CompletableFuture

interface HTTPClient : Closeable {
    class Response(
        val statusCode: Int,
        val statusMessage: String,
        val contentType: String,
        val content: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Response) return false
            return statusCode == other.statusCode &&
                    statusMessage == other.statusMessage &&
                    contentType == other.contentType &&
                    content.contentEquals(other.content)
        }

        override fun hashCode(): Int {
            var result = statusCode
            result = 31 * result + statusMessage.hashCode()
            result = 31 * result + contentType.hashCode()
            result = 31 * result + content.contentHashCode()
            return result
        }
    }

    fun get(url: String, query: Map<String, String>?, headers: Map<String, String>?): CompletableFuture<Response>
    fun put(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<Response>
    fun post(url: String, query: Map<String, String>?, headers: Map<String, String>?, body: ByteArray): CompletableFuture<Response>
}
