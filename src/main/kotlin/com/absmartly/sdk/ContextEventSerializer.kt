package com.absmartly.sdk

interface ContextEventSerializer {
    fun serialize(event: PublishEvent): ByteArray?
}
