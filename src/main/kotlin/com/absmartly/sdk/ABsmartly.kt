package com.absmartly.sdk

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ABsmartly private constructor(config: ABSmartlyConfig) : Closeable {

    companion object {
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
