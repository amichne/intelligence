package intelligence.cli.portable

@JvmInline
internal value class SnapshotId private constructor(private val text: String) {
    fun render(): String = text

    companion object {
        fun parse(raw: String): IdentifierParse<SnapshotId> = parseIdentifier(raw, ::SnapshotId)
    }
}
