package com.absmartly.sdk

import java.util.concurrent.CompletableFuture

interface ContextEventHandler {
    fun publish(context: Context, event: PublishEvent): CompletableFuture<Void>
}
