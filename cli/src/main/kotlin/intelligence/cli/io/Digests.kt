package intelligence.cli.io

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

internal enum class DigestAlgorithm(
    val jvmName: String,
    val prefix: String?,
) {
    Sha1("SHA-1", null),
    Sha256("SHA-256", "sha256"),
}

internal object Digests {
    fun digestPath(path: Path, algorithm: DigestAlgorithm): String {
        val digest = MessageDigest.getInstance(algorithm.jvmName)
        if (path.toFile().isDirectory) {
            FileSystem.regularFilesUnder(path).forEach { child ->
                val relative = path.relativize(child).toString().replace('\\', '/')
                digest.update(relative.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(child.readBytes())
                digest.update(0.toByte())
            }
        } else {
            digest.update(path.readBytes())
        }

        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return algorithm.prefix?.let { "$it:$hex" } ?: hex
    }
}
