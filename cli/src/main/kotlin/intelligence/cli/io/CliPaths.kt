package intelligence.cli.io

import java.nio.file.Path
import kotlin.io.path.absolute

internal fun String.toCliPath(): Path =
    Path.of(expandHome()).absolute().normalize()

internal fun Path.normalizedAbsolute(): Path =
    absolute().normalize()

private fun String.expandHome(): String =
    when {
        this == "~" -> System.getProperty("user.home")
        startsWith("~/") -> System.getProperty("user.home") + substring(1)
        else -> this
    }
