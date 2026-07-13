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
        fun inspect(files: List<ReleaseFile>): MarketplaceReleaseInspection {
            val filesByName = files.associateBy(ReleaseFile::name)
            if (filesByName.size != files.size) {
                return MarketplaceReleaseInspection.Rejected(MarketplaceReleaseInspectionRejection.DuplicateAsset)
            }
            val indexName = ReleaseAssetName.snapshotIndex()
            val indexFile = filesByName[indexName]
                ?: return MarketplaceReleaseInspection.Rejected(
                    MarketplaceReleaseInspectionRejection.MissingAsset(indexName),
                )
            val index =
                when (val parsed = MarketplaceSnapshotIndex.parse(indexFile.bytes())) {
                    is MarketplaceSnapshotIndexParsing.Parsed -> parsed.index
                    is MarketplaceSnapshotIndexParsing.Rejected -> {
                        return MarketplaceReleaseInspection.Rejected(
                            MarketplaceReleaseInspectionRejection.IndexRejected(parsed.reason),
                        )
                    }
                }
            val packages = mutableListOf<PackageArchive>()
            index.packages.forEach { record ->
                val file = filesByName[record.archive.name]
                    ?: return MarketplaceReleaseInspection.Rejected(
                        MarketplaceReleaseInspectionRejection.MissingAsset(record.archive.name),
                    )
                if (file.byteSize != record.archive.byteSize || file.sha256 != record.archive.sha256) {
                    return MarketplaceReleaseInspection.Rejected(
                        MarketplaceReleaseInspectionRejection.PackageEvidenceMismatch(record.name),
                    )
                }
                val archive =
                    when (val parsed = PackageArchive.parse(file.bytes())) {
                        is PackageArchiveParsing.Parsed -> parsed.archive
                        is PackageArchiveParsing.Rejected -> {
                            return MarketplaceReleaseInspection.Rejected(
                                MarketplaceReleaseInspectionRejection.PackageRejected(record.name, parsed.reason),
                            )
                        }
                    }
                if (archive.marketplaceId != index.marketplaceId || archive.packageName != record.name) {
                    return MarketplaceReleaseInspection.Rejected(
                        MarketplaceReleaseInspectionRejection.PackageIdentityMismatch(record.name),
                    )
                }
                packages += archive
            }
            val expected =
                when (
                    val materialized =
                        materialize(
                            index.marketplaceId,
                            index.snapshotId,
                            index.defaultPackage,
                            packages,
                        )
                ) {
                    is MarketplaceReleaseMaterialization.Materialized -> materialized.release
                    is MarketplaceReleaseMaterialization.Rejected -> {
                        return MarketplaceReleaseInspection.Rejected(
                            MarketplaceReleaseInspectionRejection.BuildRejected(materialized.reason),
                        )
                    }
                }
            return when (val verification = expected.verify(files)) {
                MarketplaceReleaseVerification.Verified -> MarketplaceReleaseInspection.Inspected(expected)
                is MarketplaceReleaseVerification.Rejected ->
                    MarketplaceReleaseInspection.Rejected(
                        MarketplaceReleaseInspectionRejection.ContentRejected(verification.reason),
                    )
            }
        }

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

internal sealed interface MarketplaceReleaseInspection {
    data class Inspected(val release: MarketplaceRelease) : MarketplaceReleaseInspection

    data class Rejected(val reason: MarketplaceReleaseInspectionRejection) : MarketplaceReleaseInspection
}

internal sealed interface MarketplaceReleaseInspectionRejection {
    data object DuplicateAsset : MarketplaceReleaseInspectionRejection

    data class MissingAsset(val name: ReleaseAssetName) : MarketplaceReleaseInspectionRejection

    data class IndexRejected(val reason: MarketplaceSnapshotIndexRejection) : MarketplaceReleaseInspectionRejection

    data class PackageEvidenceMismatch(val packageName: PackageName) : MarketplaceReleaseInspectionRejection

    data class PackageRejected(
        val packageName: PackageName,
        val reason: PackageArchiveParseRejection,
    ) : MarketplaceReleaseInspectionRejection

    data class PackageIdentityMismatch(val packageName: PackageName) : MarketplaceReleaseInspectionRejection

    data class BuildRejected(val reason: MarketplaceReleaseRejection) : MarketplaceReleaseInspectionRejection

    data class ContentRejected(val reason: MarketplaceReleaseVerificationRejection) : MarketplaceReleaseInspectionRejection
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
