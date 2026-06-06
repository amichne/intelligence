package intelligence.cli

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(IntelligenceCli().run(args.toList()))
}

internal class IntelligenceCli(
    private val stdout: Appendable = System.out,
    private val stderr: Appendable = System.err,
    private val processRunner: ProcessRunner = ProcessRunner.system(),
) {
    fun run(args: List<String>): Int {
        if (args.isEmpty() || args.first() in setOf("-h", "--help", "help")) {
            stdout.appendLine(usage())
            return 0
        }

        return when (val command = args.first()) {
            "validate" -> runValidate(args.drop(1))
            else -> {
                stderr.appendLine("intelligence: unknown command: $command")
                stderr.appendLine(usage())
                2
            }
        }
    }

    private fun runValidate(args: List<String>): Int {
        if (args.firstOrNull() in setOf("-h", "--help", "help")) {
            stdout.appendLine(validateUsage())
            return 0
        }

        val options = try {
            ValidateOptions.parse(args)
        } catch (error: CliUsageError) {
            stderr.appendLine("intelligence: ${error.message}")
            return 2
        }

        if (!options.repo.isDirectory()) {
            stderr.appendLine("intelligence: repository does not exist: ${options.repo}")
            return 1
        }

        val validator = options.repo.resolve("scripts/validate-manifests.mjs")
        if (!validator.exists()) {
            stderr.appendLine("intelligence: repository has no scripts/validate-manifests.mjs: ${options.repo}")
            return 1
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
        stdout.appendLine(command.joinToString(" ", prefix = "$ "))
        return processRunner.run(command, options.repo)
    }
}

internal data class ValidateOptions(
    val repo: Path,
    val manifestsOnly: Boolean,
    val portable: Boolean,
    val hydrated: Path?,
) {
    companion object {
        fun parse(args: List<String>): ValidateOptions {
            var repo = Path.of(".").absolute().normalize()
            var manifestsOnly = false
            var portable = false
            var hydrated: Path? = null
            var index = 0

            while (index < args.size) {
                when (val arg = args[index]) {
                    "--repo" -> {
                        repo = args.valueAfter(index, arg).toPath().absolute().normalize()
                        index += 2
                    }

                    "--manifests-only" -> {
                        manifestsOnly = true
                        index += 1
                    }

                    "--portable" -> {
                        portable = true
                        index += 1
                    }

                    "--hydrated" -> {
                        hydrated = args.valueAfter(index, arg).toPath().absolute().normalize()
                        index += 2
                    }

                    "-h", "--help" -> throw CliUsageError("help must be the first validate argument")
                    else -> throw CliUsageError("unknown validate option: $arg")
                }
            }

            return ValidateOptions(
                repo = repo,
                manifestsOnly = manifestsOnly,
                portable = portable,
                hydrated = hydrated,
            )
        }
    }
}

internal fun interface ProcessRunner {
    fun run(command: List<String>, cwd: Path): Int

    companion object {
        fun system(): ProcessRunner =
            ProcessRunner { command, cwd ->
                ProcessBuilder(command)
                    .directory(cwd.toFile())
                    .inheritIO()
                    .start()
                    .waitFor()
            }
    }
}

private class CliUsageError(message: String) : RuntimeException(message)

private fun List<String>.valueAfter(index: Int, option: String): String {
    val value = getOrNull(index + 1)
    if (value.isNullOrBlank() || value.startsWith("-")) {
        throw CliUsageError("$option requires a value")
    }
    return value
}

private fun String.toPath(): Path = Path.of(replaceFirst("^~".toRegex(), System.getProperty("user.home")))

private fun usage(): String =
    """
    Usage: intelligence <command> [options]

    Commands:
      validate    Run repository manifest validation.

    Development:
      ./gradlew installDevelopmentCli
      .local/intelligence/bin/intelligence validate
    """.trimIndent()

private fun validateUsage(): String =
    """
    Usage: intelligence validate [--repo <path>] [--portable] [--hydrated <path>] [--manifests-only]

    --manifests-only is accepted for compatibility while validation is still backed by scripts/validate-manifests.mjs.
    """.trimIndent()
