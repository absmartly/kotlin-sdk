package com.absmartly.sdk

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

class Client private constructor(config: ClientConfig, private val httpClient: HTTPClient) : Closeable {

    companion object {
        internal fun applicationVersionHeader(version: String): String =
            if (version.isNotEmpty() && version.length <= 18 && version.all { it in '0'..'9' }) version else "0"
        @JvmStatic
        fun create(config: ClientConfig): Client {
            val httpClient = DefaultHTTPClient.create()
            try {
                return Client(config, httpClient)
            } catch (e: Throwable) {
                httpClient.close()
                throw e
            }
        }

        @JvmStatic
        fun create(config: ClientConfig, httpClient: HTTPClient): Client {
            return Client(config, httpClient)
        }
    }

    private val url: String
    private val query: Map<String, String>
    private val headers: Map<String, String>
    private val executor: Executor?
    private var deserializer: ContextDataDeserializer
    private var serializer: ContextEventSerializer

    // The configured application and environment names, made available to audiences and rule conditions.
    val application: String
    val environment: String
    val applicationVersion: String
    val agentVersion: String get() = SdkVersion.VERSION
    val agent: String get() = SdkVersion.AGENT

    init {
        val endpoint = config.endpoint
        require(!endpoint.isNullOrEmpty()) { "Missing Endpoint configuration" }

        if (!endpoint.startsWith("https://")) {
            if (endpoint.startsWith("http://")) {
                System.err.println(
                    "WARNING: ABSmartly SDK endpoint is not using HTTPS. API keys will be transmitted in plaintext: $endpoint"
                )
            } else {
                throw IllegalArgumentException("Endpoint must use http:// or https:// protocol: $endpoint")
            }
        }

        val apiKey = config.apiKey
        require(!apiKey.isNullOrEmpty()) { "Missing APIKey configuration" }

        val application = config.application
        require(!application.isNullOrEmpty()) { "Missing Application configuration" }

        val environment = config.environment
        require(!environment.isNullOrEmpty()) { "Missing Environment configuration" }

        this.application = application
        this.environment = environment
        this.applicationVersion = config.applicationVersion ?: "0"

        url = "$endpoint/context"
        executor = config.executor
        deserializer = config.deserializer ?: DefaultContextDataDeserializer()
        serializer = config.serializer ?: DefaultContextEventSerializer()

        headers = mapOf(
            "X-API-Key" to apiKey,
            "X-Application" to application,
            "X-Environment" to environment,
            // The collector parses this header as a whole number and rejects anything else, so a version such as
            // "2.4.1" is sent as "0"; the full version is still used for the app_version system attribute.
            "X-Application-Version" to applicationVersionHeader(applicationVersion),
            "X-Agent" to SdkVersion.AGENT
        )

        query = mapOf(
            "application" to application,
            "environment" to environment
        )
    }

    fun getContextData(): CompletableFuture<ContextData> {
        return httpClient.get(url, query, headers).thenApply { response ->
            val code = response.statusCode
            if (code / 100 != 2) {
                throw Exception("HTTP $code: ${response.statusMessage} - ${String(response.content ?: ByteArray(0))}")
            }
            val content = response.content
            if (content.isEmpty()) {
                throw IllegalStateException("Empty response body from context data endpoint")
            }
            deserializer.deserialize(content, 0, content.size)
                ?: throw IllegalStateException("Failed to deserialize context data")
        }
    }

    fun publish(event: PublishEvent): CompletableFuture<Void> {
        return CompletableFuture.supplyAsync({
            serializer.serialize(event)
        }, executor ?: ForkJoinPool.commonPool()).thenCompose { content ->
            if (content != null) {
                httpClient.put(url, null, headers, content)
            } else {
                throw IllegalStateException("Failed to serialize publish event")
            }
        }.thenAccept { response ->
            val code = response.statusCode
            if (code / 100 != 2) {
                throw Exception("HTTP $code: ${response.statusMessage} - ${String(response.content ?: ByteArray(0))}")
            }
        }
    }

    override fun close() {
        httpClient.close()
    }
}
