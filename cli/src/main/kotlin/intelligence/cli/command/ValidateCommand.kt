package intelligence.cli.command

import intelligence.cli.validation.ValidationOptions
import intelligence.cli.validation.ValidationService
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class ValidateCommand : PortableConsumerCommand("validate", "validate") {
    private val portable by option(
        "--portable",
        help = "Forbid host-local and network assumptions while validating owned state.",
    ).flag(default = false)

    override fun help(context: Context): String =
        "Validate every owned structured-data contract reachable from the repository."

    override fun execute(invocation: PortableInvocation): PortableCommandOutcome {
        val messages = mutableListOf<String>()
        val exitCode =
            ValidationService(output = messages::add).validate(
                ValidationOptions(
                    repo = invocation.repository,
                    portable = portable,
                    hydrated = null,
                ),
            )
        val issues = messages.filter { message -> message.startsWith("FAIL ") }.map { it.removePrefix("FAIL ") }
        val report =
            buildJsonObject {
                put("subject", invocation.repository.toString())
                put("outcome", if (exitCode == 0) "PASS" else "FAIL")
                put("portable", portable)
                putJsonArray("gates") {
                    add(
                        buildJsonObject {
                            put("name", "OWNED_STRUCTURED_STATE")
                            put("outcome", if (exitCode == 0) "PASS" else "FAIL")
                        },
                    )
                }
                putJsonArray("diagnostics") {
                    issues.sorted().forEach { issue ->
                        add(
                            buildJsonObject {
                                put("code", "VALIDATION_FAILED")
                                put("severity", "ERROR")
                                put("message", issue)
                            },
                        )
                    }
                }
            }
        return if (exitCode == 0) {
            PortableCommandOutcome.Success(report, messages.joinToString("\n").ifBlank { "validate: PASS" })
        } else {
            PortableCommandOutcome.Failure(
                PortableCommandError(
                    PortableErrorCode.VALIDATION_FAILED,
                    "Repository validation failed",
                    report,
                ),
                report["diagnostics"] as JsonArray,
            )
        }
    }
}
