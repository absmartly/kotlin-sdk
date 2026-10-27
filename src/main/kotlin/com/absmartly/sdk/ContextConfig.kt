package com.absmartly.sdk

class ContextConfig private constructor() {

    companion object {
        @JvmStatic
        fun create(): ContextConfig = ContextConfig()
    }

    private var units: MutableMap<String, Any>? = null
    private var attributes: MutableMap<String, Any>? = null
    private var overrides: MutableMap<String, Int>? = null
    private var cassignments: MutableMap<String, Int>? = null
    var eventLogger: ContextEventLogger? = null
        private set
    var publishDelay: Long = 100
        private set
    var refreshInterval: Long = 0
        private set

    fun setUnit(unitType: String, uid: String): ContextConfig = apply {
        if (units == null) units = mutableMapOf()
        units!![unitType] = uid
    }

    fun setUnit(unitType: String, uid: Number): ContextConfig = apply {
        if (units == null) units = mutableMapOf()
        units!![unitType] = uid
    }

    fun setUnits(units: Map<String, Any>): ContextConfig = apply {
        for ((k, v) in units) {
            when (v) {
                is String -> setUnit(k, v)
                is Number -> setUnit(k, v)
                else -> throw IllegalArgumentException("Unit '$k' must be a string or a number.")
            }
        }
    }

    fun getUnit(unitType: String): Any? = units?.get(unitType)

    fun getUnits(): Map<String, Any>? = units

    fun setAttribute(name: String, value: Any): ContextConfig = apply {
        if (attributes == null) attributes = mutableMapOf()
        attributes!![name] = value
    }

    fun setAttributes(attributes: Map<String, Any>): ContextConfig = apply {
        if (this.attributes == null) this.attributes = mutableMapOf()
        this.attributes!!.putAll(attributes)
    }

    fun getAttribute(name: String): Any? = attributes?.get(name)

    fun getAttributes(): Map<String, Any>? = attributes

    fun setOverride(experimentName: String, variant: Int): ContextConfig = apply {
        if (overrides == null) overrides = mutableMapOf()
        overrides!![experimentName] = variant
    }

    fun setOverrides(overrides: Map<String, Int>): ContextConfig = apply {
        if (this.overrides == null) this.overrides = mutableMapOf()
        this.overrides!!.putAll(overrides)
    }

    fun getOverride(experimentName: String): Int? = overrides?.get(experimentName)

    fun getOverrides(): Map<String, Int>? = overrides

    fun setCustomAssignment(experimentName: String, variant: Int): ContextConfig = apply {
        if (cassignments == null) cassignments = mutableMapOf()
        cassignments!![experimentName] = variant
    }

    fun setCustomAssignments(customAssignments: Map<String, Int>): ContextConfig = apply {
        if (cassignments == null) cassignments = mutableMapOf()
        cassignments!!.putAll(customAssignments)
    }

    fun getCustomAssignment(experimentName: String): Int? = cassignments?.get(experimentName)

    fun getCustomAssignments(): Map<String, Int>? = cassignments

    fun setEventLogger(eventLogger: ContextEventLogger?): ContextConfig = apply { this.eventLogger = eventLogger }

    fun setPublishDelay(delayMs: Long): ContextConfig = apply { this.publishDelay = delayMs }

    fun setRefreshInterval(intervalMs: Long): ContextConfig = apply { this.refreshInterval = intervalMs }
}
