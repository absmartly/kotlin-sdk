package com.absmartly.sdk

import java.util.concurrent.CompletableFuture

class DefaultContextDataProvider(private val client: Client) : ContextDataProvider {
    override fun getContextData(): CompletableFuture<ContextData> {
        return client.getContextData()
    }
}
