package intelligence.cli.portable

import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException

@JvmInline
internal value class GitHubReleaseUrl private constructor(
    private val value: String,
) {
    fun render(): String = value

    companion object {
        fun parse(
            candidate: String,
            repository: GitHubRepository,
            snapshotId: SnapshotId,
        ): GitHubReleaseUrlParsing {
            val expected = "https://github.com/${repository.render()}/releases/tag/${snapshotId.render()}"
            return if (candidate == expected) {
                GitHubReleaseUrlParsing.Parsed(GitHubReleaseUrl(candidate))
            } else {
                GitHubReleaseUrlParsing.Rejected(GitHubReleaseUrlRejection.NonCanonical)
            }
        }
    }
}

internal sealed interface GitHubReleaseUrlParsing {
    data class Parsed(val url: GitHubReleaseUrl) : GitHubReleaseUrlParsing

    data class Rejected(val reason: GitHubReleaseUrlRejection) : GitHubReleaseUrlParsing
}

internal enum class GitHubReleaseUrlRejection {
    NonCanonical,
}

@JvmInline
internal value class GitHubPublishedTimestamp private constructor(
    private val value: String,
) {
    fun render(): String = value

    companion object {
        fun parse(candidate: String): GitHubPublishedTimestampParsing =
            try {
                val instant = Instant.parse(candidate)
                if (instant.toString() == candidate) {
                    GitHubPublishedTimestampParsing.Parsed(GitHubPublishedTimestamp(candidate))
                } else {
                    GitHubPublishedTimestampParsing.Rejected(GitHubPublishedTimestampRejection.NonCanonical)
                }
            } catch (_: DateTimeParseException) {
                GitHubPublishedTimestampParsing.Rejected(GitHubPublishedTimestampRejection.Invalid)
            }
    }
}

internal sealed interface GitHubPublishedTimestampParsing {
    data class Parsed(val timestamp: GitHubPublishedTimestamp) : GitHubPublishedTimestampParsing

    data class Rejected(val reason: GitHubPublishedTimestampRejection) : GitHubPublishedTimestampParsing
}

internal enum class GitHubPublishedTimestampRejection {
    Invalid,
    NonCanonical,
}

internal data class GitHubPublicationRequest(
    val repository: GitHubRepository,
    val commit: GitCommitSha,
    val snapshotId: SnapshotId,
)

internal data class GitHubDraftRelease(
    val repository: GitHubRepository,
    val releaseId: GitHubReleaseId,
    val snapshotId: SnapshotId,
    val commit: GitCommitSha,
)

internal data class GitHubPublishedRelease(
    val repositoryId: GitHubRepositoryId,
    val release: GitHubExactRelease,
    val htmlUrl: GitHubReleaseUrl,
    val publishedAt: GitHubPublishedTimestamp,
)

internal sealed interface GitHubPublicationPreflight {
    data class Ready(val repositoryId: GitHubRepositoryId) : GitHubPublicationPreflight

    data class Conflict(val reason: GitHubPublicationTransportRejection) : GitHubPublicationPreflight

    data class Rejected(val reason: GitHubPublicationTransportRejection) : GitHubPublicationPreflight
}

internal sealed interface GitHubRemoteMutation<out T> {
    data class Completed<T>(val value: T) : GitHubRemoteMutation<T>

    data class Rejected(val reason: GitHubPublicationTransportRejection) : GitHubRemoteMutation<Nothing>

    data class Unknown(val releaseId: GitHubReleaseId?) : GitHubRemoteMutation<Nothing>
}

internal data object GitHubMutationComplete

internal sealed interface GitHubPublicationRead<out T> {
    data class Read<T>(val value: T) : GitHubPublicationRead<T>

    data class Rejected(val reason: GitHubPublicationTransportRejection) : GitHubPublicationRead<Nothing>
}

internal sealed interface GitHubPublicationLookup {
    data object Absent : GitHubPublicationLookup

    data class Draft(val releaseId: GitHubReleaseId) : GitHubPublicationLookup

    data class Published(val release: GitHubPublishedRelease) : GitHubPublicationLookup

    data class Rejected(val reason: GitHubPublicationTransportRejection) : GitHubPublicationLookup
}

internal sealed interface GitHubDraftCleanup {
    data object Cleared : GitHubDraftCleanup

    data class Retained(val releaseId: GitHubReleaseId) : GitHubDraftCleanup

    data class Unknown(val releaseId: GitHubReleaseId) : GitHubDraftCleanup
}

internal sealed interface GitHubPublicationTransportRejection {
    data object Unavailable : GitHubPublicationTransportRejection

    data object AuthenticationFailed : GitHubPublicationTransportRejection

    data object RepositoryMismatch : GitHubPublicationTransportRejection

    data object CommitUnavailable : GitHubPublicationTransportRejection

    data object ImmutableReleasesDisabled : GitHubPublicationTransportRejection

    data object TagOrReleaseExists : GitHubPublicationTransportRejection

    data object InvalidResponse : GitHubPublicationTransportRejection

    data object MutationRejected : GitHubPublicationTransportRejection
}

internal interface GitHubPublicationTransport {
    fun preflight(request: GitHubPublicationRequest): GitHubPublicationPreflight

    fun createDraft(request: GitHubPublicationRequest): GitHubRemoteMutation<GitHubDraftRelease>

    fun upload(
        draft: GitHubDraftRelease,
        file: ReleaseFile,
        contentType: GitHubAssetContentType,
    ): GitHubRemoteMutation<GitHubReleaseAsset>

    fun listDraftAssets(draft: GitHubDraftRelease): GitHubPublicationRead<List<GitHubReleaseAsset>>

    fun downloadDraftAsset(
        draft: GitHubDraftRelease,
        asset: GitHubReleaseAsset,
    ): GitHubPublicationRead<ByteArray>

    fun publish(draft: GitHubDraftRelease): GitHubRemoteMutation<GitHubMutationComplete>

    fun lookup(
        repository: GitHubRepository,
        snapshotId: SnapshotId,
    ): GitHubPublicationLookup

    fun downloadPublishedAsset(
        release: GitHubPublishedRelease,
        asset: GitHubReleaseAsset,
    ): GitHubPublicationRead<ByteArray>

    fun cleanup(draft: GitHubDraftRelease): GitHubDraftCleanup
}

internal data class GitHubPublicationReceipt(
    val repository: GitHubRepository,
    val repositoryId: GitHubRepositoryId,
    val releaseId: GitHubReleaseId,
    val htmlUrl: GitHubReleaseUrl,
    val snapshotId: SnapshotId,
    val commit: GitCommitSha,
    val publishedAt: GitHubPublishedTimestamp,
    val immutable: Boolean,
    val assets: List<GitHubReleaseAsset>,
    val completedGates: List<String>,
)

internal sealed interface GitHubPublication {
    data class Prepared(
        val repository: GitHubRepository,
        val repositoryId: GitHubRepositoryId,
        val snapshotId: SnapshotId,
        val commit: GitCommitSha,
        val assets: List<SnapshotAssetEvidence>,
    ) : GitHubPublication

    data class Published(val receipt: GitHubPublicationReceipt) : GitHubPublication

    data class Rejected(val reason: GitHubPublicationRejection) : GitHubPublication

    data class DraftRetained(
        val releaseId: GitHubReleaseId,
        val reason: GitHubPublicationRejection,
    ) : GitHubPublication

    data class RemoteStateUnknown(
        val releaseId: GitHubReleaseId?,
        val snapshotId: SnapshotId,
    ) : GitHubPublication

    data class PublishedUnverified(
        val releaseId: GitHubReleaseId,
        val snapshotId: SnapshotId,
        val reason: GitHubPublicationRejection,
    ) : GitHubPublication
}

internal sealed interface GitHubPublicationRejection {
    data class LocalReleaseRejected(val reason: MarketplaceReleaseDirectoryInspectionRejection) :
        GitHubPublicationRejection

    data class PreflightRejected(val reason: GitHubPublicationTransportRejection) : GitHubPublicationRejection

    data class PreflightConflict(val reason: GitHubPublicationTransportRejection) : GitHubPublicationRejection

    data class RemoteRejected(val reason: GitHubPublicationTransportRejection) : GitHubPublicationRejection

    data class UploadEvidenceMismatch(val asset: ReleaseAssetName) : GitHubPublicationRejection

    data object DraftAssetSetMismatch : GitHubPublicationRejection

    data class DownloadEvidenceMismatch(val asset: ReleaseAssetName) : GitHubPublicationRejection

    data class FinalReleaseRejected(val reason: GitHubPublicationVerificationRejection) :
        GitHubPublicationRejection
}

internal sealed interface GitHubPublicationVerificationRejection {
    data object Absent : GitHubPublicationVerificationRejection

    data class Draft(val releaseId: GitHubReleaseId) : GitHubPublicationVerificationRejection

    data class RemoteRejected(val reason: GitHubPublicationTransportRejection) :
        GitHubPublicationVerificationRejection

    data object RepositoryMismatch : GitHubPublicationVerificationRejection

    data object SnapshotMismatch : GitHubPublicationVerificationRejection

    data object CommitMismatch : GitHubPublicationVerificationRejection

    data object ReleaseIdMismatch : GitHubPublicationVerificationRejection

    data object NotImmutable : GitHubPublicationVerificationRejection

    data object AssetEvidenceMismatch : GitHubPublicationVerificationRejection

    data class AssetDownloadRejected(val asset: ReleaseAssetName) : GitHubPublicationVerificationRejection

    data class ReleaseContentRejected(val reason: MarketplaceReleaseInspectionRejection) :
        GitHubPublicationVerificationRejection
}

internal sealed interface GitHubPublicationVerification {
    data class Verified(
        val release: GitHubPublishedRelease,
        val marketplace: MarketplaceRelease,
    ) : GitHubPublicationVerification

    data class Rejected(val reason: GitHubPublicationVerificationRejection) : GitHubPublicationVerification
}

internal object GitHubPublisher {
    fun publish(
        releaseDirectory: Path,
        repository: GitHubRepository,
        commit: GitCommitSha,
        transport: GitHubPublicationTransport,
        dryRun: Boolean,
    ): GitHubPublication {
        val local =
            when (val inspection = MarketplaceReleaseDirectory.inspect(releaseDirectory)) {
                is MarketplaceReleaseDirectoryInspection.Inspected -> inspection.release
                is MarketplaceReleaseDirectoryInspection.Rejected -> {
                    return GitHubPublication.Rejected(
                        GitHubPublicationRejection.LocalReleaseRejected(inspection.reason),
                    )
                }
            }
        val request = GitHubPublicationRequest(repository, commit, local.snapshotId)
        val repositoryId =
            when (val preflight = transport.preflight(request)) {
                is GitHubPublicationPreflight.Ready -> preflight.repositoryId
                is GitHubPublicationPreflight.Conflict -> {
                    return GitHubPublication.Rejected(
                        GitHubPublicationRejection.PreflightConflict(preflight.reason),
                    )
                }
                is GitHubPublicationPreflight.Rejected -> {
                    return GitHubPublication.Rejected(
                        GitHubPublicationRejection.PreflightRejected(preflight.reason),
                    )
                }
            }
        if (dryRun) {
            return GitHubPublication.Prepared(
                repository,
                repositoryId,
                local.snapshotId,
                commit,
                local.files().map { file -> SnapshotAssetEvidence(file.name, file.byteSize, file.sha256) },
            )
        }
        val draft =
            when (val created = transport.createDraft(request)) {
                is GitHubRemoteMutation.Completed -> created.value
                is GitHubRemoteMutation.Rejected -> {
                    return GitHubPublication.Rejected(GitHubPublicationRejection.RemoteRejected(created.reason))
                }
                is GitHubRemoteMutation.Unknown -> {
                    return GitHubPublication.RemoteStateUnknown(created.releaseId, local.snapshotId)
                }
            }
        val uploaded = mutableListOf<GitHubReleaseAsset>()
        local.files().forEach { file ->
            val contentType = publicationContentType(file.name)
            when (val upload = transport.upload(draft, file, contentType)) {
                is GitHubRemoteMutation.Completed -> {
                    val asset = upload.value
                    if (!asset.matches(file, contentType)) {
                        return cleanupAfterFailure(
                            draft,
                            local.snapshotId,
                            GitHubPublicationRejection.UploadEvidenceMismatch(file.name),
                            transport,
                        )
                    }
                    uploaded += asset
                }
                is GitHubRemoteMutation.Rejected -> {
                    return cleanupAfterFailure(
                        draft,
                        local.snapshotId,
                        GitHubPublicationRejection.RemoteRejected(upload.reason),
                        transport,
                    )
                }
                is GitHubRemoteMutation.Unknown -> {
                    return GitHubPublication.RemoteStateUnknown(upload.releaseId ?: draft.releaseId, local.snapshotId)
                }
            }
        }
        val listed =
            when (val listing = transport.listDraftAssets(draft)) {
                is GitHubPublicationRead.Read -> listing.value.sortedBy { asset -> asset.name.render() }
                is GitHubPublicationRead.Rejected -> {
                    return cleanupAfterFailure(
                        draft,
                        local.snapshotId,
                        GitHubPublicationRejection.RemoteRejected(listing.reason),
                        transport,
                    )
                }
            }
        if (listed != uploaded.sortedBy { asset -> asset.name.render() }) {
            return cleanupAfterFailure(
                draft,
                local.snapshotId,
                GitHubPublicationRejection.DraftAssetSetMismatch,
                transport,
            )
        }
        local.files().forEach { file ->
            val asset = listed.single { candidate -> candidate.name == file.name }
            val bytes =
                when (val download = transport.downloadDraftAsset(draft, asset)) {
                    is GitHubPublicationRead.Read -> download.value
                    is GitHubPublicationRead.Rejected -> {
                        return cleanupAfterFailure(
                            draft,
                            local.snapshotId,
                            GitHubPublicationRejection.RemoteRejected(download.reason),
                            transport,
                        )
                    }
                }
            if (bytes.size != file.byteSize || Sha256Digest.compute(bytes) != file.sha256) {
                return cleanupAfterFailure(
                    draft,
                    local.snapshotId,
                    GitHubPublicationRejection.DownloadEvidenceMismatch(file.name),
                    transport,
                )
            }
        }
        when (val published = transport.publish(draft)) {
            is GitHubRemoteMutation.Completed -> Unit
            is GitHubRemoteMutation.Rejected -> {
                return cleanupAfterFailure(
                    draft,
                    local.snapshotId,
                    GitHubPublicationRejection.RemoteRejected(published.reason),
                    transport,
                )
            }
            is GitHubRemoteMutation.Unknown -> {
                return GitHubPublication.RemoteStateUnknown(published.releaseId ?: draft.releaseId, local.snapshotId)
            }
        }
        val verification =
            verifyPublishedMetadata(
                repository,
                local.snapshotId,
                expectedCommit = commit,
                expectedReleaseId = draft.releaseId,
                expectedAssets = listed,
                transport = transport,
            )
        val published =
            when (verification) {
                is GitHubPublicationMetadataVerification.Verified -> verification.release
                is GitHubPublicationMetadataVerification.Rejected -> {
                    return GitHubPublication.PublishedUnverified(
                        draft.releaseId,
                        local.snapshotId,
                        GitHubPublicationRejection.FinalReleaseRejected(verification.reason),
                    )
                }
            }
        return GitHubPublication.Published(
            GitHubPublicationReceipt(
                repository,
                repositoryId,
                draft.releaseId,
                published.htmlUrl,
                local.snapshotId,
                commit,
                published.publishedAt,
                published.release.immutable,
                listed,
                listOf("LOCAL_VALIDATE", "REMOTE_PREFLIGHT", "UPLOAD_READBACK", "IMMUTABLE_VERIFY"),
            ),
        )
    }
}

internal object GitHubPublicationVerifier {
    fun verify(
        repository: GitHubRepository,
        snapshotId: SnapshotId,
        transport: GitHubPublicationTransport,
    ): GitHubPublicationVerification {
        val metadata =
            when (
                val verification =
                    verifyPublishedMetadata(
                        repository,
                        snapshotId,
                        expectedCommit = null,
                        expectedReleaseId = null,
                        expectedAssets = null,
                        transport = transport,
                    )
            ) {
                is GitHubPublicationMetadataVerification.Verified -> verification.release
                is GitHubPublicationMetadataVerification.Rejected -> {
                    return GitHubPublicationVerification.Rejected(verification.reason)
                }
            }
        val files = mutableListOf<ReleaseFile>()
        metadata.release.assets.forEach { asset ->
            val bytes =
                when (val download = transport.downloadPublishedAsset(metadata, asset)) {
                    is GitHubPublicationRead.Read -> download.value
                    is GitHubPublicationRead.Rejected -> {
                        return GitHubPublicationVerification.Rejected(
                            GitHubPublicationVerificationRejection.AssetDownloadRejected(asset.name),
                        )
                    }
                }
            if (bytes.size != asset.byteSize || Sha256Digest.compute(bytes) != asset.sha256) {
                return GitHubPublicationVerification.Rejected(
                    GitHubPublicationVerificationRejection.AssetDownloadRejected(asset.name),
                )
            }
            when (val created = ReleaseFile.create(asset.name, bytes)) {
                is ReleaseFileCreation.Created -> files += created.file
                is ReleaseFileCreation.Rejected -> {
                    return GitHubPublicationVerification.Rejected(
                        GitHubPublicationVerificationRejection.AssetDownloadRejected(asset.name),
                    )
                }
            }
        }
        return when (val inspection = MarketplaceRelease.inspect(files)) {
            is MarketplaceReleaseInspection.Inspected ->
                GitHubPublicationVerification.Verified(metadata, inspection.release)
            is MarketplaceReleaseInspection.Rejected ->
                GitHubPublicationVerification.Rejected(
                    GitHubPublicationVerificationRejection.ReleaseContentRejected(inspection.reason),
                )
        }
    }
}

private sealed interface GitHubPublicationMetadataVerification {
    data class Verified(val release: GitHubPublishedRelease) : GitHubPublicationMetadataVerification

    data class Rejected(val reason: GitHubPublicationVerificationRejection) :
        GitHubPublicationMetadataVerification
}

private fun verifyPublishedMetadata(
    repository: GitHubRepository,
    snapshotId: SnapshotId,
    expectedCommit: GitCommitSha?,
    expectedReleaseId: GitHubReleaseId?,
    expectedAssets: List<GitHubReleaseAsset>?,
    transport: GitHubPublicationTransport,
): GitHubPublicationMetadataVerification {
    val published =
        when (val lookup = transport.lookup(repository, snapshotId)) {
            GitHubPublicationLookup.Absent -> {
                return GitHubPublicationMetadataVerification.Rejected(
                    GitHubPublicationVerificationRejection.Absent,
                )
            }
            is GitHubPublicationLookup.Draft -> {
                return GitHubPublicationMetadataVerification.Rejected(
                    GitHubPublicationVerificationRejection.Draft(lookup.releaseId),
                )
            }
            is GitHubPublicationLookup.Rejected -> {
                return GitHubPublicationMetadataVerification.Rejected(
                    GitHubPublicationVerificationRejection.RemoteRejected(lookup.reason),
                )
            }
            is GitHubPublicationLookup.Published -> lookup.release
        }
    val exact = published.release
    if (exact.repository != repository) {
        return GitHubPublicationMetadataVerification.Rejected(
            GitHubPublicationVerificationRejection.RepositoryMismatch,
        )
    }
    if (exact.snapshotId != snapshotId) {
        return GitHubPublicationMetadataVerification.Rejected(
            GitHubPublicationVerificationRejection.SnapshotMismatch,
        )
    }
    if (expectedCommit != null && exact.tagCommitSha != expectedCommit) {
        return GitHubPublicationMetadataVerification.Rejected(
            GitHubPublicationVerificationRejection.CommitMismatch,
        )
    }
    if (expectedReleaseId != null && exact.releaseId != expectedReleaseId) {
        return GitHubPublicationMetadataVerification.Rejected(
            GitHubPublicationVerificationRejection.ReleaseIdMismatch,
        )
    }
    if (exact.draft || !exact.immutable) {
        return GitHubPublicationMetadataVerification.Rejected(
            GitHubPublicationVerificationRejection.NotImmutable,
        )
    }
    if (expectedAssets != null &&
        exact.assets.sortedBy { asset -> asset.name.render() } != expectedAssets.sortedBy { asset -> asset.name.render() }
    ) {
        return GitHubPublicationMetadataVerification.Rejected(
            GitHubPublicationVerificationRejection.AssetEvidenceMismatch,
        )
    }
    return GitHubPublicationMetadataVerification.Verified(published)
}

private fun cleanupAfterFailure(
    draft: GitHubDraftRelease,
    snapshotId: SnapshotId,
    reason: GitHubPublicationRejection,
    transport: GitHubPublicationTransport,
): GitHubPublication =
    when (val cleanup = transport.cleanup(draft)) {
        GitHubDraftCleanup.Cleared -> GitHubPublication.Rejected(reason)
        is GitHubDraftCleanup.Retained -> GitHubPublication.DraftRetained(cleanup.releaseId, reason)
        is GitHubDraftCleanup.Unknown -> GitHubPublication.RemoteStateUnknown(cleanup.releaseId, snapshotId)
    }

private fun GitHubReleaseAsset.matches(
    file: ReleaseFile,
    expectedContentType: GitHubAssetContentType,
): Boolean =
    name == file.name &&
        byteSize == file.byteSize &&
        sha256 == file.sha256 &&
        contentType == expectedContentType

internal fun publicationContentType(name: ReleaseAssetName): GitHubAssetContentType =
    when {
        name.render() == "marketplace.json" -> GitHubAssetContentType.JSON
        name.render().endsWith(".zip") -> GitHubAssetContentType.ZIP
        else -> GitHubAssetContentType.PLAIN_TEXT
    }
