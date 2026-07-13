package intelligence.cli.portable

internal class ResolvedMarketplace internal constructor(
    val lockEntry: MarketplaceLockEntry,
    val index: MarketplaceSnapshotIndex,
    packages: List<PackageArchive>,
) {
    val packages: List<PackageArchive> = packages.toList()
}

internal sealed interface MarketplaceResolution {
    data class Resolved(val marketplace: ResolvedMarketplace) : MarketplaceResolution

    data class Rejected(val reason: MarketplaceResolutionRejection) : MarketplaceResolution
}

internal sealed interface MarketplaceResolutionRejection {
    data class LocalSourceRequired(val marketplaceId: MarketplaceId) : MarketplaceResolutionRejection

    data class LocalDirectoryRejected(
        val directory: ConsumerRelativeDirectory,
        val reason: LocalSnapshotDirectoryRejection,
    ) : MarketplaceResolutionRejection

    data class SourceAssetRejected(
        val asset: ReleaseAssetName,
        val reason: LocalSnapshotAssetRejection,
    ) : MarketplaceResolutionRejection

    data class IndexDigestMismatch(
        val expected: Sha256Digest,
        val actual: Sha256Digest,
    ) : MarketplaceResolutionRejection

    data class IndexRejected(val reason: MarketplaceSnapshotIndexRejection) : MarketplaceResolutionRejection

    data class MarketplaceMismatch(
        val expected: MarketplaceId,
        val actual: MarketplaceId,
    ) : MarketplaceResolutionRejection

    data class MissingPackage(val packageName: PackageName) : MarketplaceResolutionRejection

    data object ChecksumMismatch : MarketplaceResolutionRejection

    data class PackageEvidenceMismatch(val packageName: PackageName) : MarketplaceResolutionRejection

    data class PackageRejected(
        val packageName: PackageName,
        val reason: PackageArchiveParseRejection,
    ) : MarketplaceResolutionRejection

    data class PackageIdentityMismatch(
        val expectedMarketplace: MarketplaceId,
        val expectedPackage: PackageName,
        val actualMarketplace: MarketplaceId,
        val actualPackage: PackageName,
    ) : MarketplaceResolutionRejection

    data class CacheRejected(
        val asset: ReleaseAssetName,
        val reason: DigestCacheRejection,
    ) : MarketplaceResolutionRejection

    data class LockEntryRejected(val reason: MarketplaceLockEntryRejection) : MarketplaceResolutionRejection

    data class OfflineCacheMiss(val asset: ReleaseAssetName) : MarketplaceResolutionRejection
}

internal sealed interface LocalSnapshotDirectoryRejection {
    data class ConsumerRootUnavailable(val root: java.nio.file.Path) : LocalSnapshotDirectoryRejection

    data class SegmentUnavailable(val path: java.nio.file.Path) : LocalSnapshotDirectoryRejection
}

internal sealed interface LocalSnapshotAssetRejection {
    data class Missing(val path: java.nio.file.Path) : LocalSnapshotAssetRejection

    data class NonRegular(val path: java.nio.file.Path) : LocalSnapshotAssetRejection

    data class InvalidSize(
        val path: java.nio.file.Path,
        val actualBytes: Long,
    ) : LocalSnapshotAssetRejection

    data class SizeMismatch(
        val expectedBytes: Int,
        val actualBytes: Long,
    ) : LocalSnapshotAssetRejection

    data class DigestMismatch(
        val expected: Sha256Digest,
        val actual: Sha256Digest,
    ) : LocalSnapshotAssetRejection

    data class IoFailure(val path: java.nio.file.Path) : LocalSnapshotAssetRejection
}
