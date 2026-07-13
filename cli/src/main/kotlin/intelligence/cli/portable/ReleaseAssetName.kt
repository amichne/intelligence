package intelligence.cli.portable

@JvmInline
internal value class ReleaseAssetName private constructor(
    private val text: String,
) {
    fun render(): String = text

    companion object {
        fun parse(candidate: String): ReleaseAssetNameParsing =
            if (candidate.length > MAX_RELEASE_ASSET_NAME_CHARACTERS) {
                ReleaseAssetNameParsing.Rejected(ReleaseAssetNameRejection.TOO_LONG)
            } else if (!releaseAssetNamePattern.matches(candidate)) {
                ReleaseAssetNameParsing.Rejected(ReleaseAssetNameRejection.INVALID_SYNTAX)
            } else {
                ReleaseAssetNameParsing.Parsed(ReleaseAssetName(candidate))
            }

        internal fun packageArchive(packageName: PackageName): ReleaseAssetName =
            ReleaseAssetName("package-${packageName.render()}.zip")

        internal fun providerArchive(provider: PortableProvider): ReleaseAssetName =
            ReleaseAssetName("${provider.render()}-marketplace.zip")

        internal fun snapshotIndex(): ReleaseAssetName = ReleaseAssetName("marketplace.json")

        internal fun checksumManifest(): ReleaseAssetName = ReleaseAssetName("SHA256SUMS")
    }
}

internal sealed interface ReleaseAssetNameParsing {
    data class Parsed(val name: ReleaseAssetName) : ReleaseAssetNameParsing

    data class Rejected(val reason: ReleaseAssetNameRejection) : ReleaseAssetNameParsing
}

internal enum class ReleaseAssetNameRejection {
    TOO_LONG,
    INVALID_SYNTAX,
}

private const val MAX_RELEASE_ASSET_NAME_CHARACTERS = 128
private val releaseAssetNamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
