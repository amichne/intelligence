package intelligence.cli.rpc

import intelligence.cli.io.JsonFiles
import intelligence.cli.io.arrayValue
import intelligence.cli.io.objectValue
import intelligence.cli.io.stringValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal enum class RpcMethod(
    val wireName: String,
) {
    MarketplaceBrowse("marketplace.browse"),
    MarketplaceRemotesList("marketplace.remotes.list"),
    MarketplaceRemotesAdd("marketplace.remotes.add"),
    MarketplaceRemotesRemove("marketplace.remotes.remove"),
    MarketplaceImport("marketplace.import"),
    MarketplaceInstall("marketplace.install"),
    MarketplaceMaterialize("marketplace.materialize"),
    MarketplacePublishDefault("marketplace.publishDefault"),
    MarketplacePublishBranch("marketplace.publishBranch"),
    ValidationRun("validation.run");

    companion object {
        fun parse(value: String): RpcMethod =
            entries.firstOrNull { it.wireName == value }
                ?: throw RpcFailure.methodNotFound("unknown RPC method: $value")
    }
}

internal enum class RpcErrorType(
    val wireName: String,
) {
    ParseError("PARSE_ERROR"),
    InvalidRequest("INVALID_REQUEST"),
    MethodNotFound("METHOD_NOT_FOUND"),
    InvalidParams("INVALID_PARAMS"),
    MarketplaceFailure("MARKETPLACE_FAILURE"),
    ValidationFailure("VALIDATION_FAILURE"),
    InternalError("INTERNAL_ERROR"),
}

internal class RpcFailure(
    val code: Int,
    val type: RpcErrorType,
    override val message: String,
    val exitCode: Int = 1,
) : RuntimeException(message) {
    companion object {
        fun parseError(message: String): RpcFailure =
            RpcFailure(code = -32700, type = RpcErrorType.ParseError, message = message)

        fun invalidRequest(message: String): RpcFailure =
            RpcFailure(code = -32600, type = RpcErrorType.InvalidRequest, message = message)

        fun methodNotFound(message: String): RpcFailure =
            RpcFailure(code = -32601, type = RpcErrorType.MethodNotFound, message = message)

        fun invalidParams(message: String): RpcFailure =
            RpcFailure(code = -32602, type = RpcErrorType.InvalidParams, message = message)

        fun marketplace(message: String, exitCode: Int): RpcFailure =
            RpcFailure(code = -32010, type = RpcErrorType.MarketplaceFailure, message = message, exitCode = exitCode)

        fun validation(message: String): RpcFailure =
            RpcFailure(code = -32020, type = RpcErrorType.ValidationFailure, message = message)

        fun internal(message: String): RpcFailure =
            RpcFailure(code = -32603, type = RpcErrorType.InternalError, message = message)
    }
}

internal data class RpcCall(
    val id: JsonElement,
    val method: RpcMethod,
    val params: JsonObject,
)

internal fun parseRpcCall(input: String): RpcCall {
    val element = try {
        JsonFiles.json.parseToJsonElement(input)
    } catch (error: Exception) {
        throw RpcFailure.parseError(error.message ?: "invalid JSON")
    }

    val request = element as? JsonObject
        ?: throw RpcFailure.invalidRequest("RPC request must be a JSON object")
    val version = request.stringValue("jsonrpc")
    if (version != "2.0") {
        throw RpcFailure.invalidRequest("jsonrpc must be 2.0")
    }
    val id = request["id"] ?: throw RpcFailure.invalidRequest("id is required")
    if (!id.isSupportedRpcId()) {
        throw RpcFailure.invalidRequest("id must be a string or integer")
    }
    val methodName = request.stringValue("method")
        ?: throw RpcFailure.invalidRequest("method is required")
    val params = request.objectValue("params") ?: JsonObject(emptyMap())
    return RpcCall(
        id = id,
        method = RpcMethod.parse(methodName),
        params = params,
    )
}

internal fun rpcSuccess(id: JsonElement, result: JsonElement): JsonObject =
    buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

internal fun rpcError(id: JsonElement?, failure: RpcFailure): JsonObject =
    buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        putJsonObject("error") {
            put("code", failure.code)
            put("message", failure.message)
            putJsonObject("data") {
                put("type", failure.type.wireName)
                put("exitCode", failure.exitCode)
            }
        }
    }

internal fun JsonObject.requiredStringParam(name: String): String =
    stringValue(name)?.takeIf { it.isNotBlank() }
        ?: throw RpcFailure.invalidParams("missing string param `$name`")

internal fun JsonObject.optionalStringParam(name: String): String? =
    stringValue(name)?.takeIf { it.isNotBlank() }

internal fun JsonObject.booleanParam(name: String, default: Boolean): Boolean =
    this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: default

internal fun JsonObject.stringArray(name: String): List<String> =
    arrayValue(name).mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }

private fun JsonElement.isSupportedRpcId(): Boolean {
    val primitive = this as? JsonPrimitive ?: return false
    if (primitive.isString) {
        return primitive.content.isNotBlank()
    }
    return primitive.content.toIntOrNull() != null
}
