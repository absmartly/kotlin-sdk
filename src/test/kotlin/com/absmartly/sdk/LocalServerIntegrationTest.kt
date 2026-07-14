package com.absmartly.sdk

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Hermetic integration test: starts a real local HTTP server (JDK built-in
 * com.sun.net.httpserver.HttpServer) on an ephemeral port, points the SDK's
 * client endpoint at it, and drives the PUBLIC SDK API so the real
 * DefaultHTTPClient performs a GET /context (createContext -> waitUntilReady)
 * and a PUT /context (getTreatment + track -> publish). Asserts the wire
 * contract: paths, query params, headers and body fields.
 */
class LocalServerIntegrationTest {

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val rawQuery: String?,
        val headers: Map<String, String>,
        val body: String,
    )

    private lateinit var server: HttpServer
    private var port = 0
    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/context") { exchange: HttpExchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val headers = exchange.requestHeaders.entries.associate { (k, v) ->
                k.lowercase() to v.joinToString(",")
            }
            requests.add(
                RecordedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    rawQuery = exchange.requestURI.rawQuery,
                    headers = headers,
                    body = body,
                )
            )

            val response = if (exchange.requestMethod == "GET") {
                """{"experiments":[]}"""
            } else {
                "{}"
            }.toByteArray()

            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        port = server.address.port
    }

    @AfterTest
    fun stopServer() {
        server.stop(0)
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split("&").associate {
            val idx = it.indexOf('=')
            if (idx < 0) it to "" else it.substring(0, idx) to URI("http://x?$it").query.substringAfter('=')
        }
    }

    @Test
    fun performsRealGetAndPutContext() {
        val sdk = ABsmartly.builder()
            .endpoint("http://127.0.0.1:$port")
            .apiKey("test-api-key")
            .application("website")
            .environment("dev")
            .build()

        val contextConfig = ContextConfig.create()
            .setUnit("user_id", "123456789")

        val context = sdk.createContext(contextConfig)
        context.waitUntilReady()

        // --- assert the real GET /context ---
        val get = requests.firstOrNull { it.method == "GET" }
        assertNotNull(get, "expected a GET /context")
        assertEquals("/context", get.path)
        val query = parseQuery(get.rawQuery)
        assertEquals("website", query["application"])
        assertEquals("dev", query["environment"])

        // --- queue an event then publish ---
        context.getTreatment("not_found_experiment")
        context.track("payment", mapOf("value" to 99))
        context.publish().get()

        val put = requests.firstOrNull { it.method == "PUT" }
        assertNotNull(put, "expected a PUT /context")
        assertEquals("/context", put.path)
        assertTrue(put.rawQuery.isNullOrEmpty(), "PUT should carry no query params")

        // --- headers ---
        assertEquals("test-api-key", put.headers["x-api-key"])
        assertEquals("website", put.headers["x-application"])
        assertEquals("dev", put.headers["x-environment"])
        assertEquals("0", put.headers["x-application-version"])
        assertTrue(!put.headers["x-agent"].isNullOrEmpty(), "X-Agent must be present")
        assertTrue(
            put.headers["content-type"]?.contains("application/json") == true,
            "Content-Type must be application/json"
        )

        // --- body ---
        val mapper = ObjectMapper()
        val node: JsonNode = mapper.readTree(put.body)
        assertTrue(node.has("hashed"))
        assertTrue(node.get("units").isArray)
        assertTrue(node.get("units").size() > 0)
        assertTrue(node.get("units").get(0).has("type"))
        assertTrue(node.get("units").get(0).has("uid"))
        assertTrue(node.has("publishedAt"))
        assertTrue(node.get("publishedAt").isNumber)
        assertTrue(node.get("goals").isArray)
        assertTrue(node.get("goals").size() > 0)
        assertEquals("payment", node.get("goals").get(0).get("name").asText())

        sdk.close()
    }
}
