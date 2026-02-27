package com.absmartly.sdk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class DefaultContextDataDeserializer : ContextDataDeserializer {
    private val reader = jacksonObjectMapper().readerFor(ContextData::class.java)

    override fun deserialize(bytes: ByteArray, offset: Int, length: Int): ContextData? {
        return try {
            reader.readValue(bytes, offset, length)
        } catch (e: Exception) {
            null
        }
    }
}
