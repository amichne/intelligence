package intelligence.cli.portable

import java.security.MessageDigest

@JvmInline
internal value class Sha256Digest private constructor(
    private val value: String,
) {
    fun render(): String = value

    companion object {
        fun compute(content: ByteArray): Sha256Digest {
            val bytes = MessageDigest.getInstance(SHA_256).digest(content)
            val characters = CharArray(SHA256_HEX_LENGTH)
            bytes.forEachIndexed { index, byte ->
                val unsigned = byte.toInt() and 0xff
                characters[index * 2] = HEX_DIGITS[unsigned ushr 4]
                characters[index * 2 + 1] = HEX_DIGITS[unsigned and 0x0f]
            }
            return Sha256Digest(characters.concatToString())
        }

        fun parse(candidate: String): Sha256DigestParsing {
            if (candidate.length != SHA256_HEX_LENGTH) {
                return Sha256DigestParsing.Rejected(
                    Sha256DigestRejection.InvalidLength(
                        actual = candidate.length,
                        expected = SHA256_HEX_LENGTH,
                    ),
                )
            }

            candidate.forEachIndexed { index, character ->
                if (character !in '0'..'9' && character !in 'a'..'f') {
                    return Sha256DigestParsing.Rejected(
                        Sha256DigestRejection.InvalidCharacter(index, character),
                    )
                }
            }
            return Sha256DigestParsing.Parsed(Sha256Digest(candidate))
        }
    }
}

internal sealed interface Sha256DigestParsing {
    data class Parsed(val digest: Sha256Digest) : Sha256DigestParsing

    data class Rejected(val reason: Sha256DigestRejection) : Sha256DigestParsing
}

internal sealed interface Sha256DigestRejection {
    data class InvalidLength(
        val actual: Int,
        val expected: Int,
    ) : Sha256DigestRejection

    data class InvalidCharacter(
        val index: Int,
        val character: Char,
    ) : Sha256DigestRejection
}

private const val SHA_256 = "SHA-256"
private const val SHA256_HEX_LENGTH = 64
private const val HEX_DIGITS = "0123456789abcdef"
