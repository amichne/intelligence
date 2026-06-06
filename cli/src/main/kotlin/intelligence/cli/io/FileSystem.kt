package intelligence.cli.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

internal object FileSystem {
    private val ignoredDirectoryNames = setOf(
        ".git",
        ".gradle",
        ".idea",
        "__pycache__",
        "build",
        "dist",
        "node_modules",
        "target",
    )

    fun replaceDirectory(path: Path) {
        if (path.exists()) {
            deleteRecursively(path)
        }
        path.createDirectories()
    }

    fun copyPath(source: Path, target: Path) {
        if (target.exists() || target.isSymbolicLink()) {
            deleteRecursively(target)
        }
        target.parent?.createDirectories()
        if (source.isDirectory() && !source.isSymbolicLink()) {
            copyDirectory(source, target)
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
        }
    }

    fun copyTreeContents(source: Path, target: Path) {
        source.listDirectoryEntries().forEach { child ->
            copyPath(child, target.resolve(child.name))
        }
    }

    fun clearWorktree(worktree: Path) {
        worktree.listDirectoryEntries().forEach { child ->
            if (child.name != ".git") {
                deleteRecursively(child)
            }
        }
    }

    fun deleteRecursively(path: Path) {
        if (!path.exists() && !path.isSymbolicLink()) {
            return
        }
        if (path.isDirectory() && !path.isSymbolicLink()) {
            path.listDirectoryEntries().forEach(::deleteRecursively)
        }
        path.deleteIfExists()
    }

    fun regularFilesUnder(path: Path): List<Path> {
        if (!path.exists()) {
            return emptyList()
        }
        return Files.walk(path).use { stream ->
            stream
                .filter { it.isRegularFile() }
                .sorted(compareBy<Path> { path.relativize(it).toString() })
                .toList()
        }
    }

    fun copyHookSidecars(pluginOut: Path, hookScript: Path) {
        val stem = hookScript.fileName.toString().substringBeforeLast(".")
        Files.list(hookScript.parent).use { stream ->
            stream
                .filter { it.isRegularFile() }
                .filter { it.fileName.toString().startsWith("$stem.") }
                .filter { it.fileName.toString().endsWith(".json") }
                .toList()
                .filterNot { it.fileName.toString().endsWith(".hook.json") }
                .sortedBy { it.fileName.toString() }
                .forEach { sidecar ->
                    copyPath(sidecar, pluginOut.resolve("hooks").resolve(sidecar.fileName))
                }
        }
    }

    private fun copyDirectory(source: Path, target: Path) {
        target.createDirectories()
        source.listDirectoryEntries().forEach { child ->
            if (shouldIgnore(child)) {
                return@forEach
            }
            copyPath(child, target.resolve(child.name))
        }
    }

    private fun shouldIgnore(path: Path): Boolean {
        val fileName = path.name
        return fileName in ignoredDirectoryNames || fileName.endsWith(".pyc")
    }
}
