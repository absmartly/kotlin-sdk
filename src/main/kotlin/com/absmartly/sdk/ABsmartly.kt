package com.absmartly.sdk

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ABsmartly private constructor(config: ABSmartlyConfig) : Closeable {

    class Builder {
        private var endpoint: String? = null
        private var apiKey: String? = null
        private var application: String? = null
        private var environment: String? = null
        private var eventLogger: ContextEventLogger? = null

        fun endpoint(endpoint: String) = apply { this.endpoint = endpoint }
        fun apiKey(apiKey: String) = apply { this.apiKey = apiKey }
        fun application(application: String) = apply { this.application = application }
        fun environment(environment: String) = apply { this.environment = environment }
        fun eventLogger(eventLogger: ContextEventLogger) = apply { this.eventLogger = eventLogger }

        fun build(): ABsmartly {
            val endpoint = requireNotNull(endpoint) { "endpoint is required" }
            val apiKey = requireNotNull(apiKey) { "apiKey is required" }
            val application = requireNotNull(application) { "application is required" }
            val environment = requireNotNull(environment) { "environment is required" }

            val clientConfig = ClientConfig.create()
                .setEndpoint(endpoint)
                .setAPIKey(apiKey)
                .setApplication(application)
                .setEnvironment(environment)

            val config = ABSmartlyConfig.create()
                .setClient(Client.create(clientConfig))

            if (eventLogger != null) {
                config.setContextEventLogger(eventLogger!!)
            }

            return create(config)
        }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()

        @JvmStatic
        fun create(config: ABSmartlyConfig): ABsmartly = ABsmartly(config)
    }

    private var client: Client?
    private var contextDataProvider: ContextDataProvider
    private var contextEventHandler: ContextEventHandler
    private val contextEventLogger: ContextEventLogger?
    private var scheduler: ScheduledExecutorService

    init {
        contextEventLogger = config.contextEventLogger

        var provider = config.contextDataProvider
        var handler = config.contextEventHandler

        if (provider == null || handler == null) {
            client = config.client ?: throw IllegalArgumentException("Missing Client instance")

            if (provider == null) {
                provider = DefaultContextDataProvider(client!!)
            }

            if (handler == null) {
                handler = DefaultContextEventHandler(client!!)
            }
        } else {
            client = config.client
        }

        contextDataProvider = provider
        contextEventHandler = handler
        scheduler = ScheduledThreadPoolExecutor(1)
    }

    fun createContext(config: ContextConfig): Context {
        return Context.create(
            config = config,
            dataFuture = contextDataProvider.getContextData(),
            dataProvider = contextDataProvider,
            eventHandler = contextEventHandler,
            eventLogger = contextEventLogger,
            scheduler = scheduler
        )
    }

    fun createContextWith(config: ContextConfig, data: ContextData): Context {
        return Context.create(
            config = config,
            dataFuture = CompletableFuture.completedFuture(data),
            dataProvider = contextDataProvider,
            eventHandler = contextEventHandler,
            eventLogger = contextEventLogger,
            scheduler = scheduler
        )
    }

    fun getContextData(): CompletableFuture<ContextData> {
        return contextDataProvider.getContextData()
    }

    override fun close() {
        if (client != null) {
            client!!.close()
            client = null
        }

        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            scheduler.shutdownNow()
        }
    }
}
