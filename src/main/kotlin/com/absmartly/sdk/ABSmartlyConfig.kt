package com.absmartly.sdk

class ABSmartlyConfig private constructor() {

    companion object {
        @JvmStatic
        fun create(): ABSmartlyConfig = ABSmartlyConfig()
    }

    var client: Client? = null
        private set
    var contextDataProvider: ContextDataProvider? = null
        private set
    var contextEventHandler: ContextPublisher? = null
        private set
    var contextEventLogger: ContextEventLogger? = null
        private set

    fun setClient(client: Client): ABSmartlyConfig = apply { this.client = client }
    fun setContextDataProvider(provider: ContextDataProvider): ABSmartlyConfig = apply { this.contextDataProvider = provider }
    fun setContextEventHandler(handler: ContextPublisher): ABSmartlyConfig = apply { this.contextEventHandler = handler }
    fun setContextEventLogger(logger: ContextEventLogger): ABSmartlyConfig = apply { this.contextEventLogger = logger }
}
