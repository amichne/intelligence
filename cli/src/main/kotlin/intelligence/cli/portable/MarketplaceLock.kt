package intelligence.cli.portable

internal sealed interface MarketplaceLockSource {
    data class GitHubRelease(
        val repository: GitHubRepositoryUrl,
        val tag: SnapshotId,
        val releaseId: GitHubReleaseId,
        val tagCommitSha: GitCommitSha,
    ) : MarketplaceLockSource

    data class LocalSnapshot(
        val directory: ConsumerRelativeDirectory,
    ) : MarketplaceLockSource
}

internal sealed interface LockedAsset {
    val name: ReleaseAssetName
    val byteSize: Int
    val sha256: Sha256Digest

    data class GitHub(
        val assetId: GitHubAssetId,
        override val name: ReleaseAssetName,
        override val byteSize: Int,
        override val sha256: Sha256Digest,
    ) : LockedAsset

    data class Local(
        override val name: ReleaseAssetName,
        override val byteSize: Int,
        override val sha256: Sha256Digest,
    ) : LockedAsset
}

internal data class LockedPackage(
    val name: PackageName,
    val archive: LockedAsset,
)

internal class MarketplaceLockEntry private constructor(
    val marketplaceId: MarketplaceId,
    val source: MarketplaceLockSource,
    val index: LockedAsset,
    val checksum: LockedAsset,
    packages: List<LockedPackage>,
) {
    val packages: List<LockedPackage> = packages.toList()

    companion object {
        fun create(
            marketplaceId: MarketplaceId,
            source: MarketplaceLockSource,
            index: LockedAsset,
            checksum: LockedAsset,
            packages: List<LockedPackage>,
        ): MarketplaceLockEntryCreation {
            validateExpectedName(index, ReleaseAssetName.snapshotIndex())?.let { return it }
            validateExpectedName(checksum, ReleaseAssetName.checksumManifest())?.let { return it }
            listOf(index, checksum).forEach { asset ->
                validateAsset(source, asset)?.let { return it }
            }
            if (packages.isEmpty()) {
                return MarketplaceLockEntryCreation.Rejected(MarketplaceLockEntryRejection.NoPackages)
            }
            if (packages.size > MAX_PACKAGES_PER_INTENT_SELECTION) {
                return MarketplaceLockEntryCreation.Rejected(
                    MarketplaceLockEntryRejection.TooManyPackages(
                        packages.size,
                        MAX_PACKAGES_PER_INTENT_SELECTION,
                    ),
                )
            }
            val ordered = packages.sortedBy { locked -> locked.name.render() }
            val seenPackages = mutableSetOf<PackageName>()
            ordered.forEach { locked ->
                if (!seenPackages.add(locked.name)) {
                    return MarketplaceLockEntryCreation.Rejected(
                        MarketplaceLockEntryRejection.DuplicatePackage(locked.name),
                    )
                }
                validateAsset(source, locked.archive)?.let { return it }
                val expectedName = ReleaseAssetName.packageArchive(locked.name)
                if (locked.archive.name != expectedName) {
                    return MarketplaceLockEntryCreation.Rejected(
                        MarketplaceLockEntryRejection.UnexpectedPackageArchiveName(
                            locked.name,
                            expectedName,
                            locked.archive.name,
                        ),
                    )
                }
            }
            val assets = listOf(index, checksum) + ordered.map(LockedPackage::archive)
            val names = mutableSetOf<ReleaseAssetName>()
            val digests = mutableSetOf<Sha256Digest>()
            assets.forEach { asset ->
                if (!names.add(asset.name)) {
                    return MarketplaceLockEntryCreation.Rejected(
                        MarketplaceLockEntryRejection.DuplicateAssetName(asset.name),
                    )
                }
                if (!digests.add(asset.sha256)) {
                    return MarketplaceLockEntryCreation.Rejected(
                        MarketplaceLockEntryRejection.DuplicateAssetDigest(asset.sha256),
                    )
                }
            }
            return MarketplaceLockEntryCreation.Created(
                MarketplaceLockEntry(marketplaceId, source, index, checksum, ordered),
            )
        }

        private fun validateExpectedName(
            asset: LockedAsset,
            expected: ReleaseAssetName,
        ): MarketplaceLockEntryCreation.Rejected? =
            if (asset.name == expected) {
                null
            } else {
                MarketplaceLockEntryCreation.Rejected(
                    MarketplaceLockEntryRejection.UnexpectedAssetName(expected, asset.name),
                )
            }

        private fun validateAsset(
            source: MarketplaceLockSource,
            asset: LockedAsset,
        ): MarketplaceLockEntryCreation.Rejected? {
            if (asset.byteSize !in 1..MAX_RELEASE_ARTIFACT_BYTES) {
                return MarketplaceLockEntryCreation.Rejected(
                    MarketplaceLockEntryRejection.InvalidAssetSize(asset.name, asset.byteSize),
                )
            }
            val matchingKind =
                when (source) {
                    is MarketplaceLockSource.GitHubRelease -> asset is LockedAsset.GitHub
                    is MarketplaceLockSource.LocalSnapshot -> asset is LockedAsset.Local
                }
            return if (matchingKind) {
                null
            } else {
                MarketplaceLockEntryCreation.Rejected(
                    MarketplaceLockEntryRejection.MixedAssetEvidence(asset.name),
                )
            }
        }
    }
}

internal sealed interface MarketplaceLockEntryCreation {
    data class Created(val entry: MarketplaceLockEntry) : MarketplaceLockEntryCreation

    data class Rejected(val reason: MarketplaceLockEntryRejection) : MarketplaceLockEntryCreation
}

internal sealed interface MarketplaceLockEntryRejection {
    data object NoPackages : MarketplaceLockEntryRejection

    data class TooManyPackages(val actual: Int, val maximum: Int) : MarketplaceLockEntryRejection

    data class DuplicatePackage(val packageName: PackageName) : MarketplaceLockEntryRejection

    data class MixedAssetEvidence(val asset: ReleaseAssetName) : MarketplaceLockEntryRejection

    data class InvalidAssetSize(
        val asset: ReleaseAssetName,
        val actualBytes: Int,
    ) : MarketplaceLockEntryRejection

    data class UnexpectedAssetName(
        val expected: ReleaseAssetName,
        val actual: ReleaseAssetName,
    ) : MarketplaceLockEntryRejection

    data class UnexpectedPackageArchiveName(
        val packageName: PackageName,
        val expected: ReleaseAssetName,
        val actual: ReleaseAssetName,
    ) : MarketplaceLockEntryRejection

    data class DuplicateAssetName(val name: ReleaseAssetName) : MarketplaceLockEntryRejection

    data class DuplicateAssetDigest(val digest: Sha256Digest) : MarketplaceLockEntryRejection
}

internal class MarketplaceLock private constructor(
    entries: List<MarketplaceLockEntry>,
    private val document: CanonicalJsonDocument,
) {
    val entries: List<MarketplaceLockEntry> = entries.toList()

    fun canonicalBytes(): ByteArray = document.bytes()

    fun sha256(): Sha256Digest = document.sha256()

    fun agreement(intent: MarketplaceIntent): MarketplaceLockAgreement {
        val expectedIds = intent.selections.map(MarketplaceIntentSelection::marketplaceId)
        val actualIds = entries.map(MarketplaceLockEntry::marketplaceId)
        if (expectedIds != actualIds) {
            return MarketplaceLockAgreement.Stale(
                MarketplaceLockStaleness.MarketplaceSetMismatch(expectedIds, actualIds),
            )
        }
        intent.selections.zip(entries).forEach { (selection, entry) ->
            if (!selection.source.matches(entry.source, entry.index)) {
                return MarketplaceLockAgreement.Stale(
                    MarketplaceLockStaleness.SourceMismatch(selection.marketplaceId),
                )
            }
            val expectedPackages = selection.packages
            val actualPackages = entry.packages.map(LockedPackage::name)
            if (expectedPackages != actualPackages) {
                return MarketplaceLockAgreement.Stale(
                    MarketplaceLockStaleness.PackageSetMismatch(
                        selection.marketplaceId,
                        expectedPackages,
                        actualPackages,
                    ),
                )
            }
        }
        return MarketplaceLockAgreement.Matched
    }

    companion object {
        fun parse(bytes: ByteArray): MarketplaceLockParsing = MarketplaceLockParser.parse(bytes)

        fun materialize(entries: List<MarketplaceLockEntry>): MarketplaceLockMaterialization {
            if (entries.isEmpty()) {
                return MarketplaceLockMaterialization.Rejected(MarketplaceLockRejection.NoEntries)
            }
            if (entries.size > MAX_MARKETPLACE_INTENT_SELECTIONS) {
                return MarketplaceLockMaterialization.Rejected(
                    MarketplaceLockRejection.TooManyEntries(entries.size, MAX_MARKETPLACE_INTENT_SELECTIONS),
                )
            }
            val ordered = entries.sortedBy { entry -> entry.marketplaceId.render() }
            val seen = mutableSetOf<MarketplaceId>()
            ordered.forEach { entry ->
                if (!seen.add(entry.marketplaceId)) {
                    return MarketplaceLockMaterialization.Rejected(
                        MarketplaceLockRejection.DuplicateMarketplace(entry.marketplaceId),
                    )
                }
            }
            val document =
                when (
                    val created =
                        CanonicalJsonDocument.create(
                            canonicalJsonObject(
                                "entries" to CanonicalJsonArray(ordered.map(MarketplaceLockEntry::canonicalValue)),
                                "schemaVersion" to canonicalJsonInteger(MARKETPLACE_LOCK_SCHEMA_VERSION.toLong()),
                                "type" to canonicalJsonString(MARKETPLACE_LOCK_TYPE),
                            ),
                        )
                ) {
                    is CanonicalJsonDocumentCreation.Created -> created.document
                    is CanonicalJsonDocumentCreation.Rejected -> {
                        val reason =
                            when (val rejection = created.reason) {
                                is CanonicalJsonDocumentRejection.SizeExceeded -> rejection
                            }
                        return MarketplaceLockMaterialization.Rejected(
                            MarketplaceLockRejection.JsonDocumentTooLarge(
                                reason.actualBytes,
                                reason.maximumBytes,
                            ),
                        )
                    }
                }
            return MarketplaceLockMaterialization.Materialized(MarketplaceLock(ordered, document))
        }
    }
}

internal sealed interface MarketplaceLockMaterialization {
    data class Materialized(val lock: MarketplaceLock) : MarketplaceLockMaterialization

    data class Rejected(val reason: MarketplaceLockRejection) : MarketplaceLockMaterialization
}

internal sealed interface MarketplaceLockParsing {
    data class Parsed(val lock: MarketplaceLock) : MarketplaceLockParsing

    data class Rejected(val reason: MarketplaceLockRejection) : MarketplaceLockParsing
}

internal sealed interface MarketplaceLockAgreement {
    data object Matched : MarketplaceLockAgreement

    data class Stale(val reason: MarketplaceLockStaleness) : MarketplaceLockAgreement
}

internal sealed interface MarketplaceLockStaleness {
    data class MarketplaceSetMismatch(
        val expected: List<MarketplaceId>,
        val actual: List<MarketplaceId>,
    ) : MarketplaceLockStaleness

    data class SourceMismatch(val marketplaceId: MarketplaceId) : MarketplaceLockStaleness

    data class PackageSetMismatch(
        val marketplaceId: MarketplaceId,
        val expected: List<PackageName>,
        val actual: List<PackageName>,
    ) : MarketplaceLockStaleness
}

internal sealed interface MarketplaceLockRejection {
    data class JsonDocumentTooLarge(val actualBytes: Long, val maximumBytes: Int) : MarketplaceLockRejection
    data object InvalidUtf8 : MarketplaceLockRejection
    data object MalformedJson : MarketplaceLockRejection
    data object RootMustBeObject : MarketplaceLockRejection
    data object NonCanonicalJson : MarketplaceLockRejection
    data class MissingField(val path: String, val field: String) : MarketplaceLockRejection
    data class UnknownField(val path: String, val field: String) : MarketplaceLockRejection
    data class WrongFieldType(val path: String, val expected: String) : MarketplaceLockRejection
    data class UnsupportedType(val actual: String) : MarketplaceLockRejection
    data class UnsupportedSchemaVersion(val actual: Long) : MarketplaceLockRejection
    data object NoEntries : MarketplaceLockRejection
    data class TooManyEntries(val actual: Int, val maximum: Int) : MarketplaceLockRejection
    data class DuplicateMarketplace(val marketplaceId: MarketplaceId) : MarketplaceLockRejection
    data class InvalidMarketplaceId(val entryIndex: Int, val reason: IdentifierRejection) : MarketplaceLockRejection
    data class UnsupportedSourceType(val entryIndex: Int, val actual: String) : MarketplaceLockRejection
    data class InvalidGitHubRepository(val entryIndex: Int, val reason: GitHubRepositoryUrlRejection) : MarketplaceLockRejection
    data class InvalidSnapshotTag(val entryIndex: Int, val reason: IdentifierRejection) : MarketplaceLockRejection
    data class InvalidReleaseId(val entryIndex: Int, val reason: PositiveEvidenceIdRejection) : MarketplaceLockRejection
    data class InvalidCommitSha(val entryIndex: Int, val reason: GitCommitShaRejection) : MarketplaceLockRejection
    data class ImmutableReleaseRequired(val entryIndex: Int) : MarketplaceLockRejection
    data class InvalidLocalDirectory(val entryIndex: Int, val reason: ConsumerRelativeDirectoryRejection) : MarketplaceLockRejection
    data class InvalidAssetName(val path: String, val reason: ReleaseAssetNameRejection) : MarketplaceLockRejection
    data class InvalidAssetId(val path: String, val reason: PositiveEvidenceIdRejection) : MarketplaceLockRejection
    data class InvalidAssetSize(val path: String, val actualBytes: Long) : MarketplaceLockRejection
    data class InvalidAssetDigest(val path: String, val reason: Sha256DigestRejection) : MarketplaceLockRejection
    data class InvalidPackageName(val entryIndex: Int, val packageIndex: Int, val reason: IdentifierRejection) : MarketplaceLockRejection
    data class EntryRejected(val entryIndex: Int, val reason: MarketplaceLockEntryRejection) : MarketplaceLockRejection
}

private fun MarketplaceIntentSource.matches(
    locked: MarketplaceLockSource,
    index: LockedAsset,
): Boolean =
    when (this) {
        is MarketplaceIntentSource.GitHubRelease ->
            locked is MarketplaceLockSource.GitHubRelease &&
                repository == locked.repository &&
                tag == locked.tag
        is MarketplaceIntentSource.LocalSnapshot ->
            locked is MarketplaceLockSource.LocalSnapshot &&
                directory == locked.directory &&
                indexSha256 == index.sha256
    }

private fun MarketplaceLockEntry.canonicalValue(): CanonicalJsonValue =
    canonicalJsonObject(
        "checksum" to checksum.canonicalValue(),
        "index" to index.canonicalValue(),
        "marketplaceId" to canonicalJsonString(marketplaceId.render()),
        "packages" to CanonicalJsonArray(packages.map(LockedPackage::canonicalValue)),
        "source" to source.canonicalValue(),
    )

private fun LockedPackage.canonicalValue(): CanonicalJsonValue =
    canonicalJsonObject(
        "archive" to archive.canonicalValue(),
        "name" to canonicalJsonString(name.render()),
    )

private fun LockedAsset.canonicalValue(): CanonicalJsonValue =
    when (this) {
        is LockedAsset.GitHub ->
            canonicalJsonObject(
                "assetId" to canonicalJsonInteger(assetId.render()),
                "name" to canonicalJsonString(name.render()),
                "sha256" to canonicalJsonString(sha256.render()),
                "size" to canonicalJsonInteger(byteSize.toLong()),
            )
        is LockedAsset.Local ->
            canonicalJsonObject(
                "name" to canonicalJsonString(name.render()),
                "sha256" to canonicalJsonString(sha256.render()),
                "size" to canonicalJsonInteger(byteSize.toLong()),
            )
    }

private fun MarketplaceLockSource.canonicalValue(): CanonicalJsonValue =
    when (this) {
        is MarketplaceLockSource.GitHubRelease ->
            canonicalJsonObject(
                "immutable" to CanonicalJsonBoolean(true),
                "releaseId" to canonicalJsonInteger(releaseId.render()),
                "repository" to canonicalJsonString(repository.render()),
                "tag" to canonicalJsonString(tag.render()),
                "tagCommitSha" to canonicalJsonString(tagCommitSha.render()),
                "type" to canonicalJsonString(GITHUB_RELEASE_SOURCE_TYPE),
            )
        is MarketplaceLockSource.LocalSnapshot ->
            canonicalJsonObject(
                "path" to canonicalJsonString(directory.render()),
                "type" to canonicalJsonString(LOCAL_SNAPSHOT_SOURCE_TYPE),
            )
    }

internal const val MARKETPLACE_LOCK_SCHEMA_VERSION = 1
internal const val MARKETPLACE_LOCK_TYPE = "MARKETPLACE_LOCK"
internal const val MAX_MARKETPLACE_LOCK_JSON_BYTES = 4 * 1024 * 1024
