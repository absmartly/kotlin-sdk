package com.absmartly.sdk

import java.util.concurrent.Executor

class ClientConfig private constructor() {

    companion object {
        @JvmStatic
        fun create(): ClientConfig = ClientConfig()
    }

    var endpoint: String? = null
        private set
    var apiKey: String? = null
        private set
    var application: String? = null
        private set
    var environment: String? = null
        private set
    var deserializer: ContextDataDeserializer? = null
        private set
    var serializer: ContextEventSerializer? = null
        private set
    var executor: Executor? = null
        private set

    fun setEndpoint(endpoint: String): ClientConfig = apply { this.endpoint = endpoint }
    fun setAPIKey(apiKey: String): ClientConfig = apply { this.apiKey = apiKey }
    fun setApplication(application: String): ClientConfig = apply { this.application = application }
    fun setEnvironment(environment: String): ClientConfig = apply { this.environment = environment }
    fun setContextDataDeserializer(deserializer: ContextDataDeserializer): ClientConfig = apply { this.deserializer = deserializer }
    fun setContextEventSerializer(serializer: ContextEventSerializer): ClientConfig = apply { this.serializer = serializer }
    fun setExecutor(executor: Executor): ClientConfig = apply { this.executor = executor }
}
