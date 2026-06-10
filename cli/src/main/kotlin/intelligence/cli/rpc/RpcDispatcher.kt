package intelligence.cli.rpc

import intelligence.cli.io.JsonFiles
import intelligence.cli.io.ProcessRunner
import intelligence.cli.io.objectValue
import intelligence.cli.io.putStringArray
import intelligence.cli.io.toCliPath
import intelligence.cli.marketplace.MarketplaceBrowseProvider
import intelligence.cli.marketplace.MarketplaceBrowserService
import intelligence.cli.marketplace.MarketplaceFailure
import intelligence.cli.marketplace.MarketplaceProvider
import intelligence.cli.marketplace.MarketplaceService
import intelligence.cli.validation.ValidationFailure
import intelligence.cli.validation.ValidationOptions
import intelligence.cli.validation.ValidationService
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class RpcDispatcher(
    private val processRunner: ProcessRunner = ProcessRunner.system(),
    private val browserService: MarketplaceBrowserService = MarketplaceBrowserService(),
) {
    fun handleLine(input: String): String {
        val id = runCatching {
            (JsonFiles.json.parseToJsonElement(input) as? JsonObject)?.get("id")
        }.getOrNull()
        val response = try {
            val call = parseRpcCall(input)
            rpcSuccess(call.id, execute(call.method, call.params))
        } catch (failure: RpcFailure) {
            rpcError(id, failure)
        } catch (failure: Throwable) {
            rpcError(id, RpcFailure.internal(failure.message ?: failure::class.simpleName.orEmpty()))
        }
        return JsonFiles.compactJson.encodeToString(JsonElement.serializer(), response)
    }

    fun execute(method: RpcMethod, params: JsonObject): JsonElement =
        try {
            when (method) {
                RpcMethod.MarketplaceBrowse -> browse(params)
                RpcMethod.MarketplaceRemotesList -> listRemotes(params)
                RpcMethod.MarketplaceRemotesAdd -> withMarketplaceMessages { service ->
                    params.requireOnlyParams("repoRoot", "name", "repository", "ref")
                    service.addRemote(
                        repoRoot = params.requiredStringParam("repoRoot").toCliPath(),
                        name = params.requiredStringParam("name"),
                        repository = params.requiredStringParam("repository"),
                        ref = params.optionalStringParam("ref"),
                    )
                }
                RpcMethod.MarketplaceRemotesRemove -> withMarketplaceMessages { service ->
                    params.requireOnlyParams("repoRoot", "name")
                    service.removeRemote(
                        repoRoot = params.requiredStringParam("repoRoot").toCliPath(),
                        name = params.requiredStringParam("name"),
                    )
                }
                RpcMethod.MarketplaceImport -> withMarketplaceMessages { service ->
                    params.requireOnlyParams("repoRoot", "target", "version", "ref")
                    service.importPlugin(
                        repoRoot = params.requiredStringParam("repoRoot").toCliPath(),
                        target = params.requiredStringParam("target"),
                        version = params.optionalStringParam("version"),
                        ref = params.optionalStringParam("ref"),
                    )
                }
                RpcMethod.MarketplaceInstall -> withMarketplaceMessages { service ->
                    params.requireOnlyParams("repoRoot", "repository", "ref")
                    service.installMarketplace(
                        repoRoot = params.requiredStringParam("repoRoot").toCliPath(),
                        repository = params.requiredStringParam("repository"),
                        ref = params.optionalStringParam("ref"),
                    )
                }
                RpcMethod.MarketplaceMaterialize -> withMarketplaceMessages { service ->
                    params.requireOnlyParams("repoRoot", "outRoot", "provider")
                    service.materialize(
                        repoRoot = params.requiredStringParam("repoRoot").toCliPath(),
                        outRoot = params.requiredStringParam("outRoot").toCliPath(),
                        provider = providerParam(params, "provider"),
                    )
                }
                RpcMethod.MarketplacePublishDefault -> withMarketplaceMessages { service ->
                    params.requireOnlyParams("repoRoot")
                    service.publishDefault(repoRoot = params.requiredStringParam("repoRoot").toCliPath())
                }
                RpcMethod.MarketplacePublishBranch -> withMarketplaceMessages { service ->
                    params.requireOnlyParams("repoRoot", "provider", "branch", "noPush")
                    service.publishBranch(
                        repoRoot = params.requiredStringParam("repoRoot").toCliPath(),
                        provider = providerParam(params, "provider"),
                        branch = params.optionalStringParam("branch"),
                        noPush = params.booleanParam("noPush", default = false),
                    )
                }
                RpcMethod.ValidationRun -> validate(params)
            }
        } catch (failure: MarketplaceFailure) {
            throw RpcFailure.marketplace(failure.message ?: "marketplace operation failed", failure.exitCode)
        } catch (failure: ValidationFailure) {
            throw RpcFailure.validation(failure.message ?: "validation failed")
        } catch (failure: IllegalArgumentException) {
            throw RpcFailure.invalidParams(failure.message ?: "invalid params")
        }

    private fun browse(params: JsonObject): JsonObject {
        params.requireOnlyParams("repository", "provider")
        val provider = browseProviderParam(params, "provider")
        return browserService
            .browse(repository = params.requiredStringParam("repository"), provider = provider)
            .toJson()
    }

    private fun listRemotes(params: JsonObject): JsonObject {
        params.requireOnlyParams("repoRoot")
        val messages = mutableListOf<String>()
        val service = MarketplaceService(processRunner = processRunner, output = messages::add)
        val remotes = service.remoteEntries(params.requiredStringParam("repoRoot").toCliPath())
        if (remotes.isEmpty()) {
            messages += "no external marketplaces configured"
        }
        return buildJsonObject {
            putMessages(messages)
            putJsonArray("remotes") {
                remotes.forEach { remote ->
                    add(
                        buildJsonObject {
                            put("name", remote.name)
                            put("source", remote.source)
                        }
                    )
                }
            }
        }
    }

    private fun validate(params: JsonObject): JsonObject {
        params.requireOnlyParams("repoRoot", "portable", "hydrated")
        val messages = mutableListOf<String>()
        val service = ValidationService(output = messages::add)
        val exitCode = service.validate(
            ValidationOptions(
                repo = params.requiredStringParam("repoRoot").toCliPath(),
                portable = params.booleanParam("portable", default = false),
                hydrated = params.optionalStringParam("hydrated")?.toCliPath(),
            )
        )
        return buildJsonObject {
            put("exitCode", exitCode)
            putMessages(messages)
            putStringArray("issues", messages.filter { it.startsWith("FAIL ") }.map { it.removePrefix("FAIL ") })
        }
    }

    private fun withMarketplaceMessages(action: (MarketplaceService) -> Unit): JsonObject {
        val messages = mutableListOf<String>()
        val service = MarketplaceService(processRunner = processRunner, output = messages::add)
        action(service)
        return buildJsonObject {
            putMessages(messages)
        }
    }

    private fun providerParam(params: JsonObject, name: String): MarketplaceProvider =
        MarketplaceProvider.parse(params.requiredStringParam(name))

    private fun browseProviderParam(params: JsonObject, name: String): MarketplaceBrowseProvider =
        params.optionalStringParam(name)
            ?.let(MarketplaceBrowseProvider::parse)
            ?: MarketplaceBrowseProvider.Auto
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putMessages(messages: List<String>) {
    putJsonArray("messages") {
        messages.forEach { message -> add(message) }
    }
}
