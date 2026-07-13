package intelligence.cli.portable

internal enum class GitHubAssetContentType(
    private val wireName: String,
) {
    JSON("application/json"),
    ZIP("application/zip"),
    PLAIN_TEXT("text/plain"),
    ;

    fun render(): String = wireName

    companion object {
        fun parse(candidate: String): GitHubAssetContentType? =
            entries.singleOrNull { type -> type.wireName == candidate }
    }
}

internal data class GitHubReleaseAsset(
    val assetId: GitHubAssetId,
    val name: ReleaseAssetName,
    val byteSize: Int,
    val sha256: Sha256Digest,
    val contentType: GitHubAssetContentType,
)

internal class GitHubExactRelease internal constructor(
    val repository: GitHubRepository,
    val releaseId: GitHubReleaseId,
    val snapshotId: SnapshotId,
    val tagCommitSha: GitCommitSha,
    val immutable: Boolean,
    val draft: Boolean,
    assets: List<GitHubReleaseAsset>,
) {
    val assets: List<GitHubReleaseAsset> = assets.sortedBy { asset -> asset.name.render() }
}

internal data class GitHubReleaseCandidate(
    val snapshotId: SnapshotId,
    val advertisedName: String,
)

internal sealed interface GitHubReleaseListing {
    data class Listed(val candidates: List<GitHubReleaseCandidate>) : GitHubReleaseListing

    data class Rejected(val reason: GitHubReadTransportRejection) : GitHubReleaseListing
}

internal sealed interface GitHubReleaseResolution {
    data class Resolved(val release: GitHubExactRelease) : GitHubReleaseResolution

    data class Rejected(val reason: GitHubReadTransportRejection) : GitHubReleaseResolution
}

internal sealed interface GitHubAssetDownload {
    class Downloaded internal constructor(bytes: ByteArray) : GitHubAssetDownload {
        private val content = bytes.copyOf()

        fun bytes(): ByteArray = content.copyOf()
    }

    data class Rejected(val reason: GitHubReadTransportRejection) : GitHubAssetDownload
}

internal sealed interface GitHubReadTransportRejection {
    data object Unavailable : GitHubReadTransportRejection

    data object AuthenticationFailed : GitHubReadTransportRejection

    data object NotFound : GitHubReadTransportRejection

    data object InvalidResponse : GitHubReadTransportRejection
}

internal interface GitHubReadTransport {
    fun list(repository: GitHubRepository): GitHubReleaseListing

    fun resolve(
        repository: GitHubRepository,
        snapshotId: SnapshotId,
    ): GitHubReleaseResolution

    fun download(
        repository: GitHubRepository,
        release: GitHubExactRelease,
        asset: GitHubReleaseAsset,
    ): GitHubAssetDownload
}

internal sealed interface GitHubMarketplaceDiscovery {
    data class Discovered(val candidates: List<GitHubReleaseCandidate>) : GitHubMarketplaceDiscovery

    data class Rejected(val reason: GitHubReadTransportRejection) : GitHubMarketplaceDiscovery

    companion object {
        fun discover(
            repository: GitHubRepository,
            query: String?,
            transport: GitHubReadTransport,
        ): GitHubMarketplaceDiscovery =
            when (val listing = transport.list(repository)) {
                is GitHubReleaseListing.Rejected -> Rejected(listing.reason)
                is GitHubReleaseListing.Listed -> {
                    val normalizedQuery = query?.trim()?.lowercase().orEmpty()
                    val candidates =
                        listing.candidates
                            .filter { candidate ->
                                normalizedQuery.isEmpty() ||
                                    candidate.snapshotId.render().lowercase().contains(normalizedQuery) ||
                                    candidate.advertisedName.lowercase().contains(normalizedQuery)
                            }
                            .sortedBy { candidate -> candidate.snapshotId.render() }
                    Discovered(candidates)
                }
            }
    }
}

internal sealed interface GitHubSnapshotInspection {
    class Inspected internal constructor(
        val release: GitHubExactRelease,
        val index: MarketplaceSnapshotIndex,
        internal val indexAsset: GitHubReleaseAsset,
        internal val checksumAsset: GitHubReleaseAsset,
        indexBytes: ByteArray,
        checksumBytes: ByteArray,
    ) : GitHubSnapshotInspection {
        private val indexContent = indexBytes.copyOf()
        private val checksumContent = checksumBytes.copyOf()

        internal fun indexBytes(): ByteArray = indexContent.copyOf()

        internal fun checksumBytes(): ByteArray = checksumContent.copyOf()
    }

    data class Rejected(val reason: GitHubSnapshotRejection) : GitHubSnapshotInspection
}

internal sealed interface GitHubSnapshotRejection {
    data class TransportRejected(val reason: GitHubReadTransportRejection) : GitHubSnapshotRejection

    data class RepositoryMismatch(
        val expected: GitHubRepository,
        val actual: GitHubRepository,
    ) : GitHubSnapshotRejection

    data class SnapshotMismatch(
        val expected: SnapshotId,
        val actual: SnapshotId,
    ) : GitHubSnapshotRejection

    data class ReleaseIsDraft(val snapshotId: SnapshotId) : GitHubSnapshotRejection

    data class ReleaseNotImmutable(val snapshotId: SnapshotId) : GitHubSnapshotRejection

    data class DuplicateAssetName(val name: ReleaseAssetName) : GitHubSnapshotRejection

    data class DuplicateAssetId(val assetId: GitHubAssetId) : GitHubSnapshotRejection

    data class InvalidAssetSize(
        val name: ReleaseAssetName,
        val actual: Int,
    ) : GitHubSnapshotRejection

    data class AssetSetMismatch(
        val expected: List<ReleaseAssetName>,
        val actual: List<ReleaseAssetName>,
    ) : GitHubSnapshotRejection

    data class AssetEvidenceMismatch(val name: ReleaseAssetName) : GitHubSnapshotRejection

    data class ContentTypeMismatch(
        val name: ReleaseAssetName,
        val expected: GitHubAssetContentType,
        val actual: GitHubAssetContentType,
    ) : GitHubSnapshotRejection

    data class DownloadRejected(
        val name: ReleaseAssetName,
        val reason: GitHubAssetDownloadRejection,
    ) : GitHubSnapshotRejection

    data class IndexRejected(val reason: MarketplaceSnapshotIndexRejection) : GitHubSnapshotRejection

    data class ChecksumMismatch(val snapshotId: SnapshotId) : GitHubSnapshotRejection

    data class LockEntryRejected(val reason: MarketplaceLockEntryRejection) : GitHubSnapshotRejection
}

internal sealed interface GitHubAssetDownloadRejection {
    data class TransportRejected(val reason: GitHubReadTransportRejection) : GitHubAssetDownloadRejection

    data class SizeMismatch(
        val expected: Int,
        val actual: Int,
    ) : GitHubAssetDownloadRejection

    data class DigestMismatch(
        val expected: Sha256Digest,
        val actual: Sha256Digest,
    ) : GitHubAssetDownloadRejection

    data class CacheRejected(val reason: DigestCacheRejection) : GitHubAssetDownloadRejection
}

internal object GitHubSnapshotResolver {
    fun inspect(
        repository: GitHubRepository,
        snapshotId: SnapshotId,
        transport: GitHubReadTransport,
    ): GitHubSnapshotInspection {
        val release =
            when (val resolution = transport.resolve(repository, snapshotId)) {
                is GitHubReleaseResolution.Resolved -> resolution.release
                is GitHubReleaseResolution.Rejected -> {
                    return GitHubSnapshotInspection.Rejected(
                        GitHubSnapshotRejection.TransportRejected(resolution.reason),
                    )
                }
            }
        if (release.repository != repository) {
            return GitHubSnapshotInspection.Rejected(
                GitHubSnapshotRejection.RepositoryMismatch(repository, release.repository),
            )
        }
        if (release.snapshotId != snapshotId) {
            return GitHubSnapshotInspection.Rejected(
                GitHubSnapshotRejection.SnapshotMismatch(snapshotId, release.snapshotId),
            )
        }
        if (release.draft) {
            return GitHubSnapshotInspection.Rejected(GitHubSnapshotRejection.ReleaseIsDraft(snapshotId))
        }
        if (!release.immutable) {
            return GitHubSnapshotInspection.Rejected(GitHubSnapshotRejection.ReleaseNotImmutable(snapshotId))
        }
        duplicateAssetName(release.assets)?.let { duplicate ->
            return GitHubSnapshotInspection.Rejected(GitHubSnapshotRejection.DuplicateAssetName(duplicate))
        }
        duplicateAssetId(release.assets)?.let { duplicate ->
            return GitHubSnapshotInspection.Rejected(GitHubSnapshotRejection.DuplicateAssetId(duplicate))
        }
        release.assets.firstOrNull { asset -> asset.byteSize !in 1..MAX_RELEASE_ARTIFACT_BYTES }?.let { asset ->
            return GitHubSnapshotInspection.Rejected(
                GitHubSnapshotRejection.InvalidAssetSize(asset.name, asset.byteSize),
            )
        }
        val assetsByName = release.assets.associateBy(GitHubReleaseAsset::name)
        val indexName = ReleaseAssetName.snapshotIndex()
        val indexAsset = assetsByName[indexName]
            ?: return GitHubSnapshotInspection.Rejected(
                GitHubSnapshotRejection.AssetSetMismatch(listOf(indexName), release.assets.map(GitHubReleaseAsset::name)),
            )
        expectedContentType(indexName, GitHubAssetContentType.JSON, indexAsset)?.let { return it }
        val indexBytes =
            when (val downloaded = downloadVerified(repository, release, indexAsset, transport)) {
                is GitHubAssetAcquisition.Acquired -> downloaded.bytes
                is GitHubAssetAcquisition.Rejected -> {
                    return GitHubSnapshotInspection.Rejected(
                        GitHubSnapshotRejection.DownloadRejected(indexName, downloaded.reason),
                    )
                }
            }
        val index =
            when (val parsed = MarketplaceSnapshotIndex.parse(indexBytes)) {
                is MarketplaceSnapshotIndexParsing.Parsed -> parsed.index
                is MarketplaceSnapshotIndexParsing.Rejected -> {
                    return GitHubSnapshotInspection.Rejected(GitHubSnapshotRejection.IndexRejected(parsed.reason))
                }
            }
        if (index.snapshotId != snapshotId) {
            return GitHubSnapshotInspection.Rejected(
                GitHubSnapshotRejection.SnapshotMismatch(snapshotId, index.snapshotId),
            )
        }
        val checksumName = index.checksumAsset
        val expectedNames =
            (index.packages.map { record -> record.archive.name } +
                index.projections.map { record -> record.archive.name } +
                listOf(indexName, checksumName))
                .sortedBy(ReleaseAssetName::render)
        val actualNames = release.assets.map(GitHubReleaseAsset::name).sortedBy(ReleaseAssetName::render)
        if (actualNames != expectedNames) {
            return GitHubSnapshotInspection.Rejected(
                GitHubSnapshotRejection.AssetSetMismatch(expectedNames, actualNames),
            )
        }
        index.packages.forEach { record ->
            val asset = assetsByName.getValue(record.archive.name)
            if (asset.byteSize != record.archive.byteSize || asset.sha256 != record.archive.sha256) {
                return GitHubSnapshotInspection.Rejected(
                    GitHubSnapshotRejection.AssetEvidenceMismatch(record.archive.name),
                )
            }
            expectedContentType(record.archive.name, GitHubAssetContentType.ZIP, asset)?.let { return it }
        }
        index.projections.forEach { record ->
            val asset = assetsByName.getValue(record.archive.name)
            if (asset.byteSize != record.archive.byteSize || asset.sha256 != record.archive.sha256) {
                return GitHubSnapshotInspection.Rejected(
                    GitHubSnapshotRejection.AssetEvidenceMismatch(record.archive.name),
                )
            }
            expectedContentType(record.archive.name, GitHubAssetContentType.ZIP, asset)?.let { return it }
        }
        val checksumAsset = assetsByName.getValue(checksumName)
        expectedContentType(checksumName, GitHubAssetContentType.PLAIN_TEXT, checksumAsset)?.let { return it }
        val checksumBytes =
            when (val downloaded = downloadVerified(repository, release, checksumAsset, transport)) {
                is GitHubAssetAcquisition.Acquired -> downloaded.bytes
                is GitHubAssetAcquisition.Rejected -> {
                    return GitHubSnapshotInspection.Rejected(
                        GitHubSnapshotRejection.DownloadRejected(checksumName, downloaded.reason),
                    )
                }
            }
        if (!releaseChecksumMatches(index, indexBytes, checksumBytes)) {
            return GitHubSnapshotInspection.Rejected(GitHubSnapshotRejection.ChecksumMismatch(snapshotId))
        }
        return GitHubSnapshotInspection.Inspected(
            release,
            index,
            indexAsset,
            checksumAsset,
            indexBytes,
            checksumBytes,
        )
    }

    fun resolve(
        selection: MarketplaceIntentSelection,
        cache: DigestAddressedCache,
        transport: GitHubReadTransport,
        cacheWritePolicy: DigestCacheWritePolicy,
    ): MarketplaceResolution {
        val source =
            when (val candidate = selection.source) {
                is MarketplaceIntentSource.GitHubRelease -> candidate
                is MarketplaceIntentSource.LocalSnapshot -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.GitHubSourceRequired(selection.marketplaceId),
                    )
                }
            }
        val repository =
            when (
                val parsed =
                    GitHubRepository.parse(
                        source.repository.render().removePrefix("https://github.com/"),
                    )
            ) {
                is GitHubRepositoryParsing.Parsed -> parsed.repository
                is GitHubRepositoryParsing.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.GitHubRejected(
                            GitHubSnapshotRejection.TransportRejected(GitHubReadTransportRejection.InvalidResponse),
                        ),
                    )
                }
            }
        val inspection =
            when (val inspected = inspect(repository, source.tag, transport)) {
                is GitHubSnapshotInspection.Inspected -> inspected
                is GitHubSnapshotInspection.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.GitHubRejected(inspected.reason),
                    )
                }
            }
        if (inspection.index.marketplaceId != selection.marketplaceId) {
            return MarketplaceResolution.Rejected(
                MarketplaceResolutionRejection.MarketplaceMismatch(
                    selection.marketplaceId,
                    inspection.index.marketplaceId,
                ),
            )
        }
        val recordsByName = inspection.index.packages.associateBy(SnapshotPackageRecord::name)
        selection.packages.firstOrNull { name -> name !in recordsByName }?.let { missing ->
            return MarketplaceResolution.Rejected(MarketplaceResolutionRejection.MissingPackage(missing))
        }
        val assetsByName = inspection.release.assets.associateBy(GitHubReleaseAsset::name)
        val lockedPackages =
            selection.packages.map { packageName ->
                val record = recordsByName.getValue(packageName)
                val asset = assetsByName.getValue(record.archive.name)
                LockedPackage(packageName, asset.locked())
            }
        val lockEntry =
            when (
                val created =
                    MarketplaceLockEntry.create(
                        selection.marketplaceId,
                        MarketplaceLockSource.GitHubRelease(
                            source.repository,
                            source.tag,
                            inspection.release.releaseId,
                            inspection.release.tagCommitSha,
                        ),
                        inspection.indexAsset.locked(),
                        inspection.checksumAsset.locked(),
                        lockedPackages,
                    )
            ) {
                is MarketplaceLockEntryCreation.Created -> created.entry
                is MarketplaceLockEntryCreation.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.GitHubRejected(
                            GitHubSnapshotRejection.LockEntryRejected(created.reason),
                        ),
                    )
                }
            }
        val indexBytes = inspection.indexBytes()
        val checksumBytes = inspection.checksumBytes()
        cacheVerified(inspection.indexAsset, indexBytes, cache, cacheWritePolicy)?.let { return it }
        cacheVerified(inspection.checksumAsset, checksumBytes, cache, cacheWritePolicy)?.let { return it }
        val packageBytes = linkedMapOf<PackageName, ByteArray>()
        lockedPackages.forEach { locked ->
            val asset = assetsByName.getValue(locked.archive.name)
            val bytes =
                when (val acquired = acquireAsset(repository, inspection.release, asset, cache, transport, cacheWritePolicy)) {
                    is GitHubAssetAcquisition.Acquired -> acquired.bytes
                    is GitHubAssetAcquisition.Rejected -> {
                        return MarketplaceResolution.Rejected(
                            MarketplaceResolutionRejection.GitHubRejected(
                                GitHubSnapshotRejection.DownloadRejected(asset.name, acquired.reason),
                            ),
                        )
                    }
                }
            packageBytes[locked.name] = bytes
        }
        return verifyLockedMarketplace(lockEntry, indexBytes, checksumBytes, packageBytes)
    }

    fun reconstruct(
        lockEntry: MarketplaceLockEntry,
        cache: DigestAddressedCache,
        transport: GitHubReadTransport,
        cacheWritePolicy: DigestCacheWritePolicy,
    ): MarketplaceResolution {
        val source =
            when (val candidate = lockEntry.source) {
                is MarketplaceLockSource.GitHubRelease -> candidate
                is MarketplaceLockSource.LocalSnapshot -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.GitHubSourceRequired(lockEntry.marketplaceId),
                    )
                }
            }
        val repository =
            when (
                val parsed =
                    GitHubRepository.parse(
                        source.repository.render().removePrefix("https://github.com/"),
                    )
            ) {
                is GitHubRepositoryParsing.Parsed -> parsed.repository
                is GitHubRepositoryParsing.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.GitHubRejected(
                            GitHubSnapshotRejection.TransportRejected(GitHubReadTransportRejection.InvalidResponse),
                        ),
                    )
                }
            }
        val inspection =
            when (val inspected = inspect(repository, source.tag, transport)) {
                is GitHubSnapshotInspection.Inspected -> inspected
                is GitHubSnapshotInspection.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.GitHubRejected(inspected.reason),
                    )
                }
            }
        if (inspection.release.releaseId != source.releaseId ||
            inspection.release.tagCommitSha != source.tagCommitSha ||
            !inspection.indexAsset.matches(lockEntry.index) ||
            !inspection.checksumAsset.matches(lockEntry.checksum)
        ) {
            return MarketplaceResolution.Rejected(
                MarketplaceResolutionRejection.GitHubRejected(
                    GitHubSnapshotRejection.AssetEvidenceMismatch(inspection.indexAsset.name),
                ),
            )
        }
        val assetsByName = inspection.release.assets.associateBy(GitHubReleaseAsset::name)
        lockEntry.packages.forEach { locked ->
            val remote = assetsByName[locked.archive.name]
            if (remote == null || !remote.matches(locked.archive)) {
                return MarketplaceResolution.Rejected(
                    MarketplaceResolutionRejection.GitHubRejected(
                        GitHubSnapshotRejection.AssetEvidenceMismatch(locked.archive.name),
                    ),
                )
            }
        }
        val indexBytes = inspection.indexBytes()
        val checksumBytes = inspection.checksumBytes()
        cacheVerified(inspection.indexAsset, indexBytes, cache, cacheWritePolicy)?.let { return it }
        cacheVerified(inspection.checksumAsset, checksumBytes, cache, cacheWritePolicy)?.let { return it }
        val packageBytes = linkedMapOf<PackageName, ByteArray>()
        lockEntry.packages.forEach { locked ->
            val remote = assetsByName.getValue(locked.archive.name)
            val bytes =
                when (val acquired = acquireAsset(repository, inspection.release, remote, cache, transport, cacheWritePolicy)) {
                    is GitHubAssetAcquisition.Acquired -> acquired.bytes
                    is GitHubAssetAcquisition.Rejected -> {
                        return MarketplaceResolution.Rejected(
                            MarketplaceResolutionRejection.GitHubRejected(
                                GitHubSnapshotRejection.DownloadRejected(remote.name, acquired.reason),
                            ),
                        )
                    }
                }
            packageBytes[locked.name] = bytes
        }
        return verifyLockedMarketplace(lockEntry, indexBytes, checksumBytes, packageBytes)
    }
}

private sealed interface GitHubAssetAcquisition {
    data class Acquired(val bytes: ByteArray) : GitHubAssetAcquisition

    data class Rejected(val reason: GitHubAssetDownloadRejection) : GitHubAssetAcquisition
}

private fun acquireAsset(
    repository: GitHubRepository,
    release: GitHubExactRelease,
    asset: GitHubReleaseAsset,
    cache: DigestAddressedCache,
    transport: GitHubReadTransport,
    policy: DigestCacheWritePolicy,
): GitHubAssetAcquisition {
    val expectation = CacheBlobExpectation.from(asset)
    when (val cached = cache.read(expectation)) {
        is DigestCacheRead.Hit -> return GitHubAssetAcquisition.Acquired(cached.blob.bytes())
        DigestCacheRead.Miss -> Unit
        is DigestCacheRead.Rejected -> {
            return GitHubAssetAcquisition.Rejected(GitHubAssetDownloadRejection.CacheRejected(cached.reason))
        }
    }
    val downloaded = downloadVerified(repository, release, asset, transport)
    val bytes =
        when (downloaded) {
            is GitHubAssetAcquisition.Acquired -> downloaded.bytes
            is GitHubAssetAcquisition.Rejected -> return downloaded
        }
    return when (policy) {
        DigestCacheWritePolicy.VERIFY_ONLY -> GitHubAssetAcquisition.Acquired(bytes)
        DigestCacheWritePolicy.STORE -> {
            when (val inserted = cache.insert(expectation, bytes)) {
                is DigestCacheInsertion.Stored,
                is DigestCacheInsertion.AlreadyPresent,
                -> GitHubAssetAcquisition.Acquired(bytes)
                is DigestCacheInsertion.Rejected ->
                    GitHubAssetAcquisition.Rejected(GitHubAssetDownloadRejection.CacheRejected(inserted.reason))
            }
        }
    }
}

private fun downloadVerified(
    repository: GitHubRepository,
    release: GitHubExactRelease,
    asset: GitHubReleaseAsset,
    transport: GitHubReadTransport,
): GitHubAssetAcquisition =
    when (val download = transport.download(repository, release, asset)) {
        is GitHubAssetDownload.Rejected ->
            GitHubAssetAcquisition.Rejected(GitHubAssetDownloadRejection.TransportRejected(download.reason))
        is GitHubAssetDownload.Downloaded -> {
            val bytes = download.bytes()
            if (bytes.size != asset.byteSize) {
                GitHubAssetAcquisition.Rejected(
                    GitHubAssetDownloadRejection.SizeMismatch(asset.byteSize, bytes.size),
                )
            } else {
                val digest = Sha256Digest.compute(bytes)
                if (digest != asset.sha256) {
                    GitHubAssetAcquisition.Rejected(
                        GitHubAssetDownloadRejection.DigestMismatch(asset.sha256, digest),
                    )
                } else {
                    GitHubAssetAcquisition.Acquired(bytes)
                }
            }
        }
    }

private fun cacheVerified(
    asset: GitHubReleaseAsset,
    bytes: ByteArray,
    cache: DigestAddressedCache,
    policy: DigestCacheWritePolicy,
): MarketplaceResolution.Rejected? =
    when (policy) {
        DigestCacheWritePolicy.VERIFY_ONLY -> null
        DigestCacheWritePolicy.STORE -> {
            val expectation = CacheBlobExpectation.from(asset)
            when (val inserted = cache.insert(expectation, bytes)) {
                is DigestCacheInsertion.Stored,
                is DigestCacheInsertion.AlreadyPresent,
                -> null
                is DigestCacheInsertion.Rejected ->
                    MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.CacheRejected(asset.name, inserted.reason),
                    )
            }
        }
    }

private fun GitHubReleaseAsset.locked(): LockedAsset.GitHub =
    LockedAsset.GitHub(assetId, name, byteSize, sha256)

private fun GitHubReleaseAsset.matches(asset: LockedAsset): Boolean =
    asset is LockedAsset.GitHub &&
        assetId == asset.assetId &&
        name == asset.name &&
        byteSize == asset.byteSize &&
        sha256 == asset.sha256

private fun expectedContentType(
    name: ReleaseAssetName,
    expected: GitHubAssetContentType,
    asset: GitHubReleaseAsset,
): GitHubSnapshotInspection.Rejected? =
    if (asset.contentType == expected) {
        null
    } else {
        GitHubSnapshotInspection.Rejected(
            GitHubSnapshotRejection.ContentTypeMismatch(name, expected, asset.contentType),
        )
    }

private fun duplicateAssetName(assets: List<GitHubReleaseAsset>): ReleaseAssetName? {
    val seen = mutableSetOf<ReleaseAssetName>()
    return assets.firstOrNull { asset -> !seen.add(asset.name) }?.name
}

private fun duplicateAssetId(assets: List<GitHubReleaseAsset>): GitHubAssetId? {
    val seen = mutableSetOf<GitHubAssetId>()
    return assets.firstOrNull { asset -> !seen.add(asset.assetId) }?.assetId
}
