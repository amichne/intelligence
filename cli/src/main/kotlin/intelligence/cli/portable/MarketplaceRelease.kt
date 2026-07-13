package intelligence.cli.portable

internal class ReleaseFile private constructor(
    val name: ReleaseAssetName,
    private val content: ByteArray,
) {
    val byteSize: Int
        get() = content.size

    val sha256: Sha256Digest = Sha256Digest.compute(content)

    fun bytes(): ByteArray = content.copyOf()

    companion object {
        fun create(
            name: ReleaseAssetName,
            content: ByteArray,
        ): ReleaseFileCreation =
            when {
                content.isEmpty() -> ReleaseFileCreation.Rejected(ReleaseFileRejection.Empty)
                content.size > MAX_RELEASE_ARTIFACT_BYTES ->
                    ReleaseFileCreation.Rejected(
                        ReleaseFileRejection.TooLarge(
                            actualBytes = content.size,
                            maximumBytes = MAX_RELEASE_ARTIFACT_BYTES,
                        ),
                    )
                else -> ReleaseFileCreation.Created(ReleaseFile(name, content.copyOf()))
            }
    }
}

internal sealed interface ReleaseFileCreation {
    data class Created(val file: ReleaseFile) : ReleaseFileCreation

    data class Rejected(val reason: ReleaseFileRejection) : ReleaseFileCreation
}

internal sealed interface ReleaseFileRejection {
    data object Empty : ReleaseFileRejection

    data class TooLarge(
        val actualBytes: Int,
        val maximumBytes: Int,
    ) : ReleaseFileRejection
}

internal class MarketplaceRelease private constructor(
    val marketplaceId: MarketplaceId,
    val snapshotId: SnapshotId,
    val defaultPackage: PackageName,
    val index: MarketplaceSnapshotIndex,
    files: List<ReleaseFile>,
) {
    private val files: List<ReleaseFile> = files.toList()
    private val filesByName: Map<ReleaseAssetName, ReleaseFile> = files.associateBy(ReleaseFile::name)

    fun files(): List<ReleaseFile> = files.toList()

    fun file(name: ReleaseAssetName): ReleaseFile = filesByName.getValue(name)

    fun checksumBytes(): ByteArray = file(ReleaseAssetName.checksumManifest()).bytes()

    fun verify(actualFiles: List<ReleaseFile>): MarketplaceReleaseVerification =
        verifyMarketplaceRelease(files, actualFiles)

    companion object {
        fun materialize(
            marketplaceId: MarketplaceId,
            snapshotId: SnapshotId,
            defaultPackage: PackageName,
            packages: List<PackageArchive>,
        ): MarketplaceReleaseMaterialization {
            val providerArchives = mutableListOf<ProviderMarketplaceArchive>()
            PortableProvider.entries.forEach { provider ->
                when (
                    val materialized =
                        ProviderMarketplaceArchive.materialize(
                            marketplaceId,
                            snapshotId,
                            packages,
                            provider,
                        )
                ) {
                    is ProviderMarketplaceArchiveMaterialization.Materialized ->
                        providerArchives += materialized.archive
                    is ProviderMarketplaceArchiveMaterialization.Rejected -> {
                        return MarketplaceReleaseMaterialization.Rejected(
                            MarketplaceReleaseRejection.ProviderArchiveRejected(
                                provider,
                                materialized.reason,
                            ),
                        )
                    }
                }
            }

            val index =
                when (
                    val materialized =
                        MarketplaceSnapshotIndex.materialize(
                            marketplaceId,
                            snapshotId,
                            defaultPackage,
                            packages,
                            providerArchives,
                        )
                ) {
                    is MarketplaceSnapshotIndexMaterialization.Materialized -> materialized.index
                    is MarketplaceSnapshotIndexMaterialization.Rejected -> {
                        return MarketplaceReleaseMaterialization.Rejected(
                            MarketplaceReleaseRejection.SnapshotIndexRejected(materialized.reason),
                        )
                    }
                }

            val releaseFiles = mutableListOf<ReleaseFile>()
            packages.sortedBy { archive -> archive.assetName.render() }.forEach { archive ->
                when (val created = ReleaseFile.create(archive.assetName, archive.bytes())) {
                    is ReleaseFileCreation.Created -> releaseFiles += created.file
                    is ReleaseFileCreation.Rejected -> {
                        return MarketplaceReleaseMaterialization.Rejected(
                            MarketplaceReleaseRejection.ReleaseFileRejected(archive.assetName, created.reason),
                        )
                    }
                }
            }
            providerArchives.sortedBy { archive -> archive.assetName.render() }.forEach { archive ->
                when (val created = ReleaseFile.create(archive.assetName, archive.bytes())) {
                    is ReleaseFileCreation.Created -> releaseFiles += created.file
                    is ReleaseFileCreation.Rejected -> {
                        return MarketplaceReleaseMaterialization.Rejected(
                            MarketplaceReleaseRejection.ReleaseFileRejected(archive.assetName, created.reason),
                        )
                    }
                }
            }
            val indexName = ReleaseAssetName.snapshotIndex()
            when (val created = ReleaseFile.create(indexName, index.canonicalBytes())) {
                is ReleaseFileCreation.Created -> releaseFiles += created.file
                is ReleaseFileCreation.Rejected -> {
                    return MarketplaceReleaseMaterialization.Rejected(
                        MarketplaceReleaseRejection.ReleaseFileRejected(indexName, created.reason),
                    )
                }
            }

            val orderedWithoutChecksums = releaseFiles.sortedBy { file -> file.name.render() }
            val checksumName = ReleaseAssetName.checksumManifest()
            val checksumFile =
                when (
                    val created =
                        ReleaseFile.create(
                            checksumName,
                            releaseChecksums(orderedWithoutChecksums),
                        )
                ) {
                    is ReleaseFileCreation.Created -> created.file
                    is ReleaseFileCreation.Rejected -> {
                        return MarketplaceReleaseMaterialization.Rejected(
                            MarketplaceReleaseRejection.ReleaseFileRejected(checksumName, created.reason),
                        )
                    }
                }
            val files = (orderedWithoutChecksums + checksumFile).sortedBy { file -> file.name.render() }
            val release =
                MarketplaceRelease(
                    marketplaceId = marketplaceId,
                    snapshotId = snapshotId,
                    defaultPackage = defaultPackage,
                    index = index,
                    files = files,
                )
            check(release.verify(release.files()) == MarketplaceReleaseVerification.Verified)
            return MarketplaceReleaseMaterialization.Materialized(release)
        }
    }
}

internal sealed interface MarketplaceReleaseMaterialization {
    data class Materialized(val release: MarketplaceRelease) : MarketplaceReleaseMaterialization

    data class Rejected(val reason: MarketplaceReleaseRejection) : MarketplaceReleaseMaterialization
}

internal sealed interface MarketplaceReleaseRejection {
    data class ProviderArchiveRejected(
        val provider: PortableProvider,
        val reason: ProviderMarketplaceArchiveRejection,
    ) : MarketplaceReleaseRejection

    data class SnapshotIndexRejected(
        val reason: MarketplaceSnapshotIndexRejection,
    ) : MarketplaceReleaseRejection

    data class ReleaseFileRejected(
        val name: ReleaseAssetName,
        val reason: ReleaseFileRejection,
    ) : MarketplaceReleaseRejection
}

internal sealed interface MarketplaceReleaseVerification {
    data object Verified : MarketplaceReleaseVerification

    data class Rejected(
        val reason: MarketplaceReleaseVerificationRejection,
    ) : MarketplaceReleaseVerification
}

internal sealed interface MarketplaceReleaseVerificationRejection {
    data class DuplicateAsset(val name: ReleaseAssetName) : MarketplaceReleaseVerificationRejection

    data class MissingAsset(val name: ReleaseAssetName) : MarketplaceReleaseVerificationRejection

    data class UnexpectedAsset(val name: ReleaseAssetName) : MarketplaceReleaseVerificationRejection

    data class SizeMismatch(
        val name: ReleaseAssetName,
        val expectedBytes: Int,
        val actualBytes: Int,
    ) : MarketplaceReleaseVerificationRejection

    data class DigestMismatch(
        val name: ReleaseAssetName,
        val expected: Sha256Digest,
        val actual: Sha256Digest,
    ) : MarketplaceReleaseVerificationRejection

    data class ContentMismatch(val name: ReleaseAssetName) : MarketplaceReleaseVerificationRejection
}

private fun releaseChecksums(files: List<ReleaseFile>): ByteArray =
    buildString {
        files.forEach { file ->
            append(file.sha256.render())
            append("  ")
            append(file.name.render())
            append('\n')
        }
    }.encodeToByteArray()

private fun verifyMarketplaceRelease(
    expectedFiles: List<ReleaseFile>,
    actualFiles: List<ReleaseFile>,
): MarketplaceReleaseVerification {
    val actualByName = linkedMapOf<ReleaseAssetName, ReleaseFile>()
    actualFiles.forEach { file ->
        if (actualByName.putIfAbsent(file.name, file) != null) {
            return MarketplaceReleaseVerification.Rejected(
                MarketplaceReleaseVerificationRejection.DuplicateAsset(file.name),
            )
        }
    }
    val expectedByName = expectedFiles.associateBy(ReleaseFile::name)
    expectedFiles.firstOrNull { file -> file.name !in actualByName }?.let { missing ->
        return MarketplaceReleaseVerification.Rejected(
            MarketplaceReleaseVerificationRejection.MissingAsset(missing.name),
        )
    }
    actualByName.keys.sortedBy(ReleaseAssetName::render).firstOrNull { name -> name !in expectedByName }
        ?.let { unexpected ->
            return MarketplaceReleaseVerification.Rejected(
                MarketplaceReleaseVerificationRejection.UnexpectedAsset(unexpected),
            )
        }
    expectedFiles.forEach { expected ->
        val actual = checkNotNull(actualByName[expected.name])
        if (actual.byteSize != expected.byteSize) {
            return MarketplaceReleaseVerification.Rejected(
                MarketplaceReleaseVerificationRejection.SizeMismatch(
                    expected.name,
                    expected.byteSize,
                    actual.byteSize,
                ),
            )
        }
        if (actual.sha256 != expected.sha256) {
            return MarketplaceReleaseVerification.Rejected(
                MarketplaceReleaseVerificationRejection.DigestMismatch(
                    expected.name,
                    expected.sha256,
                    actual.sha256,
                ),
            )
        }
        if (!actual.bytes().contentEquals(expected.bytes())) {
            return MarketplaceReleaseVerification.Rejected(
                MarketplaceReleaseVerificationRejection.ContentMismatch(expected.name),
            )
        }
    }
    return MarketplaceReleaseVerification.Verified
}
