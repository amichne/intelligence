package intelligence.cli.command

import intelligence.cli.io.arrayValue
import intelligence.cli.rpc.RpcDispatcher
import intelligence.cli.rpc.RpcFailure
import intelligence.cli.rpc.RpcMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError

internal fun CliktCommand.executeRpc(
    dispatcher: RpcDispatcher,
    method: RpcMethod,
    params: JsonObject,
    failureMessage: String,
): JsonObject =
    executeRpcCall(dispatcher, method, params, failureMessage)

internal fun executeRpcCall(
    dispatcher: RpcDispatcher,
    method: RpcMethod,
    params: JsonObject,
    failureMessage: String,
): JsonObject =
    try {
        dispatcher.execute(method, params) as? JsonObject
            ?: throw CliktError("$failureMessage: RPC result was not an object")
    } catch (failure: RpcFailure) {
        throw CliktError(failure.message, statusCode = failure.exitCode)
    }

internal fun CliktCommand.echoRpcMessages(result: JsonObject) {
    result.arrayValue("messages")
        .mapNotNull { (it as? JsonPrimitive)?.content }
        .forEach { message -> echo(message) }
}
