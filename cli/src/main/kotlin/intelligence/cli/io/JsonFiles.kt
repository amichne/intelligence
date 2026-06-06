package intelligence.cli.io

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal object JsonFiles {
    val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
    }

    fun readObject(path: Path): JsonObject =
        json.parseToJsonElement(path.readText()).jsonObject

    fun writeObject(path: Path, payload: JsonObject) {
        path.parent?.createDirectories()
        path.writeText(json.encodeToString(JsonElement.serializer(), payload.sorted()) + "\n")
    }
}

internal fun JsonObject.stringValue(key: String): String? =
    this[key]?.let { element -> (element as? JsonPrimitive)?.content }

internal fun JsonObject.objectValue(key: String): JsonObject? =
    this[key] as? JsonObject

internal fun JsonObject.arrayValue(key: String): JsonArray =
    this[key] as? JsonArray ?: JsonArray(emptyList())

internal fun JsonObject.stringList(key: String): List<String> =
    arrayValue(key).mapNotNull { (it as? JsonPrimitive)?.content }

internal fun JsonObjectBuilder.putIfNotNull(key: String, value: String?) {
    if (!value.isNullOrBlank()) {
        put(key, value)
    }
}

internal fun JsonObjectBuilder.putStringArray(key: String, values: Iterable<String>) {
    putJsonArray(key) {
        values.forEach(::add)
    }
}

internal fun jsonObjectOf(vararg pairs: Pair<String, JsonElement?>): JsonObject =
    buildJsonObject {
        pairs.forEach { (key, value) ->
            if (value != null) {
                put(key, value)
            }
        }
    }

internal fun JsonElement.sorted(): JsonElement =
    when (this) {
        is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { it.key to it.value.sorted() })
        is JsonArray -> JsonArray(map { it.sorted() })
        else -> this
    }

internal fun jsonArrayOfStrings(values: Iterable<String>): JsonArray =
    buildJsonArray {
        values.forEach(::add)
    }
