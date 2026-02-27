package com.absmartly.sdk

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class Client private constructor(config: ClientConfig, private val httpClient: HTTPClient) : Closeable {

    companion object {
        @JvmStatic
        fun create(config: ClientConfig): Client {
            return Client(config, DefaultHTTPClient.create())
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

        url = "$endpoint/context"
        executor = config.executor
        deserializer = config.deserializer ?: DefaultContextDataDeserializer()
        serializer = config.serializer ?: DefaultContextEventSerializer()

        headers = mapOf(
            "X-API-Key" to apiKey,
            "X-Application" to application,
            "X-Environment" to environment,
            "X-Application-Version" to "0",
            "X-Agent" to "absmartly-kotlin-sdk"
        )

        query = mapOf(
            "application" to application,
            "environment" to environment
        )
    }

    fun getContextData(): CompletableFuture<ContextData> {
        val dataFuture = CompletableFuture<ContextData>()

        httpClient.get(url, query, null).thenAccept { response ->
            val code = response.statusCode
            if (code / 100 == 2) {
                val content = response.content
                if (content.isEmpty()) {
                    dataFuture.completeExceptionally(
                        IllegalStateException("Empty response body from context data endpoint")
                    )
                } else {
                    val data = deserializer.deserialize(content, 0, content.size)
                    if (data != null) {
                        dataFuture.complete(data)
                    } else {
                        dataFuture.completeExceptionally(
                            IllegalStateException("Failed to deserialize context data")
                        )
                    }
                }
            } else {
                dataFuture.completeExceptionally(Exception(response.statusMessage))
            }
        }.exceptionally { exception ->
            dataFuture.completeExceptionally(exception)
            null
        }

        return dataFuture
    }

    fun publish(event: PublishEvent): CompletableFuture<Void> {
        val publishFuture = CompletableFuture<Void>()

        CompletableFuture.supplyAsync({
            serializer.serialize(event)
        }, executor ?: CompletableFuture.completedFuture(null).defaultExecutor()).thenCompose { content ->
            if (content != null) {
                httpClient.put(url, null, headers, content)
            } else {
                throw IllegalStateException("Failed to serialize publish event")
            }
        }.thenAccept { response ->
            val code = response.statusCode
            if (code / 100 == 2) {
                publishFuture.complete(null)
            } else {
                publishFuture.completeExceptionally(Exception(response.statusMessage))
            }
        }.exceptionally { exception ->
            publishFuture.completeExceptionally(exception)
            null
        }

        return publishFuture
    }

    override fun close() {
        httpClient.close()
    }
}
