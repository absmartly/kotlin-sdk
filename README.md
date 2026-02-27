# A/B Smartly SDK

A/B Smartly - Kotlin SDK

## Compatibility

The A/B Smartly Kotlin SDK is compatible with Kotlin 1.6 and later, running on JVM 11+. It uses [Jackson](https://github.com/FasterXML/jackson) for JSON serialization and provides a thread-safe API using `ConcurrentHashMap` and atomic operations.

### Android

The A/B Smartly Kotlin SDK is compatible with Android 5.0 and later (API level 21+).

The `android.permission.INTERNET` permission is required. To add this permission to your application ensure the following line is present in the `AndroidManifest.xml` file:
```xml
    <uses-permission android:name="android.permission.INTERNET"/>
```

## Installation

#### Gradle (Kotlin DSL)

To install the ABsmartly Kotlin SDK, place the following in your `build.gradle.kts` and replace `{VERSION}` with the latest SDK version available in MavenCentral.

```kotlin
dependencies {
    implementation("com.absmartly:absmartly-sdk-kotlin:{VERSION}")
}
```

#### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.absmartly:absmartly-sdk-kotlin:{VERSION}'
}
```

#### Maven

To install the ABsmartly Kotlin SDK, place the following in your `pom.xml` and replace `{VERSION}` with the latest SDK version available in MavenCentral.

```xml
<dependency>
    <groupId>com.absmartly</groupId>
    <artifactId>absmartly-sdk-kotlin</artifactId>
    <version>{VERSION}</version>
</dependency>
```

## Getting Started

Please follow the [installation](#installation) instructions before trying the following code.

### Initialization

This example assumes an Api Key, an Application, and an Environment have been created in the A/B Smartly web console.

#### Recommended: Using the SDK Wrapper

The recommended approach uses the `ABsmartly` wrapper which handles HTTP communication, data fetching, and event publishing automatically.

```kotlin
import com.absmartly.sdk.*

fun main() {
    val clientConfig = ClientConfig.create()
        .setEndpoint("https://your-company.absmartly.io/v1")
        .setAPIKey("YOUR_API_KEY")
        .setApplication("website")
        .setEnvironment("production")

    val client = Client.create(clientConfig)

    val sdkConfig = ABSmartlyConfig.create()
        .setClient(client)

    val sdk = ABsmartly.create(sdkConfig)

    val contextConfig = ContextConfig.create()
        .setUnit("session_id", "5ebf06d8cb5d8137290c4abb64155584fbdb64d8")

    val context = sdk.createContext(contextConfig)
        .waitUntilReady()

    val treatment = context.getTreatment("exp_test_experiment")

    context.close()
    sdk.close()
}
```

#### With Pre-fetched Data

If you already have the context data, you can create a context without an additional HTTP request:

```kotlin
val contextData = sdk.getContextData().get()

val contextConfig = ContextConfig.create()
    .setUnit("session_id", "5ebf06d8cb5d8137290c4abb64155584fbdb64d8")

val context = sdk.createContextWith(contextConfig, contextData)
```

#### Async Context Creation

The `createContext()` method starts fetching data asynchronously. You can use the context after calling `waitUntilReady()` or `waitUntilReadyAsync()`:

```kotlin
val context = sdk.createContext(contextConfig)

context.waitUntilReadyAsync().thenAccept { ctx ->
    val treatment = ctx.getTreatment("exp_test_experiment")
}
```

#### Direct Construction

For simpler use cases where you manage data fetching yourself:

```kotlin
val contextData: ContextData = fetchContextData()

val units = mutableMapOf("session_id" to "5ebf06d8cb5d8137290c4abb64155584fbdb64d8")
val options = ContextOptions(publishDelay = -1, refreshPeriod = 0)

val context = Context(
    data = contextData,
    units = units,
    options = options,
)
```

#### Advanced Configuration

For advanced use cases where you need to handle SDK events, provide a custom event logger:

```kotlin
val eventLogger = object : ContextEventLogger {
    override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
        when (type) {
            ContextEventLogger.EventType.Exposure -> {
                val exposure = data as Exposure
                println("Exposed to experiment: ${exposure.name}")
            }
            ContextEventLogger.EventType.Goal -> {
                val goal = data as GoalAchievement
                println("Goal tracked: ${goal.name}")
            }
            ContextEventLogger.EventType.Error -> {
                println("Error: $data")
            }
            else -> {}
        }
    }
}

val sdkConfig = ABSmartlyConfig.create()
    .setClient(client)
    .setContextEventLogger(eventLogger)

val sdk = ABsmartly.create(sdkConfig)
```

**ClientConfig Parameters**

| Config      | Type                         | Required? | Description                                                       |
| :---------- | :--------------------------- | :-------: | :---------------------------------------------------------------- |
| endpoint    | `String`                     |  &#9989;  | The A/B Smartly collector endpoint URL (must use https://)         |
| apiKey      | `String`                     |  &#9989;  | Your API key from the A/B Smartly web console                     |
| application | `String`                     |  &#9989;  | The application name as configured in the web console              |
| environment | `String`                     |  &#9989;  | The environment name as configured in the web console              |
| deserializer| `ContextDataDeserializer?`   |  &#10060; | Custom context data deserializer (defaults to Jackson-based)       |
| serializer  | `ContextEventSerializer?`    |  &#10060; | Custom event serializer (defaults to Jackson-based)                |
| executor    | `Executor?`                  |  &#10060; | Custom executor for async operations                               |

**ContextConfig Parameters**

| Method               | Description                                                   |
| :------------------- | :------------------------------------------------------------ |
| `setUnit(type, uid)` | Set a unit type and identifier                                |
| `setOverride(name, variant)` | Force a specific variant for an experiment              |
| `setCustomAssignment(name, variant)` | Set a custom assignment for an experiment      |
| `setEventLogger(logger)` | Set a context-level event logger                          |
| `setPublishDelay(ms)` | Set the publish delay in milliseconds (default: 100)         |
| `setRefreshInterval(ms)` | Set the refresh interval in milliseconds (default: 0)    |

## Creating a New Context

### Using ABsmartly SDK Wrapper (Recommended)

```kotlin
val contextConfig = ContextConfig.create()
    .setUnit("session_id", "5ebf06d8cb5d8137290c4abb64155584fbdb64d8")

val context = sdk.createContext(contextConfig)
    .waitUntilReady()

assert(context.isReady)
```

### With Pre-fetched Data

Creating a context involves obtaining data from the A/B Smartly event collector. You can avoid repeating the round-trip by re-using previously retrieved data:

```kotlin
val contextData = sdk.getContextData().get()

val context = sdk.createContextWith(
    ContextConfig.create().setUnit("session_id", "5ebf06d8cb5d8137290c4abb64155584fbdb64d8"),
    contextData
)

val anotherContext = sdk.createContextWith(
    ContextConfig.create().setUnit("session_id", "another-session-id"),
    contextData
)

assert(anotherContext.isReady)
```

### Direct Construction (Manual Data Fetching)

```kotlin
val contextData: ContextData = fetchContextData()
val units = mutableMapOf("session_id" to "5ebf06d8cb5d8137290c4abb64155584fbdb64d8")
val options = ContextOptions()

val context = Context(
    data = contextData,
    units = units,
    options = options,
)

assert(context.isReady)
```

### Refreshing the Context with Fresh Experiment Data

For long-running contexts, experiments started after the context was created will not be triggered.

When using the SDK wrapper, call `refresh()` without arguments to automatically fetch fresh data:

```kotlin
context.refresh().get()
```

When using direct construction, pass the new data explicitly:

```kotlin
val freshData: ContextData = fetchFreshContextData()
context.refresh(freshData)
```

### Setting Extra Units

You can add additional units to a context by calling the `setUnit()` method. This is useful when a user logs in and you want to associate the new identity with the context. Note that you cannot override an already set unit type, as that would be a change of identity and will throw an `IllegalArgumentException`. In this case, you must create a new context instead.

```kotlin
context.setUnit("db_user_id", "1000013")
```

## Basic Usage

### Selecting a Treatment

```kotlin
if (context.getTreatment("exp_test_experiment") == 0) {
    // user is in control group (variant 0)
} else {
    // user is in treatment group
}
```

### Treatment Variables

```kotlin
val variable = context.getVariableValue("my_variable", "default_value")
```

Variables can be of any type:

```kotlin
val buttonColor = context.getVariableValue("button_color", "blue") as String
val showBanner = context.getVariableValue("show_banner", false) as Boolean
val bannerHeight = context.getVariableValue("banner_height", 200) as Int
```

### Peek at Treatment Variants

Although generally not recommended, it is sometimes necessary to peek at a treatment or variable without triggering an exposure. The A/B Smartly SDK provides `peekTreatment()` and `peekVariableValue()` methods for that.

```kotlin
if (context.peekTreatment("exp_test_experiment") == 0) {
    // user is in control group (variant 0)
} else {
    // user is in treatment group
}
```

#### Peeking at Variables

```kotlin
val variable = context.peekVariableValue("my_variable", "default_value")
```

### Overriding Treatment Variants

During development, for example, it is useful to force a treatment for an experiment. This can be achieved with the `setOverride()` method. The `setOverride()` method can be called before the context is ready.

```kotlin
context.setOverride("exp_test_experiment", 1)
```

## Advanced

### Context Attributes

The `setAttribute()` method can be called before the context is ready.

```kotlin
context.setAttribute("user_agent", "Mozilla/5.0...")

context.setAttribute("customer_age", "new_customer")
```

### Custom Assignments

```kotlin
context.setCustomAssignment("exp_test_experiment", 1)
```

### Custom Field Values

```kotlin
val fieldValue = context.getCustomFieldValue("exp_test_experiment", "my_field")
val fieldType = context.getCustomFieldValueType("exp_test_experiment", "my_field")
val fieldKeys: Set<String> = context.customFieldKeys
```

Custom field values are automatically converted based on their type:
- `json` type fields are deserialized into their native types
- `boolean` type fields return `Boolean`
- `number` type fields return `Double`
- Other types return `String`

### Variable Keys

```kotlin
val keys: Map<String, List<String>> = context.variableKeys
// Maps variable names to the list of experiments that define them
```

### Tracking Goals

Goals are created in the A/B Smartly web console.

```kotlin
context.track("payment", mapOf(
    "item_count" to 1,
    "total_amount" to 1999.99,
))
```

Track a goal without properties:

```kotlin
context.track("page_view", null)
```

### Publishing Pending Data

Sometimes it is necessary to ensure all events have been published to the A/B Smartly collector, before proceeding. You can explicitly call the `publish()` method.

```kotlin
context.publish()
```

### Closing

The `close()` method will ensure all events have been published to the A/B Smartly collector, like `publish()`, and will also "seal" the context, throwing an `IllegalStateException` if any method that could generate an event is called.

```kotlin
context.close()
```

### Context State

```kotlin
context.isReady       // true when context has been initialized with data
context.isFailed      // true when context initialization failed
context.isClosed      // true when context has been closed
context.pendingCount  // number of pending events awaiting publish
context.experiments   // list of experiment names in the context
```

### Custom Event Logger

The A/B Smartly SDK can be instantiated with an event logger. Implement the `ContextEventLogger` interface to receive SDK lifecycle events.

```kotlin
class CustomEventLogger : ContextEventLogger {
    override fun handleEvent(context: Context, type: ContextEventLogger.EventType, data: Any?) {
        when (type) {
            ContextEventLogger.EventType.Exposure -> {
                val exposure = data as Exposure
                println("Exposed to experiment: ${exposure.name}")
            }
            ContextEventLogger.EventType.Goal -> {
                val goal = data as GoalAchievement
                println("Goal tracked: ${goal.name}")
            }
            ContextEventLogger.EventType.Error -> {
                println("Error: $data")
            }
            ContextEventLogger.EventType.Publish -> {
                val event = data as PublishEvent
                println("Published ${event.exposures?.size ?: 0} exposures")
            }
            ContextEventLogger.EventType.Ready,
            ContextEventLogger.EventType.Refresh,
            ContextEventLogger.EventType.Close -> {}
        }
    }
}
```

Usage:

```kotlin
val context = Context(
    data = contextData,
    units = mutableMapOf("session_id" to "abc123"),
    options = ContextOptions(),
    eventLogger = CustomEventLogger(),
)
```

**Event Types**

| Event      | When                                                       | Data                                   |
| ---------- | ---------------------------------------------------------- | -------------------------------------- |
| `Error`    | `Context` receives an error                                | `Throwable` object                     |
| `Ready`    | `Context` turns ready                                      | `ContextData` used to initialize       |
| `Refresh`  | `Context.refresh()` method succeeds                        | `ContextData` used to refresh          |
| `Publish`  | `Context.publish()` method succeeds                        | `PublishEvent` sent to collector       |
| `Exposure` | `Context.getTreatment()` succeeds on first exposure        | `Exposure` enqueued for publishing     |
| `Goal`     | `Context.track()` method succeeds                          | `GoalAchievement` enqueued for publishing |
| `Close`    | `Context.close()` method succeeds the first time           | `null`                                 |

### Using with Android

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.absmartly.sdk.*

class MainActivity : AppCompatActivity() {

    private lateinit var context: Context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contextData = fetchContextData()
        val deviceId = getDeviceId()

        context = Context(
            data = contextData,
            units = mutableMapOf("device_id" to deviceId),
            options = ContextOptions(),
        )

        val treatment = context.getTreatment("exp_button_color")

        if (treatment == 0) {
            setContentView(R.layout.activity_main_control)
        } else {
            setContentView(R.layout.activity_main_treatment)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::context.isInitialized) {
            context.close()
        }
    }
}
```

### Using with Ktor

```kotlin
import com.absmartly.sdk.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Application.configureRouting(contextData: ContextData) {
    routing {
        get("/product") {
            val sessionId = call.sessions.get<UserSession>()?.id ?: "anonymous"

            val context = Context(
                data = contextData,
                units = mutableMapOf("session_id" to sessionId),
                options = ContextOptions(),
            )

            val treatment = context.getTreatment("exp_product_layout")

            call.respond(mapOf("treatment" to treatment))

            context.close()
        }
    }
}
```

## About A/B Smartly

**A/B Smartly** is the leading provider of state-of-the-art, on-premises, full-stack experimentation platforms for engineering and product teams that want to confidently deploy features as fast as they can develop them.
A/B Smartly's real-time analytics helps engineering and product teams ensure that new features will improve the customer experience without breaking or degrading performance and/or business metrics.

### Have a look at our growing list of clients and SDKs:
- [Java SDK](https://www.github.com/absmartly/java-sdk)
- [JavaScript SDK](https://www.github.com/absmartly/javascript-sdk)
- [PHP SDK](https://www.github.com/absmartly/php-sdk)
- [Swift SDK](https://www.github.com/absmartly/swift-sdk)
- [Vue2 SDK](https://www.github.com/absmartly/vue2-sdk)
- [Vue3 SDK](https://www.github.com/absmartly/vue3-sdk)
- [React SDK](https://www.github.com/absmartly/react-sdk)
- [Angular SDK](https://www.github.com/absmartly/angular-sdk)
- [Python3 SDK](https://www.github.com/absmartly/python3-sdk)
- [Go SDK](https://www.github.com/absmartly/go-sdk)
- [Ruby SDK](https://www.github.com/absmartly/ruby-sdk)
- [.NET SDK](https://www.github.com/absmartly/dotnet-sdk)
- [Kotlin SDK](https://www.github.com/absmartly/kotlin-sdk) (this package)
- [Dart SDK](https://www.github.com/absmartly/dart-sdk)
- [Flutter SDK](https://www.github.com/absmartly/flutter-sdk)
