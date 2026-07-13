package intelligence.cli.portable

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal enum class DigestCacheWritePolicy {
    STORE,
    VERIFY_ONLY,
}

internal sealed interface LocalSnapshotInspection {
    class Inspected internal constructor(
        val index: MarketplaceSnapshotIndex,
        internal val directory: Path,
        internal val indexAsset: LocalSnapshotAsset,
        internal val checksumAsset: LocalSnapshotAsset,
    ) : LocalSnapshotInspection

    data class Rejected(val reason: MarketplaceResolutionRejection) : LocalSnapshotInspection
}

internal object LocalSnapshotResolver {
    fun inspect(
        consumerRoot: Path,
        marketplaceId: MarketplaceId,
        source: MarketplaceIntentSource.LocalSnapshot,
    ): LocalSnapshotInspection {
        val directory =
            when (val opened = openLocalSnapshotDirectory(consumerRoot, source.directory)) {
                is LocalSnapshotDirectoryOpening.Opened -> opened.path
                is LocalSnapshotDirectoryOpening.Rejected -> {
                    return LocalSnapshotInspection.Rejected(
                        MarketplaceResolutionRejection.LocalDirectoryRejected(source.directory, opened.reason),
                    )
                }
            }
        val indexName = ReleaseAssetName.snapshotIndex()
        val indexAsset =
            when (val read = readLocalAsset(directory, indexName, expected = null)) {
                is LocalSnapshotAssetRead.Read -> read.asset
                is LocalSnapshotAssetRead.Rejected -> {
                    return LocalSnapshotInspection.Rejected(
                        MarketplaceResolutionRejection.SourceAssetRejected(indexName, read.reason),
                    )
                }
            }
        if (indexAsset.expectation.sha256 != source.indexSha256) {
            return LocalSnapshotInspection.Rejected(
                MarketplaceResolutionRejection.IndexDigestMismatch(
                    expected = source.indexSha256,
                    actual = indexAsset.expectation.sha256,
                ),
            )
        }
        val index =
            when (val parsed = MarketplaceSnapshotIndex.parse(indexAsset.bytes)) {
                is MarketplaceSnapshotIndexParsing.Parsed -> parsed.index
                is MarketplaceSnapshotIndexParsing.Rejected -> {
                    return LocalSnapshotInspection.Rejected(
                        MarketplaceResolutionRejection.IndexRejected(parsed.reason),
                    )
                }
            }
        if (index.marketplaceId != marketplaceId) {
            return LocalSnapshotInspection.Rejected(
                MarketplaceResolutionRejection.MarketplaceMismatch(
                    expected = marketplaceId,
                    actual = index.marketplaceId,
                ),
            )
        }
        val checksumAsset =
            when (val read = readLocalAsset(directory, index.checksumAsset, expected = null)) {
                is LocalSnapshotAssetRead.Read -> read.asset
                is LocalSnapshotAssetRead.Rejected -> {
                    return LocalSnapshotInspection.Rejected(
                        MarketplaceResolutionRejection.SourceAssetRejected(index.checksumAsset, read.reason),
                    )
                }
            }
        if (!releaseChecksumMatches(index, indexAsset.bytes, checksumAsset.bytes)) {
            return LocalSnapshotInspection.Rejected(MarketplaceResolutionRejection.ChecksumMismatch)
        }
        return LocalSnapshotInspection.Inspected(index, directory, indexAsset, checksumAsset)
    }

    fun resolve(
        consumerRoot: Path,
        selection: MarketplaceIntentSelection,
        cache: DigestAddressedCache,
        cacheWritePolicy: DigestCacheWritePolicy = DigestCacheWritePolicy.STORE,
    ): MarketplaceResolution {
        val source =
            when (val selectedSource = selection.source) {
                is MarketplaceIntentSource.LocalSnapshot -> selectedSource
                is MarketplaceIntentSource.GitHubRelease -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.LocalSourceRequired(selection.marketplaceId),
                    )
                }
            }
        val inspection =
            when (val inspected = inspect(consumerRoot, selection.marketplaceId, source)) {
                is LocalSnapshotInspection.Inspected -> inspected
                is LocalSnapshotInspection.Rejected -> {
                    return MarketplaceResolution.Rejected(inspected.reason)
                }
            }
        val directory = inspection.directory
        val indexName = ReleaseAssetName.snapshotIndex()
        val indexAsset = inspection.indexAsset
        val index = inspection.index
        val recordsByName = index.packages.associateBy(SnapshotPackageRecord::name)
        selection.packages.firstOrNull { packageName -> packageName !in recordsByName }?.let { missing ->
            return MarketplaceResolution.Rejected(MarketplaceResolutionRejection.MissingPackage(missing))
        }

        val checksumName = index.checksumAsset
        val checksumAsset = inspection.checksumAsset

        cacheAsset(cache, indexName, indexAsset, cacheWritePolicy)?.let {
            return MarketplaceResolution.Rejected(it)
        }
        cacheAsset(cache, checksumName, checksumAsset, cacheWritePolicy)?.let {
            return MarketplaceResolution.Rejected(it)
        }

        val lockedPackages = mutableListOf<LockedPackage>()
        val packageArchives = mutableListOf<PackageArchive>()
        selection.packages.forEach { packageName ->
            val record = recordsByName[packageName]
                ?: return MarketplaceResolution.Rejected(
                    MarketplaceResolutionRejection.MissingPackage(packageName),
                )
            val expected = CacheBlobExpectation.from(record.archive)
            val sourceAsset =
                when (val read = readLocalAsset(directory, record.archive.name, expected)) {
                    is LocalSnapshotAssetRead.Read -> read.asset
                    is LocalSnapshotAssetRead.Rejected -> {
                        return MarketplaceResolution.Rejected(
                            MarketplaceResolutionRejection.SourceAssetRejected(record.archive.name, read.reason),
                        )
                    }
                }
            val archive =
                when (val parsed = PackageArchive.parse(sourceAsset.bytes)) {
                    is PackageArchiveParsing.Parsed -> parsed.archive
                    is PackageArchiveParsing.Rejected -> {
                        return MarketplaceResolution.Rejected(
                            MarketplaceResolutionRejection.PackageRejected(packageName, parsed.reason),
                        )
                    }
                }
            validatePackageIdentity(index.marketplaceId, packageName, archive)?.let { reason ->
                return MarketplaceResolution.Rejected(reason)
            }
            cacheAsset(cache, record.archive.name, sourceAsset, cacheWritePolicy)?.let { reason ->
                return MarketplaceResolution.Rejected(reason)
            }
            lockedPackages +=
                LockedPackage(
                    packageName,
                    LockedAsset.Local(record.archive.name, record.archive.byteSize, record.archive.sha256),
                )
            packageArchives += archive
        }

        val lockEntry =
            when (
                val created =
                    MarketplaceLockEntry.create(
                        marketplaceId = selection.marketplaceId,
                        source = MarketplaceLockSource.LocalSnapshot(source.directory),
                        index =
                            LockedAsset.Local(
                                indexName,
                                indexAsset.expectation.byteSize,
                                indexAsset.expectation.sha256,
                            ),
                        checksum =
                            LockedAsset.Local(
                                checksumName,
                                checksumAsset.expectation.byteSize,
                                checksumAsset.expectation.sha256,
                            ),
                        packages = lockedPackages,
                    )
            ) {
                is MarketplaceLockEntryCreation.Created -> created.entry
                is MarketplaceLockEntryCreation.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.LockEntryRejected(created.reason),
                    )
                }
            }
        return MarketplaceResolution.Resolved(ResolvedMarketplace(lockEntry, index, packageArchives))
    }

    fun reconstruct(
        consumerRoot: Path,
        lockEntry: MarketplaceLockEntry,
        cache: DigestAddressedCache,
        cacheWritePolicy: DigestCacheWritePolicy = DigestCacheWritePolicy.STORE,
    ): MarketplaceResolution {
        val source =
            when (val lockedSource = lockEntry.source) {
                is MarketplaceLockSource.LocalSnapshot -> lockedSource
                is MarketplaceLockSource.GitHubRelease -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.LocalSourceRequired(lockEntry.marketplaceId),
                    )
                }
            }
        val directory =
            when (val opened = openLocalSnapshotDirectory(consumerRoot, source.directory)) {
                is LocalSnapshotDirectoryOpening.Opened -> opened.path
                is LocalSnapshotDirectoryOpening.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.LocalDirectoryRejected(source.directory, opened.reason),
                    )
                }
            }
        val indexBytes =
            when (val acquisition = acquireLockedAsset(directory, lockEntry.index, cache, cacheWritePolicy)) {
                is LockedAssetAcquisition.Acquired -> acquisition.bytes
                is LockedAssetAcquisition.Rejected -> {
                    return MarketplaceResolution.Rejected(acquisition.reason)
                }
            }
        val checksumBytes =
            when (val acquisition = acquireLockedAsset(directory, lockEntry.checksum, cache, cacheWritePolicy)) {
                is LockedAssetAcquisition.Acquired -> acquisition.bytes
                is LockedAssetAcquisition.Rejected -> {
                    return MarketplaceResolution.Rejected(acquisition.reason)
                }
            }
        val packageBytes = linkedMapOf<PackageName, ByteArray>()
        lockEntry.packages.forEach { locked ->
            when (val acquisition = acquireLockedAsset(directory, locked.archive, cache, cacheWritePolicy)) {
                is LockedAssetAcquisition.Acquired -> packageBytes[locked.name] = acquisition.bytes
                is LockedAssetAcquisition.Rejected -> {
                    return MarketplaceResolution.Rejected(acquisition.reason)
                }
            }
        }
        return verifyLockedMarketplace(lockEntry, indexBytes, checksumBytes, packageBytes)
    }
}

internal object OfflineMarketplaceReconstructor {
    fun reconstruct(
        lockEntry: MarketplaceLockEntry,
        cache: DigestAddressedCache,
    ): MarketplaceResolution {
        val indexBytes =
            when (val read = cache.read(lockEntry.index.expectation())) {
                is DigestCacheRead.Hit -> read.blob.bytes()
                DigestCacheRead.Miss -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.OfflineCacheMiss(lockEntry.index.name),
                    )
                }
                is DigestCacheRead.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.CacheRejected(lockEntry.index.name, read.reason),
                    )
                }
            }
        val checksumBytes =
            when (val read = cache.read(lockEntry.checksum.expectation())) {
                is DigestCacheRead.Hit -> read.blob.bytes()
                DigestCacheRead.Miss -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.OfflineCacheMiss(lockEntry.checksum.name),
                    )
                }
                is DigestCacheRead.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.CacheRejected(lockEntry.checksum.name, read.reason),
                    )
                }
            }
        val packageBytes = linkedMapOf<PackageName, ByteArray>()
        lockEntry.packages.forEach { locked ->
            when (val read = cache.read(locked.archive.expectation())) {
                is DigestCacheRead.Hit -> packageBytes[locked.name] = read.blob.bytes()
                DigestCacheRead.Miss -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.OfflineCacheMiss(locked.archive.name),
                    )
                }
                is DigestCacheRead.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.CacheRejected(locked.archive.name, read.reason),
                    )
                }
            }
        }
        return verifyLockedMarketplace(lockEntry, indexBytes, checksumBytes, packageBytes)
    }
}

internal data class LocalSnapshotAsset(
    val expectation: CacheBlobExpectation,
    val bytes: ByteArray,
)

private sealed interface LocalSnapshotDirectoryOpening {
    data class Opened(val path: Path) : LocalSnapshotDirectoryOpening

    data class Rejected(val reason: LocalSnapshotDirectoryRejection) : LocalSnapshotDirectoryOpening
}

private sealed interface LocalSnapshotAssetRead {
    data class Read(val asset: LocalSnapshotAsset) : LocalSnapshotAssetRead

    data class Rejected(val reason: LocalSnapshotAssetRejection) : LocalSnapshotAssetRead
}

private sealed interface LockedAssetAcquisition {
    data class Acquired(val bytes: ByteArray) : LockedAssetAcquisition

    data class Rejected(val reason: MarketplaceResolutionRejection) : LockedAssetAcquisition
}

private fun openLocalSnapshotDirectory(
    consumerRoot: Path,
    relativeDirectory: ConsumerRelativeDirectory,
): LocalSnapshotDirectoryOpening {
    val root = consumerRoot.toAbsolutePath().normalize()
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
        return LocalSnapshotDirectoryOpening.Rejected(
            LocalSnapshotDirectoryRejection.ConsumerRootUnavailable(root),
        )
    }
    var current = root
    relativeDirectory.render().split('/').forEach { segment ->
        current = current.resolve(segment)
        if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            return LocalSnapshotDirectoryOpening.Rejected(
                LocalSnapshotDirectoryRejection.SegmentUnavailable(current),
            )
        }
    }
    return LocalSnapshotDirectoryOpening.Opened(current)
}

private fun readLocalAsset(
    directory: Path,
    name: ReleaseAssetName,
    expected: CacheBlobExpectation?,
): LocalSnapshotAssetRead {
    val path = directory.resolve(name.render())
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        return LocalSnapshotAssetRead.Rejected(LocalSnapshotAssetRejection.Missing(path))
    }
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        return LocalSnapshotAssetRead.Rejected(LocalSnapshotAssetRejection.NonRegular(path))
    }
    val bytes =
        try {
            FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
                val size = channel.size()
                if (size !in 1..MAX_RELEASE_ARTIFACT_BYTES.toLong()) {
                    return LocalSnapshotAssetRead.Rejected(
                        LocalSnapshotAssetRejection.InvalidSize(path, size),
                    )
                }
                if (expected != null && size != expected.byteSize.toLong()) {
                    return LocalSnapshotAssetRead.Rejected(
                        LocalSnapshotAssetRejection.SizeMismatch(expected.byteSize, size),
                    )
                }
                val content = ByteArray(size.toInt())
                val buffer = ByteBuffer.wrap(content)
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) {
                        return LocalSnapshotAssetRead.Rejected(LocalSnapshotAssetRejection.IoFailure(path))
                    }
                }
                if (channel.size() != size) {
                    return LocalSnapshotAssetRead.Rejected(LocalSnapshotAssetRejection.IoFailure(path))
                }
                content
            }
        } catch (_: IOException) {
            return LocalSnapshotAssetRead.Rejected(LocalSnapshotAssetRejection.IoFailure(path))
        } catch (_: SecurityException) {
            return LocalSnapshotAssetRead.Rejected(LocalSnapshotAssetRejection.IoFailure(path))
        }
    val actual = CacheBlobExpectation.fromVerified(bytes)
    if (expected != null && actual.sha256 != expected.sha256) {
        return LocalSnapshotAssetRead.Rejected(
            LocalSnapshotAssetRejection.DigestMismatch(expected.sha256, actual.sha256),
        )
    }
    return LocalSnapshotAssetRead.Read(
        LocalSnapshotAsset(actual, bytes),
    )
}

private fun cacheAsset(
    cache: DigestAddressedCache,
    name: ReleaseAssetName,
    asset: LocalSnapshotAsset,
    policy: DigestCacheWritePolicy,
): MarketplaceResolutionRejection.CacheRejected? =
    when (policy) {
        DigestCacheWritePolicy.STORE ->
            when (val inserted = cache.insert(asset.expectation, asset.bytes)) {
                is DigestCacheInsertion.Stored,
                is DigestCacheInsertion.AlreadyPresent,
                -> null
                is DigestCacheInsertion.Rejected ->
                    MarketplaceResolutionRejection.CacheRejected(name, inserted.reason)
            }
        DigestCacheWritePolicy.VERIFY_ONLY ->
            when (val existing = cache.read(asset.expectation)) {
                is DigestCacheRead.Hit,
                DigestCacheRead.Miss,
                -> null
                is DigestCacheRead.Rejected ->
                    MarketplaceResolutionRejection.CacheRejected(name, existing.reason)
            }
    }

private fun acquireLockedAsset(
    directory: Path,
    asset: LockedAsset,
    cache: DigestAddressedCache,
    policy: DigestCacheWritePolicy,
): LockedAssetAcquisition =
    when (val cached = cache.read(asset.expectation())) {
        is DigestCacheRead.Hit -> LockedAssetAcquisition.Acquired(cached.blob.bytes())
        is DigestCacheRead.Rejected ->
            LockedAssetAcquisition.Rejected(
                MarketplaceResolutionRejection.CacheRejected(asset.name, cached.reason),
            )
        DigestCacheRead.Miss -> {
            when (val read = readLocalAsset(directory, asset.name, asset.expectation())) {
                is LocalSnapshotAssetRead.Rejected ->
                    LockedAssetAcquisition.Rejected(
                        MarketplaceResolutionRejection.SourceAssetRejected(asset.name, read.reason),
                    )
                is LocalSnapshotAssetRead.Read -> {
                    when (policy) {
                        DigestCacheWritePolicy.VERIFY_ONLY ->
                            LockedAssetAcquisition.Acquired(read.asset.bytes)
                        DigestCacheWritePolicy.STORE -> {
                            when (val inserted = cache.insert(read.asset.expectation, read.asset.bytes)) {
                                is DigestCacheInsertion.Stored,
                                is DigestCacheInsertion.AlreadyPresent,
                                -> LockedAssetAcquisition.Acquired(read.asset.bytes)
                                is DigestCacheInsertion.Rejected ->
                                    LockedAssetAcquisition.Rejected(
                                        MarketplaceResolutionRejection.CacheRejected(asset.name, inserted.reason),
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

private fun LockedAsset.expectation(): CacheBlobExpectation =
    CacheBlobExpectation.from(this)

private fun releaseChecksumMatches(
    index: MarketplaceSnapshotIndex,
    indexBytes: ByteArray,
    checksumBytes: ByteArray,
): Boolean {
    val indexEvidence =
        SnapshotAssetEvidence(
            ReleaseAssetName.snapshotIndex(),
            indexBytes.size,
            Sha256Digest.compute(indexBytes),
        )
    val assets =
        index.packages.map(SnapshotPackageRecord::archive) +
            index.projections.map(SnapshotProjectionRecord::archive) +
            indexEvidence
    val expected =
        buildString {
            assets.sortedBy { asset -> asset.name.render() }.forEach { asset ->
                append(asset.sha256.render())
                append("  ")
                append(asset.name.render())
                append('\n')
            }
        }.encodeToByteArray()
    return expected.contentEquals(checksumBytes)
}

private fun verifyLockedMarketplace(
    lockEntry: MarketplaceLockEntry,
    indexBytes: ByteArray,
    checksumBytes: ByteArray,
    packageBytes: Map<PackageName, ByteArray>,
): MarketplaceResolution {
    val index =
        when (val parsed = MarketplaceSnapshotIndex.parse(indexBytes)) {
            is MarketplaceSnapshotIndexParsing.Parsed -> parsed.index
            is MarketplaceSnapshotIndexParsing.Rejected -> {
                return MarketplaceResolution.Rejected(
                    MarketplaceResolutionRejection.IndexRejected(parsed.reason),
                )
            }
        }
    if (index.marketplaceId != lockEntry.marketplaceId) {
        return MarketplaceResolution.Rejected(
            MarketplaceResolutionRejection.MarketplaceMismatch(lockEntry.marketplaceId, index.marketplaceId),
        )
    }
    if (!releaseChecksumMatches(index, indexBytes, checksumBytes)) {
        return MarketplaceResolution.Rejected(MarketplaceResolutionRejection.ChecksumMismatch)
    }
    val records = index.packages.associateBy(SnapshotPackageRecord::name)
    val archives = mutableListOf<PackageArchive>()
    lockEntry.packages.forEach { locked ->
        val record = records[locked.name]
            ?: return MarketplaceResolution.Rejected(
                MarketplaceResolutionRejection.MissingPackage(locked.name),
            )
        if (record.archive.name != locked.archive.name ||
            record.archive.byteSize != locked.archive.byteSize ||
            record.archive.sha256 != locked.archive.sha256
        ) {
            return MarketplaceResolution.Rejected(
                MarketplaceResolutionRejection.PackageEvidenceMismatch(locked.name),
            )
        }
        val bytes = packageBytes[locked.name]
            ?: return MarketplaceResolution.Rejected(
                MarketplaceResolutionRejection.OfflineCacheMiss(locked.archive.name),
            )
        val archive =
            when (val parsed = PackageArchive.parse(bytes)) {
                is PackageArchiveParsing.Parsed -> parsed.archive
                is PackageArchiveParsing.Rejected -> {
                    return MarketplaceResolution.Rejected(
                        MarketplaceResolutionRejection.PackageRejected(locked.name, parsed.reason),
                    )
                }
            }
        validatePackageIdentity(index.marketplaceId, locked.name, archive)?.let { reason ->
            return MarketplaceResolution.Rejected(reason)
        }
        archives += archive
    }
    return MarketplaceResolution.Resolved(ResolvedMarketplace(lockEntry, index, archives))
}

private fun validatePackageIdentity(
    expectedMarketplace: MarketplaceId,
    expectedPackage: PackageName,
    archive: PackageArchive,
): MarketplaceResolutionRejection.PackageIdentityMismatch? =
    if (archive.marketplaceId == expectedMarketplace && archive.packageName == expectedPackage) {
        null
    } else {
        MarketplaceResolutionRejection.PackageIdentityMismatch(
            expectedMarketplace,
            expectedPackage,
            archive.marketplaceId,
            archive.packageName,
        )
    }
