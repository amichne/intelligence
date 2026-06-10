package intelligence.cli.command

import intelligence.cli.io.ProcessRunner
import java.nio.file.Files
import java.nio.file.Path

internal class TerminalUiLauncher(
    private val processRunner: ProcessRunner = ProcessRunner.system(),
    private val executableResolver: () -> String = ::resolveTerminalUiExecutable,
    private val intelligenceResolver: (String) -> String = ::resolveIntelligenceExecutable,
) {
    fun launch(repoRoot: Path, ref: String? = null): Int {
        val terminalUiExecutable = executableResolver()
        val command = buildList {
            add(terminalUiExecutable)
            add("--repo")
            add(repoRoot.toString())
            add("--intelligence-bin")
            add(intelligenceResolver(terminalUiExecutable))
            ref?.takeIf { it.isNotBlank() }?.let {
                add("--ref")
                add(it)
            }
        }
        return processRunner.run(command, repoRoot)
    }
}

private fun resolveTerminalUiExecutable(): String =
    System.getenv("INTELLIGENCE_TUI_BIN")
        ?.takeIf { it.isNotBlank() }
        ?: siblingExecutable("intelligence-tui")
        ?: "intelligence-tui"

private fun resolveIntelligenceExecutable(terminalUiExecutable: String): String =
    System.getenv("INTELLIGENCE_BIN")
        ?.takeIf { it.isNotBlank() }
        ?: siblingExecutable(terminalUiExecutable, "intelligence")
        ?: currentExecutableIfIntelligence()
        ?: "intelligence"

private fun currentExecutableIfIntelligence(): String? {
    val command = ProcessHandle.current().info().command().orElse(null) ?: return null
    return command.takeIf { Path.of(it).fileName?.toString()?.substringBefore(".") == "intelligence" }
}

private fun siblingExecutable(name: String): String? {
    val current = ProcessHandle.current().info().command().orElse(null) ?: return null
    val currentPath = Path.of(current)
    val sibling = currentPath.parent?.resolve(name) ?: return null
    return executablePath(sibling)
}

private fun siblingExecutable(executable: String, name: String): String? {
    val sibling = Path.of(executable).parent?.resolve(name) ?: return null
    return executablePath(sibling)
}

private fun executablePath(sibling: Path): String? {
    return sibling
        .takeIf { Files.isRegularFile(it) && Files.isExecutable(it) }
        ?.toString()
}
