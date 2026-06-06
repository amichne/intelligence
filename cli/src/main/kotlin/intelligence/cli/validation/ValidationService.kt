package intelligence.cli.validation

import intelligence.cli.io.ProcessRunner
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal class ValidationService(
    private val processRunner: ProcessRunner = ProcessRunner.system(),
    private val output: (String) -> Unit = ::println,
) {
    fun validate(options: ValidationOptions): Int {
        if (!options.repo.isDirectory()) {
            throw ValidationFailure("repository does not exist: ${options.repo}")
        }

        val validator = options.repo.resolve("scripts").resolve("validate-manifests.mjs")
        if (!validator.exists()) {
            throw ValidationFailure("repository has no scripts/validate-manifests.mjs: ${options.repo}")
        }

        val command = buildList {
            add("node")
            add("scripts/validate-manifests.mjs")
            if (options.portable) {
                add("--portable")
            }
            options.hydrated?.let {
                add("--hydrated")
                add(it.toString())
            }
        }

        output("$ " + command.joinToString(" "))
        return processRunner.run(command, options.repo)
    }
}

internal data class ValidationOptions(
    val repo: Path,
    val portable: Boolean,
    val hydrated: Path?,
)

internal class ValidationFailure(message: String) : RuntimeException(message)
