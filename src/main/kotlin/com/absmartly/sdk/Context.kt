package com.absmartly.sdk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class Context private constructor(
    private val units: MutableMap<String, String>,
    private val eventLogger: ContextEventLogger?,
    private val eventHandler: ContextPublisher?,
    private val dataProvider: ContextDataProvider?,
    private val publishDelay: Long,
    private val refreshInterval: Long,
    private val scheduler: ScheduledExecutorService?
) : Closeable {

    constructor(
        data: ContextData,
        units: MutableMap<String, String>,
        options: ContextOptions,
        eventLogger: ContextEventLogger? = null,
        startReady: Boolean = true
    ) : this(
        units = units,
        eventLogger = eventLogger,
        eventHandler = null,
        dataProvider = null,
        publishDelay = options.publishDelay,
        refreshInterval = options.refreshPeriod,
        scheduler = null
    ) {
        if (startReady) {
            setData(data)
            ready_ = true
        }
    }

    companion object {
        private val COMPLETED_VOID_FUTURE: CompletableFuture<Void> = CompletableFuture.completedFuture(null)

        internal fun create(
            config: ContextConfig,
            dataFuture: CompletableFuture<ContextData>,
            dataProvider: ContextDataProvider?,
            eventHandler: ContextPublisher?,
            eventLogger: ContextEventLogger?,
            scheduler: ScheduledExecutorService?
        ): Context {
            val units = mutableMapOf<String, String>()
            config.getUnits()?.let { units.putAll(it) }

            val configLogger = config.eventLogger ?: eventLogger

            val context = Context(
                units = units,
                eventLogger = configLogger,
                eventHandler = eventHandler,
                dataProvider = dataProvider,
                publishDelay = config.publishDelay,
                refreshInterval = config.refreshInterval,
                scheduler = scheduler
            )

            config.getOverrides()?.forEach { (k, v) -> context.overrides[k] = v }
            config.getCustomAssignments()?.forEach { (k, v) -> context.cassignments[k] = v }
            config.getAttributes()?.forEach { (k, v) ->
                context.setAttribute(k, v)
            }

            if (dataFuture.isDone) {
                dataFuture.thenAccept { data ->
                    context.setData(data)
                    context.logEvent(ContextEventLogger.EventType.Ready, data)
                }.exceptionally { exception ->
                    context.setDataFailed(exception)
                    null
                }
            } else {
                val readyFuture = CompletableFuture<Void>()
                context.readyFuture_.set(readyFuture)
                dataFuture.thenAccept { data ->
                    context.setData(data)
                    val rf = context.readyFuture_.getAndSet(COMPLETED_VOID_FUTURE)
                    rf?.complete(null)
                    context.logEvent(ContextEventLogger.EventType.Ready, data)
                }.exceptionally { exception ->
                    context.setDataFailed(exception)
                    val rf = context.readyFuture_.getAndSet(COMPLETED_VOID_FUTURE)
                    rf?.complete(null)
                    null
                }
            }

            return context
        }
    }

    private val objectMapper = jacksonObjectMapper()
    private val assignmentCache = ConcurrentHashMap<String, Assignment>()
    private val overrides = ConcurrentHashMap<String, Int>()
    private val cassignments = ConcurrentHashMap<String, Int>()
    private val attributes_ = mutableListOf<Attribute>()
    private val exposures_ = ConcurrentLinkedQueue<Exposure>()
    private val achievements_ = ConcurrentLinkedQueue<GoalAchievement>()
    private val hashedUnits_ = ConcurrentHashMap<String, ByteArray>()
    private val assigners_ = ConcurrentHashMap<String, VariantAssigner>()

    private val audienceMatcher = AudienceMatcher()

    private var data: ContextData = ContextData()
    private var index_ = mutableMapOf<String, ContextExperiment>()
    private var indexVariables_ = mutableMapOf<String, MutableList<ContextExperiment>>()

    @Volatile private var ready_ = false
    @Volatile private var failed_ = false
    @Volatile private var readyError_: Throwable? = null
    private val closed_ = AtomicBoolean(false)
    private val closing_ = AtomicBoolean(false)

    private val attrsSeq_ = AtomicInteger(0)
    private val pendingCount_ = AtomicInteger(0)

    private val readyFuture_ = AtomicReference<CompletableFuture<Void>?>(null)

    private class Assignment {
        var id: Int = 0
        var iteration: Int = 0
        var fullOnVariant: Int = 0
        var name: String = ""
        var unitType: String? = null
        var trafficSplit: DoubleArray = doubleArrayOf()
        var variant: Int = 0
        var assigned: Boolean = false
        var overridden: Boolean = false
        var eligible: Boolean = true
        var fullOn: Boolean = false
        var custom: Boolean = false
        var audienceMismatch: Boolean = false
        var variables: Map<String, Any?>? = null
        var attrsSeq: Int = 0
        val exposed: AtomicBoolean = AtomicBoolean(false)
    }

    private class ContextExperiment(
        val data: Experiment,
        val variables: List<Map<String, Any?>>
    )

    val isReady: Boolean get() = ready_
    val isFailed: Boolean get() = failed_
    val isClosed: Boolean get() = closed_.get()
    val isClosing: Boolean get() = !closed_.get() && closing_.get()
    val isFinalized: Boolean get() = isClosed
    val isFinalizing: Boolean get() = isClosing

    val pendingCount: Int get() = pendingCount_.get()

    fun readyError(): Throwable? = readyError_

    fun getUnits(): Map<String, String> = HashMap(units)

    fun getAttributes(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        synchronized(attributes_) {
            for (attr in attributes_) {
                result[attr.name] = attr.value
            }
        }
        return result
    }

    fun setUnits(newUnits: Map<String, String>) {
        for ((k, v) in newUnits) {
            setUnit(k, v)
        }
    }

    fun setAttributes(newAttributes: Map<String, Any?>) {
        for ((k, v) in newAttributes) {
            setAttribute(k, v)
        }
    }

    fun setOverrides(newOverrides: Map<String, Int>) {
        for ((k, v) in newOverrides) {
            setOverride(k, v)
        }
    }

    fun setCustomAssignments(newAssignments: Map<String, Int>) {
        for ((k, v) in newAssignments) {
            setCustomAssignment(k, v)
        }
    }

    fun waitUntilReady(): Context {
        if (!ready_) {
            val future = readyFuture_.get()
            if (future != null && !future.isDone) {
                future.join()
            }
        }
        return this
    }

    fun waitUntilReadyAsync(): CompletableFuture<Context> {
        if (ready_) {
            return CompletableFuture.completedFuture(this)
        }
        val rf = readyFuture_.get()
        return if (rf != null) {
            rf.thenApply { this }
        } else {
            CompletableFuture.completedFuture(this)
        }
    }

    val experiments: List<String>
        get() {
            if (!ready_ || closed_.get() || closing_.get()) return emptyList()
            return data.experiments.map { it.name }
        }

    val variableKeys: Map<String, List<String>>
        get() {
            if (!ready_ || closed_.get() || closing_.get()) return emptyMap()
            val result = mutableMapOf<String, List<String>>()
            for ((key, exps) in indexVariables_) {
                result[key] = exps.map { it.data.name }
            }
            return result
        }

    val customFieldKeys: Set<String>
        get() {
            if (!ready_ || closed_.get() || closing_.get()) return emptySet()
            val keys = mutableSetOf<String>()
            for (experiment in data.experiments) {
                experiment.customFieldValues?.forEach { keys.add(it.name) }
            }
            return keys
        }

    fun setUnit(unitType: String, uid: String) {
        checkNotClosed()
        val uidStr = uid.trim()
        val previous = units[unitType]
        if (previous != null && previous != uidStr) {
            throw IllegalArgumentException("Unit '$unitType' UID already set.")
        }
        if (uidStr.isEmpty()) {
            throw IllegalArgumentException("Unit '$unitType' UID must not be blank.")
        }
        units[unitType] = uidStr
    }

    fun getUnit(unitType: String): String? = units[unitType]

    fun setAttribute(name: String, value: Any?) {
        checkNotClosed()
        synchronized(attributes_) {
            attributes_.add(Attribute(name, value, System.currentTimeMillis()))
        }
        attrsSeq_.incrementAndGet()
    }

    fun getAttribute(name: String): Any? {
        synchronized(attributes_) {
            for (i in attributes_.indices.reversed()) {
                if (attributes_[i].name == name) return attributes_[i].value
            }
        }
        return null
    }

    fun setOverride(experimentName: String, variant: Int) {
        overrides[experimentName] = variant
    }

    fun setCustomAssignment(experimentName: String, variant: Int) {
        checkNotClosed()
        cassignments[experimentName] = variant
    }

    fun getTreatment(experimentName: String): Int {
        if (!ready_ || closed_.get() || closing_.get()) return 0
        val assignment = getAssignment(experimentName)
        if (!assignment.exposed.get()) {
            queueExposure(assignment)
        }
        return assignment.variant
    }

    fun peekTreatment(experimentName: String): Int {
        if (!ready_ || closed_.get() || closing_.get()) return 0
        return getAssignment(experimentName).variant
    }

    fun getVariableValue(key: String, defaultValue: Any?): Any? {
        if (!ready_ || closed_.get() || closing_.get()) return defaultValue
        val assignment = getVariableAssignment(key)
        if (assignment != null && assignment.variables != null) {
            if (!assignment.exposed.get()) {
                queueExposure(assignment)
            }
            if (assignment.variables!!.containsKey(key)) {
                return assignment.variables!![key]
            }
        }
        return defaultValue
    }

    fun peekVariableValue(key: String, defaultValue: Any?): Any? {
        if (!ready_ || closed_.get() || closing_.get()) return defaultValue
        val assignment = getVariableAssignment(key)
        if (assignment != null && assignment.variables != null) {
            if (assignment.variables!!.containsKey(key)) {
                return assignment.variables!![key]
            }
        }
        return defaultValue
    }

    fun getCustomFieldValue(experimentName: String, key: String): Any? {
        if (!ready_ || closed_.get() || closing_.get()) return null
        val experiment = index_[experimentName] ?: return null
        val field = experiment.data.customFieldValues?.find { it.name == key } ?: return null
        if (field.value == null) return null

        return when {
            field.type != null && field.type!!.startsWith("json") -> {
                try {
                    objectMapper.readValue(field.value, Any::class.java)
                } catch (e: Exception) {
                    field.value
                }
            }
            field.type == "boolean" -> field.value.toBoolean()
            field.type == "number" -> {
                try {
                    field.value!!.toDouble()
                } catch (e: NumberFormatException) {
                    field.value
                }
            }
            else -> field.value
        }
    }

    fun getCustomFieldValueType(experimentName: String, key: String): String? {
        if (!ready_ || closed_.get() || closing_.get()) return null
        val experiment = index_[experimentName] ?: return null
        val field = experiment.data.customFieldValues?.find { it.name == key } ?: return null
        return field.type
    }

    fun track(goalName: String, properties: Map<String, Any?>?) {
        checkNotClosed()
        val achievement = GoalAchievement(
            name = goalName,
            achievedAt = System.currentTimeMillis(),
            properties = properties?.let { java.util.TreeMap(it) }
        )
        achievements_.add(achievement)
        pendingCount_.incrementAndGet()
        logEvent(ContextEventLogger.EventType.Goal, achievement)
    }

    fun publish(): CompletableFuture<Void> {
        checkNotClosed()
        return flush()
    }

    override fun close() {
        if (!closed_.get() && closing_.compareAndSet(false, true)) {
            try {
                if (pendingCount_.get() > 0) {
                    flush().join()
                }
                closed_.set(true)
                logEvent(ContextEventLogger.EventType.Close, null)
            } finally {
                closing_.set(false)
            }
        }
    }

    @Deprecated("Use close() instead", ReplaceWith("close()"))
    fun finalize() {
        close()
    }

    fun setDataAndReady(newData: ContextData) {
        setData(newData)
        ready_ = true
        logEvent(ContextEventLogger.EventType.Ready, newData)
    }

    fun refresh(): CompletableFuture<Void> {
        if (dataProvider == null) {
            return CompletableFuture.completedFuture(null)
        }
        return dataProvider.getContextData().thenAccept { newData ->
            setData(newData)
            logEvent(ContextEventLogger.EventType.Refresh, newData)
        }
    }

    fun refresh(newData: ContextData) {
        setData(newData)
        logEvent(ContextEventLogger.EventType.Refresh, newData)
    }

    internal fun setData(data: ContextData) {
        this.data = data
        val newIndex = mutableMapOf<String, ContextExperiment>()
        val newVarIndex = mutableMapOf<String, MutableList<ContextExperiment>>()

        for (experiment in data.experiments) {
            val variantVariables = mutableListOf<Map<String, Any?>>()
            for (variant in experiment.variants) {
                if (variant.config != null && variant.config!!.isNotEmpty()) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val vars = objectMapper.readValue(variant.config, Map::class.java) as Map<String, Any?>
                        variantVariables.add(vars)

                        val indexed = ContextExperiment(experiment, variantVariables)
                        for (key in vars.keys) {
                            val list = newVarIndex.getOrPut(key) { mutableListOf() }
                            val existing = list.find { it.data.name == experiment.name }
                            if (existing == null) {
                                val insertAt = list.indexOfFirst { it.data.id > experiment.id }
                                if (insertAt < 0) list.add(indexed) else list.add(insertAt, indexed)
                            }
                        }
                    } catch (e: Exception) {
                        variantVariables.add(emptyMap())
                    }
                } else {
                    variantVariables.add(emptyMap())
                }
            }
            newIndex[experiment.name] = ContextExperiment(experiment, variantVariables)
        }

        index_ = newIndex
        indexVariables_ = newVarIndex
        ready_ = true

        val iter = assignmentCache.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val assignment = entry.value
            val experiment = newIndex[entry.key]
            if (experiment == null) {
                if (assignment.assigned && !assignment.overridden) iter.remove()
            } else if (!experimentMatches(experiment.data, assignment)) {
                if (assignment.overridden) {
                    continue
                } else {
                    iter.remove()
                }
            }
        }
    }

    private fun setDataFailed(exception: Throwable) {
        failed_ = true
        readyError_ = exception
        ready_ = true
        logEvent(ContextEventLogger.EventType.Error, exception)
    }

    private fun getAssignment(experimentName: String): Assignment {
        val cached = assignmentCache[experimentName]

        if (cached != null) {
            val custom = cassignments[experimentName]
            val override = overrides[experimentName]
            val experiment = index_[experimentName]

            if (override != null) {
                if (cached.overridden && cached.variant == override) {
                    return cached
                }
            } else if (experiment == null) {
                if (!cached.assigned) {
                    return cached
                }
            } else if (custom == null || custom == cached.variant) {
                if (experimentMatches(experiment.data, cached) && audienceMatches(experiment.data, cached)) {
                    return cached
                }
            }
        }

        val custom = cassignments[experimentName]
        val override = overrides[experimentName]
        val experiment = index_[experimentName]

        val assignment = Assignment()
        assignment.name = experimentName
        assignment.eligible = true

        if (override != null) {
            if (experiment != null) {
                assignment.id = experiment.data.id
                assignment.unitType = experiment.data.unitType
            }
            assignment.overridden = true
            assignment.variant = override
        } else {
            if (experiment != null) {
                val unitType = experiment.data.unitType

                if (experiment.data.audience != null && experiment.data.audience!!.isNotEmpty()) {
                    val attrs = buildAttributesMap()
                    val match = audienceMatcher.evaluate(experiment.data.audience!!, attrs)
                    if (match != null) {
                        assignment.audienceMismatch = !match.value
                    }
                }

                if (experiment.data.audienceStrict && assignment.audienceMismatch) {
                    assignment.variant = 0
                } else if (experiment.data.fullOnVariant == 0) {
                    val uid = units[experiment.data.unitType]
                    if (uid != null) {
                        val unitHash = getUnitHash(unitType!!, uid)
                        val assigner = getVariantAssigner(unitType, unitHash)
                        val eligible = assigner.assign(
                            experiment.data.trafficSplit,
                            experiment.data.trafficSeedHi,
                            experiment.data.trafficSeedLo
                        ) == 1
                        if (eligible) {
                            if (custom != null) {
                                assignment.variant = custom
                                assignment.custom = true
                            } else {
                                assignment.variant = assigner.assign(
                                    experiment.data.split,
                                    experiment.data.seedHi,
                                    experiment.data.seedLo
                                )
                            }
                        } else {
                            assignment.eligible = false
                            assignment.variant = 0
                        }
                        assignment.assigned = true
                    }
                } else {
                    assignment.assigned = true
                    assignment.variant = experiment.data.fullOnVariant
                    assignment.fullOn = true
                }

                assignment.unitType = unitType
                assignment.id = experiment.data.id
                assignment.iteration = experiment.data.iteration
                assignment.trafficSplit = experiment.data.trafficSplit
                assignment.fullOnVariant = experiment.data.fullOnVariant
                assignment.attrsSeq = attrsSeq_.get()
            }
        }

        if (experiment != null && assignment.variant >= 0 && assignment.variant < experiment.data.variants.size) {
            if (assignment.variant < experiment.variables.size) {
                assignment.variables = experiment.variables[assignment.variant]
            }
        }

        assignmentCache[experimentName] = assignment
        return assignment
    }

    private fun getVariableAssignment(key: String): Assignment? {
        val keyExperiments = indexVariables_[key] ?: return null
        for (experiment in keyExperiments) {
            val assignment = getAssignment(experiment.data.name)
            if (assignment.assigned || assignment.overridden) {
                return assignment
            }
        }
        return null
    }

    private fun experimentMatches(experiment: Experiment, assignment: Assignment): Boolean {
        return experiment.id == assignment.id &&
                experiment.unitType != null && experiment.unitType == assignment.unitType &&
                experiment.iteration == assignment.iteration &&
                experiment.fullOnVariant == assignment.fullOnVariant &&
                experiment.trafficSplit.contentEquals(assignment.trafficSplit)
    }

    private fun audienceMatches(experiment: Experiment, assignment: Assignment): Boolean {
        if (experiment.audience != null && experiment.audience!!.isNotEmpty()) {
            if (attrsSeq_.get() > assignment.attrsSeq) {
                val attrs = buildAttributesMap()
                val match = audienceMatcher.evaluate(experiment.audience!!, attrs)
                val newAudienceMismatch = if (match != null) !match.value else false
                if (newAudienceMismatch != assignment.audienceMismatch) {
                    return false
                }
            }
        }
        return true
    }

    private fun queueExposure(assignment: Assignment) {
        if (assignment.exposed.compareAndSet(false, true)) {
            val exposure = Exposure(
                id = assignment.id,
                name = assignment.name,
                unit = assignment.unitType,
                variant = assignment.variant,
                exposedAt = System.currentTimeMillis(),
                assigned = assignment.assigned,
                eligible = assignment.eligible,
                overridden = assignment.overridden,
                fullOn = assignment.fullOn,
                custom = assignment.custom,
                audienceMismatch = assignment.audienceMismatch
            )
            exposures_.add(exposure)
            pendingCount_.incrementAndGet()
            logEvent(ContextEventLogger.EventType.Exposure, exposure)
        }
    }

    private fun flush(): CompletableFuture<Void> {
        if (pendingCount_.get() <= 0) {
            return CompletableFuture.completedFuture(null)
        }

        val exposureList = mutableListOf<Exposure>()
        val goalList = mutableListOf<GoalAchievement>()

        while (true) {
            val e = exposures_.poll() ?: break
            exposureList.add(e)
        }
        while (true) {
            val g = achievements_.poll() ?: break
            goalList.add(g)
        }
        val drained = exposureList.size + goalList.size
        pendingCount_.addAndGet(-drained)

        val unitList = units.map { (type, uid) ->
            com.absmartly.sdk.Unit(type, String(getUnitHash(type, uid), Charsets.US_ASCII))
        }

        val attrList = synchronized(attributes_) {
            if (attributes_.isEmpty()) null else attributes_.toList()
        }

        val event = PublishEvent(
            hashed = true,
            publishedAt = System.currentTimeMillis(),
            units = unitList,
            exposures = exposureList.ifEmpty { null },
            goals = goalList.ifEmpty { null },
            attributes = attrList
        )

        logEvent(ContextEventLogger.EventType.Publish, event)

        if (eventHandler != null) {
            return eventHandler.publish(this, event).exceptionally { exception ->
                for (exposure in exposureList) {
                    exposures_.add(exposure)
                }
                for (goal in goalList) {
                    achievements_.add(goal)
                }
                pendingCount_.addAndGet(drained)
                logEvent(ContextEventLogger.EventType.Error, exception)
                null
            }
        }

        return CompletableFuture.completedFuture(null)
    }

    private fun getUnitHash(unitType: String, uid: String): ByteArray {
        return hashedUnits_.computeIfAbsent(unitType) {
            Hashing.hashUnit(uid)
        }
    }

    private fun getVariantAssigner(unitType: String, unitHash: ByteArray): VariantAssigner {
        return assigners_.computeIfAbsent(unitType) {
            VariantAssigner(unitHash)
        }
    }

    private fun buildAttributesMap(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        synchronized(attributes_) {
            for (attr in attributes_) {
                result[attr.name] = attr.value
            }
        }
        return result
    }

    private fun logEvent(type: ContextEventLogger.EventType, data: Any?) {
        try {
            eventLogger?.handleEvent(this, type, data)
        } catch (_: Exception) {
        }
    }

    private fun checkReady(expectNotClosed: Boolean) {
        if (!ready_) throw IllegalStateException("ABsmartly Context is not yet ready.")
        if (expectNotClosed) checkNotClosed()
    }

    private fun checkNotClosed() {
        if (closed_.get()) throw IllegalStateException("ABsmartly Context is finalized.")
        if (closing_.get()) throw IllegalStateException("ABsmartly Context is closing.")
    }
}
