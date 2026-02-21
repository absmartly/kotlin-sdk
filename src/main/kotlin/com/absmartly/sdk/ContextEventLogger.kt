package com.absmartly.sdk

interface ContextEventLogger {
    enum class EventType {
        Error, Ready, Refresh, Publish, Exposure, Goal, Close
    }

    fun handleEvent(context: Context, type: EventType, data: Any?)
}
