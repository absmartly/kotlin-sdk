package com.absmartly.sdk

import java.util.concurrent.CompletableFuture

class DefaultContextPublisher(private val client: Client) : ContextPublisher {
    override fun publish(context: Context, event: PublishEvent): CompletableFuture<Void> {
        return client.publish(event)
    }
}
