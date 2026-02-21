package com.absmartly.sdk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ContextData(
    @JsonProperty("experiments") var experiments: List<Experiment> = emptyList()
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class Experiment(
    @JsonProperty("id") var id: Int = 0,
    @JsonProperty("name") var name: String = "",
    @JsonProperty("unitType") var unitType: String? = null,
    @JsonProperty("iteration") var iteration: Int = 0,
    @JsonProperty("seedHi") var seedHi: Int = 0,
    @JsonProperty("seedLo") var seedLo: Int = 0,
    @JsonProperty("split") var split: DoubleArray = doubleArrayOf(),
    @JsonProperty("trafficSeedHi") var trafficSeedHi: Int = 0,
    @JsonProperty("trafficSeedLo") var trafficSeedLo: Int = 0,
    @JsonProperty("trafficSplit") var trafficSplit: DoubleArray = doubleArrayOf(),
    @JsonProperty("fullOnVariant") var fullOnVariant: Int = 0,
    @JsonProperty("applications") var applications: List<ExperimentApplication>? = null,
    @JsonProperty("variants") var variants: List<ExperimentVariant> = emptyList(),
    @JsonProperty("audienceStrict") var audienceStrict: Boolean = false,
    @JsonProperty("audience") var audience: String? = null,
    @JsonProperty("customFieldValues") var customFieldValues: List<CustomFieldValue>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ExperimentApplication(
    @JsonProperty("name") var name: String = ""
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ExperimentVariant(
    @JsonProperty("name") var name: String = "",
    @JsonProperty("config") var config: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class CustomFieldValue(
    @JsonProperty("name") var name: String = "",
    @JsonProperty("type") var type: String? = null,
    @JsonProperty("value") var value: String? = null
)

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
class Exposure(
    var id: Int = 0,
    var name: String = "",
    var unit: String? = null,
    var variant: Int = 0,
    var exposedAt: Long = 0,
    var assigned: Boolean = false,
    var eligible: Boolean = false,
    var overridden: Boolean = false,
    var fullOn: Boolean = false,
    var custom: Boolean = false,
    var audienceMismatch: Boolean = false
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class GoalAchievement(
    var name: String = "",
    var achievedAt: Long = 0,
    var properties: Map<String, Any?>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class PublishEvent(
    var hashed: Boolean = false,
    var units: List<Unit>? = null,
    var publishedAt: Long = 0,
    var exposures: List<Exposure>? = null,
    var goals: List<GoalAchievement>? = null,
    var attributes: List<Attribute>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class Unit(
    var type: String = "",
    var uid: String = ""
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class Attribute(
    var name: String = "",
    var value: Any? = null,
    var setAt: Long = 0
)

data class ContextOptions(
    val publishDelay: Long = -1,
    val refreshPeriod: Long = 0
)
