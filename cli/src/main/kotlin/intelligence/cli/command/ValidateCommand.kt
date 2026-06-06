package intelligence.cli.command

import intelligence.cli.io.normalizedAbsolute
import intelligence.cli.io.toCliPath
import intelligence.cli.validation.ValidationFailure
import intelligence.cli.validation.ValidationOptions
import intelligence.cli.validation.ValidationService
import java.nio.file.Path
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

internal class ValidateCommand(
    private val validationService: ValidationService,
) : CliktCommand(
    name = "validate",
) {
    private val repo by option("--repo", help = "Repository root to validate.")
        .convert("PATH") { it.toCliPath() }
        .default(Path.of(".").normalizedAbsolute())

    private val portable by option("--portable", help = "Skip host-local validation checks.")
        .flag(default = false)

    private val hydrated by option("--hydrated", help = "Hydrated marketplace output to validate.")
        .convert("PATH") { it.toCliPath() }

    @Suppress("unused")
    private val manifestsOnly by option(
        "--manifests-only",
        help = "Accepted for compatibility; manifest validation is always delegated to the repository validator.",
    ).flag(default = false)

    override fun help(context: Context): String =
        "Run repository manifest validation gates."

    override fun run() {
        val exitCode = try {
            validationService.validate(
                ValidationOptions(
                    repo = repo,
                    portable = portable,
                    hydrated = hydrated,
                )
            )
        } catch (failure: ValidationFailure) {
            throw CliktError(failure.message ?: "validation failed", statusCode = 1)
        }

        if (exitCode != 0) {
            throw ProgramResult(exitCode)
        }
    }
}
