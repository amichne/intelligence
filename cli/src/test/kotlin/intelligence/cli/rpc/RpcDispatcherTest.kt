package intelligence.cli.rpc

import intelligence.cli.command.RpcCommand
import intelligence.cli.io.JsonFiles
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.github.ajalt.clikt.testing.test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RpcDispatcherTest {
    @Test
    fun `browse returns JSON RPC result catalog`() {
        val response = handle(
            """
            {
              "jsonrpc": "2.0",
              "id": "browse",
              "method": "marketplace.browse",
              "params": {
                "repository": "${repoRoot()}",
                "provider": "source"
              }
            }
            """.trimIndent(),
        )

        assertEquals("2.0", response.string("jsonrpc"))
        assertEquals("browse", response.string("id"))
        val result = response.objectValue("result")
        assertEquals("intelligence-cli", result.objectValue("marketplace").string("name"))
        assertEquals("source", result.objectValue("marketplace").string("provider"))
        assertTrue(result["plugins"].toString().contains("kotlin-engineering"))
    }

    @Test
    fun `validation run returns exit code and messages`() {
        val response = handle(
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "validation.run",
              "params": {
                "repoRoot": "${repoRoot()}",
                "portable": true
              }
            }
            """.trimIndent(),
        )

        val result = response.objectValue("result")
        assertEquals("0", result["exitCode"]!!.jsonPrimitive.content)
        assertTrue(result["messages"].toString().contains("OK adaptable marketplace"))
    }

    @Test
    fun `unknown method returns typed method not found error`() {
        val response = handle(
            """
            {
              "jsonrpc": "2.0",
              "id": "missing",
              "method": "marketplace.nope",
              "params": {
                "repoRoot": "${repoRoot()}"
              }
            }
            """.trimIndent(),
        )

        val error = response.objectValue("error")
        assertEquals("-32601", error["code"]!!.jsonPrimitive.content)
        assertEquals("METHOD_NOT_FOUND", error.objectValue("data").string("type"))
    }

    @Test
    fun `unknown params return typed invalid params error`() {
        val response = handle(
            """
            {
              "jsonrpc": "2.0",
              "id": "bad-param",
              "method": "marketplace.browse",
              "params": {
                "repository": "${repoRoot()}",
                "provider": "source",
                "surprise": true
              }
            }
            """.trimIndent(),
        )

        val error = response.objectValue("error")
        assertEquals("-32602", error["code"]!!.jsonPrimitive.content)
        assertEquals("INVALID_PARAMS", error.objectValue("data").string("type"))
        assertTrue(error.string("message").contains("surprise"))
    }

    @Test
    fun `missing params returns typed invalid params error`() {
        val response = handle(
            """
            {
              "jsonrpc": "2.0",
              "id": "missing-params",
              "method": "marketplace.browse"
            }
            """.trimIndent(),
        )

        val error = response.objectValue("error")
        assertEquals("-32602", error["code"]!!.jsonPrimitive.content)
        assertEquals("INVALID_PARAMS", error.objectValue("data").string("type"))
        assertTrue(error.string("message").contains("params"))
    }

    @Test
    fun `malformed JSON returns parse error without request id`() {
        val response = handle("{")

        val error = response.objectValue("error")
        assertEquals("null", response["id"].toString())
        assertEquals("-32700", error["code"]!!.jsonPrimitive.content)
        assertEquals("PARSE_ERROR", error.objectValue("data").string("type"))
    }

    @Test
    fun `rpc command writes one JSON response per request`() {
        val command = RpcCommand(
            dispatcher = RpcDispatcher(),
            inputLines = {
                sequenceOf(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": "browse",
                      "method": "marketplace.browse",
                      "params": {
                        "repository": "${repoRoot()}",
                        "provider": "source"
                      }
                    }
                    """.trimIndent()
                )
            },
        )

        val result = command.test("")

        assertEquals(0, result.statusCode)
        assertEquals(1, result.stdout.trim().lines().size)
        assertFalse(result.stdout.contains("Marketplace:"))
        val response = JsonFiles.json.parseToJsonElement(result.stdout).jsonObject
        assertEquals("browse", response.string("id"))
        assertEquals("intelligence-cli", response.objectValue("result").objectValue("marketplace").string("name"))
    }

    private fun handle(request: String): JsonObject =
        JsonFiles.json.parseToJsonElement(RpcDispatcher().handleLine(request)).jsonObject

    private fun repoRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .first { it.resolve("source").resolve("adaptable.marketplace.json").toFile().isFile }
}

private fun JsonObject.objectValue(key: String): JsonObject =
    this[key]!!.jsonObject

private fun JsonObject.string(key: String): String =
    this[key]!!.jsonPrimitive.content
