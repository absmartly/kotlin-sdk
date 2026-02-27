package com.absmartly.sdk

import java.util.concurrent.CompletableFuture

class DefaultContextEventHandler(private val client: Client) : ContextEventHandler {
    override fun publish(context: Context, event: PublishEvent): CompletableFuture<Void> {
        return client.publish(event)
    }
}
