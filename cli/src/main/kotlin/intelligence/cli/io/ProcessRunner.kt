package intelligence.cli.io

import java.nio.file.Path

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
