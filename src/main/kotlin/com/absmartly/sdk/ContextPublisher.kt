package com.absmartly.sdk

import java.util.concurrent.CompletableFuture

interface ContextPublisher {
    fun publish(context: Context, event: PublishEvent): CompletableFuture<Void>
}
