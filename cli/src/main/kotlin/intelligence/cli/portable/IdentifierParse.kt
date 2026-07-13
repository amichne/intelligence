package intelligence.cli.portable

internal sealed interface IdentifierParse<out T> {
    data class Accepted<T>(val value: T) : IdentifierParse<T>

    data class Rejected(val reason: IdentifierRejection) : IdentifierParse<Nothing>
}

internal enum class IdentifierRejection {
    EMPTY,
    TOO_LONG,
    INVALID_SYNTAX,
}

private const val MAX_IDENTIFIER_LENGTH = 64
private val identifierPattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

internal fun <T> parseIdentifier(
    raw: String,
    accepted: (String) -> T,
): IdentifierParse<T> =
    when {
        raw.isEmpty() -> IdentifierParse.Rejected(IdentifierRejection.EMPTY)
        raw.length > MAX_IDENTIFIER_LENGTH -> IdentifierParse.Rejected(IdentifierRejection.TOO_LONG)
        !identifierPattern.matches(raw) -> IdentifierParse.Rejected(IdentifierRejection.INVALID_SYNTAX)
        else -> IdentifierParse.Accepted(accepted(raw))
    }
