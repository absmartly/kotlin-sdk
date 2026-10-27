package com.absmartly.sdk

import java.util.concurrent.CompletableFuture

interface ContextDataProvider {
    fun getContextData(): CompletableFuture<ContextData>
}
