package com.absmartly.sdk

interface ContextDataDeserializer {
    fun deserialize(bytes: ByteArray, offset: Int, length: Int): ContextData?
}
