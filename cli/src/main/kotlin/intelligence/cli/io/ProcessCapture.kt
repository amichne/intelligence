package intelligence.cli.io

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal fun interface ProcessCaptureRunner {
    fun run(command: List<String>, cwd: Path, environment: Map<String, String>): ProcessCapture

    companion object {
        fun system(): ProcessCaptureRunner =
            ProcessCaptureRunner { command, cwd, environment ->
                try {
                    val process = ProcessBuilder(command)
                        .directory(cwd.toFile())
                        .apply { this.environment().putAll(environment) }
                        .start()
                    val stdout = AtomicReference(ByteArray(0))
                    val stderr = AtomicReference(ByteArray(0))
                    val stdoutReader = thread(start = true) { stdout.set(process.inputStream.readBytes()) }
                    val stderrReader = thread(start = true) { stderr.set(process.errorStream.readBytes()) }
                    val exitCode = process.waitFor()
                    stdoutReader.join()
                    stderrReader.join()
                    ProcessCapture(
                        exitCode = exitCode,
                        stdout = stdout.get().toString(StandardCharsets.UTF_8),
                        stderr = stderr.get().toString(StandardCharsets.UTF_8),
                    )
                } catch (error: IOException) {
                    ProcessCapture(
                        exitCode = COMMAND_NOT_FOUND,
                        stdout = "",
                        stderr = error.message ?: "failed to start ${command.firstOrNull().orEmpty()}",
                    )
                }
            }

        const val COMMAND_NOT_FOUND = 127
    }
}

internal data class ProcessCapture(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
