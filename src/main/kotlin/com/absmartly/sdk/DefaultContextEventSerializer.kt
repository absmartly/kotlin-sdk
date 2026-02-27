package com.absmartly.sdk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class DefaultContextEventSerializer : ContextEventSerializer {
    private val writer = jacksonObjectMapper().writerFor(PublishEvent::class.java)

    override fun serialize(event: PublishEvent): ByteArray? {
        return try {
            writer.writeValueAsBytes(event)
        } catch (e: Exception) {
            null
        }
    }
}
