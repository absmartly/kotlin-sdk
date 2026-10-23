package com.absmartly.sdk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
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
            readyFuture_.set(COMPLETED_VOID_FUTURE)
        } else {
            readyFuture_.set(CompletableFuture())
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

            val readyFuture = CompletableFuture<Void>()
            context.readyFuture_.set(readyFuture)
            dataFuture.thenAccept { data ->
                if (context.closed_.get() || context.closing_.get()) {
                    context.readyFuture_.getAndSet(COMPLETED_VOID_FUTURE)?.complete(null)
                    return@thenAccept
                }
                synchronized(context.refreshLock_) {
                    if (context.appliedRefreshSeq_ == 0L) context.setData(data)
                }
                val rf = context.readyFuture_.getAndSet(COMPLETED_VOID_FUTURE)
                rf?.complete(null)
                context.logEvent(ContextEventLogger.EventType.Ready, data)
                context.startTimers()
            }.exceptionally { exception ->
                if (context.closed_.get() || context.closing_.get()) {
                    context.readyFuture_.getAndSet(COMPLETED_VOID_FUTURE)?.complete(null)
                    return@exceptionally null
                }
                synchronized(context.refreshLock_) {
                    if (context.appliedRefreshSeq_ == 0L) context.setDataFailed(exception)
                }
                val rf = context.readyFuture_.getAndSet(COMPLETED_VOID_FUTURE)
                rf?.complete(null)
                context.startTimers()
                null
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

    private val lifecycleLock_ = ReentrantReadWriteLock()
    @Volatile private var publishTask_: ScheduledFuture<*>? = null
    @Volatile private var refreshTask_: ScheduledFuture<*>? = null

    @Volatile private var scheduledRefresh_: CompletableFuture<Void>? = null
    private val inFlight_: MutableSet<CompletableFuture<Void>> = ConcurrentHashMap.newKeySet()
    private val refreshSeq_ = java.util.concurrent.atomic.AtomicLong(0)
    private val refreshLock_ = Any()
    private var appliedRefreshSeq_ = 0L

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
        var split: DoubleArray = doubleArrayOf()
        var seedHi: Int = 0
        var seedLo: Int = 0
        var trafficSeedHi: Int = 0
        var trafficSeedLo: Int = 0
        var variantConfigs: List<String?> = emptyList()
        var audience: String? = null
        var audienceStrict: Boolean = false
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

    private fun startTimers() {
        val sched = scheduler ?: return
        synchronized(this) {
            if (closed_.get() || closing_.get() || publishTask_ != null || refreshTask_ != null) return
            if (publishDelay > 0) {
                publishTask_ = sched.scheduleWithFixedDelay({
                    try {
                        if (!closed_.get() && !closing_.get() && pendingCount_.get() > 0) flush()
                    } catch (_: Exception) {
                    }
                }, publishDelay, publishDelay, TimeUnit.MILLISECONDS)
            }
            if (refreshInterval > 0) {
                refreshTask_ = sched.scheduleWithFixedDelay({
                    try {
                        if (!closed_.get() && !closing_.get()) {
                            val prev = scheduledRefresh_
                            if (prev == null || prev.isDone) {
                                val f = refresh()
                                scheduledRefresh_ = f
                                f.whenComplete { _, ex ->
                                    if (ex != null) logEvent(ContextEventLogger.EventType.Error, ex)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logEvent(ContextEventLogger.EventType.Error, e)
                    }
                }, refreshInterval, refreshInterval, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun stopTimers() {
        synchronized(this) {
            publishTask_?.cancel(false)
            refreshTask_?.cancel(false)
        }
    }

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
            checkReady(true)
            return data.experiments.map { it.name }
        }

    val variableKeys: Map<String, List<String>>
        get() {
            checkReady(true)
            val result = mutableMapOf<String, List<String>>()
            for ((key, exps) in indexVariables_) {
                result[key] = exps.map { it.data.name }
            }
            return result
        }

    val customFieldKeys: Set<String>
        get() {
            checkReady(true)
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
        checkReady(true)
        val assignment = getAssignment(experimentName)
        if (!assignment.exposed.get()) {
            queueExposure(assignment)
        }
        return assignment.variant
    }

    fun peekTreatment(experimentName: String): Int {
        checkReady(true)
        return getAssignment(experimentName).variant
    }

    fun getVariableValue(key: String, defaultValue: Any?): Any? {
        checkReady(true)
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
        checkReady(true)
        val assignment = getVariableAssignment(key)
        if (assignment != null && assignment.variables != null) {
            if (assignment.variables!!.containsKey(key)) {
                return assignment.variables!![key]
            }
        }
        return defaultValue
    }

    fun getCustomFieldValue(experimentName: String, key: String): Any? {
        checkReady(true)
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
        checkReady(true)
        val experiment = index_[experimentName] ?: return null
        val field = experiment.data.customFieldValues?.find { it.name == key } ?: return null
        return field.type
    }

    fun track(goalName: String, properties: Map<String, Any?>?) {
        val achievement = GoalAchievement(
            name = goalName,
            achievedAt = System.currentTimeMillis(),
            properties = properties?.let { java.util.TreeMap(it) }
        )
        val lock = lifecycleLock_.readLock()
        lock.lock()
        try {
            checkNotClosed()
            achievements_.add(achievement)
            pendingCount_.incrementAndGet()
        } finally {
            lock.unlock()
        }
        logEvent(ContextEventLogger.EventType.Goal, achievement)
    }

    fun publish(): CompletableFuture<Void> {
        checkNotClosed()
        return flush()
    }

    override fun close() {
        if (closed_.get()) return
        val wl = lifecycleLock_.writeLock()
        val won: Boolean
        wl.lock()
        try {
            // Taking the write lock waits for in-flight event producers; later ones see closing_.
            won = !closed_.get() && closing_.compareAndSet(false, true)
        } finally {
            wl.unlock()
        }
        if (won) {
            stopTimers()
            readyFuture_.get()?.let { if (!it.isDone) it.complete(null) }
            try {
                if (pendingCount_.get() > 0) {
                    flush().join()
                }
            } finally {
                // Wait for publishes admitted before close so the client is not shut down under them.
                for (f in inFlight_.toList()) {
                    try {
                        f.join()
                    } catch (_: Exception) {
                    }
                }
                // Close wins regardless of the final publish outcome; a failure still propagates to the caller.
                // closing_ stays set: isClosing already reports false once closed_ is true, and keeping it
                // set stops a late close() from winning the CAS again.
                closed_.set(true)
                logEvent(ContextEventLogger.EventType.Close, null)
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
        readyFuture_.getAndSet(COMPLETED_VOID_FUTURE)?.complete(null)
        logEvent(ContextEventLogger.EventType.Ready, newData)
    }

    fun refresh(): CompletableFuture<Void> {
        if (dataProvider == null) {
            return CompletableFuture.completedFuture(null)
        }
        if (closed_.get() || closing_.get()) return CompletableFuture.completedFuture(null)
        val seq = refreshSeq_.incrementAndGet()
        return dataProvider.getContextData().thenAccept { newData ->
            applyRefresh(seq, newData)
        }
    }

    fun refresh(newData: ContextData) {
        if (closed_.get() || closing_.get()) return
        applyRefresh(refreshSeq_.incrementAndGet(), newData)
    }

    // Only the most recently requested refresh may win; older or post-close completions are dropped.
    private fun applyRefresh(seq: Long, newData: ContextData) {
        synchronized(refreshLock_) {
            if (closed_.get() || closing_.get()) return
            if (seq < refreshSeq_.get() || seq <= appliedRefreshSeq_) return
            appliedRefreshSeq_ = seq
            setData(newData)
            readyFuture_.getAndSet(COMPLETED_VOID_FUTURE)?.complete(null)
        }
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
        failed_ = false
        readyError_ = null
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
                assignment.split = experiment.data.split
                assignment.seedHi = experiment.data.seedHi
                assignment.seedLo = experiment.data.seedLo
                assignment.trafficSeedHi = experiment.data.trafficSeedHi
                assignment.trafficSeedLo = experiment.data.trafficSeedLo
                assignment.variantConfigs = experiment.data.variants.map { it.config }
                assignment.audience = experiment.data.audience
                assignment.audienceStrict = experiment.data.audienceStrict
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
                experiment.trafficSplit.contentEquals(assignment.trafficSplit) &&
                experiment.split.contentEquals(assignment.split) &&
                experiment.seedHi == assignment.seedHi && experiment.seedLo == assignment.seedLo &&
                experiment.trafficSeedHi == assignment.trafficSeedHi &&
                experiment.trafficSeedLo == assignment.trafficSeedLo &&
                experiment.variants.map { it.config } == assignment.variantConfigs &&
                experiment.audience == assignment.audience &&
                experiment.audienceStrict == assignment.audienceStrict
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
        val lock = lifecycleLock_.readLock()
        var publishedExposure: Exposure? = null
        lock.lock()
        try {
            if (closed_.get() || closing_.get()) return
            if (!assignment.exposed.compareAndSet(false, true)) return
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
            publishedExposure = exposure
        } finally {
            lock.unlock()
        }
        logEvent(ContextEventLogger.EventType.Exposure, publishedExposure)
    }

    private fun flush(): CompletableFuture<Void> {
        if (pendingCount_.get() <= 0) {
            return CompletableFuture.completedFuture(null)
        }
        // Registered before draining so close() can never miss a publish that has drained but not yet returned.
        val placeholder = CompletableFuture<Void>()
        inFlight_.add(placeholder)
        placeholder.whenComplete { _, _ -> inFlight_.remove(placeholder) }
        try {
            return flushInternal().also { f ->
                f.whenComplete { _, ex -> if (ex != null) placeholder.completeExceptionally(ex) else placeholder.complete(null) }
            }
        } catch (e: Throwable) {
            placeholder.completeExceptionally(e)
            throw e
        }
    }

    private fun flushInternal(): CompletableFuture<Void> {
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
            val requeue = { exception: Throwable ->
                for (exposure in exposureList) {
                    exposures_.add(exposure)
                }
                for (goal in goalList) {
                    achievements_.add(goal)
                }
                pendingCount_.addAndGet(drained)
                logEvent(ContextEventLogger.EventType.Error, exception)
            }
            val future = try {
                eventHandler.publish(this, event)
            } catch (e: Exception) {
                requeue(e)
                return CompletableFuture<Void>().also { it.completeExceptionally(e) }
            }
            val tracked = future.whenComplete { _, exception ->
                if (exception != null) requeue(exception)
            }
            return tracked
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
        if (closing_.get()) throw IllegalStateException("ABsmartly Context is finalizing.")
    }
}
