package intelligence.cli.portable

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Comparator

internal object MarketplaceReleaseDirectory {
    fun materialize(
        output: Path,
        marketplaceId: MarketplaceId,
        snapshotId: SnapshotId,
        defaultPackage: PackageName,
        packages: List<PackageArchive>,
    ): MarketplaceReleaseDirectoryMaterialization {
        val normalizedOutput = output.toAbsolutePath().normalize()
        val parent = normalizedOutput.parent
            ?: return MarketplaceReleaseDirectoryMaterialization.Rejected(
                MarketplaceReleaseDirectoryRejection.ParentUnavailable(normalizedOutput),
            )
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            return MarketplaceReleaseDirectoryMaterialization.Rejected(
                MarketplaceReleaseDirectoryRejection.ParentUnavailable(parent),
            )
        }

        val first =
            when (
                val materialized =
                    MarketplaceRelease.materialize(
                        marketplaceId,
                        snapshotId,
                        defaultPackage,
                        packages,
                    )
            ) {
                is MarketplaceReleaseMaterialization.Materialized -> materialized.release
                is MarketplaceReleaseMaterialization.Rejected -> {
                    return MarketplaceReleaseDirectoryMaterialization.Rejected(
                        MarketplaceReleaseDirectoryRejection.BuildRejected(
                            MarketplaceReleaseBuildPass.FIRST,
                            materialized.reason,
                        ),
                    )
                }
            }
        val second =
            when (
                val materialized =
                    MarketplaceRelease.materialize(
                        marketplaceId,
                        snapshotId,
                        defaultPackage,
                        packages,
                    )
            ) {
                is MarketplaceReleaseMaterialization.Materialized -> materialized.release
                is MarketplaceReleaseMaterialization.Rejected -> {
                    return MarketplaceReleaseDirectoryMaterialization.Rejected(
                        MarketplaceReleaseDirectoryRejection.BuildRejected(
                            MarketplaceReleaseBuildPass.SECOND,
                            materialized.reason,
                        ),
                    )
                }
            }
        when (val verification = first.verify(second.files())) {
            MarketplaceReleaseVerification.Verified -> Unit
            is MarketplaceReleaseVerification.Rejected -> {
                return MarketplaceReleaseDirectoryMaterialization.Rejected(
                    MarketplaceReleaseDirectoryRejection.NonDeterministicBuild(verification.reason),
                )
            }
        }

        if (Files.exists(normalizedOutput, LinkOption.NOFOLLOW_LINKS)) {
            val existing = readReleaseDirectory(normalizedOutput)
            if (existing is ReleaseDirectoryReading.Read &&
                first.verify(existing.files) == MarketplaceReleaseVerification.Verified
            ) {
                return MarketplaceReleaseDirectoryMaterialization.Unchanged(normalizedOutput, first)
            }
            return MarketplaceReleaseDirectoryMaterialization.Rejected(
                MarketplaceReleaseDirectoryRejection.OutputExists(normalizedOutput),
            )
        }

        val staging =
            try {
                Files.createTempDirectory(
                    parent,
                    ".${normalizedOutput.fileName}.intelligence-staging-",
                )
            } catch (_: IOException) {
                return MarketplaceReleaseDirectoryMaterialization.Rejected(
                    MarketplaceReleaseDirectoryRejection.StagingCreationFailed(parent),
                )
            } catch (_: SecurityException) {
                return MarketplaceReleaseDirectoryMaterialization.Rejected(
                    MarketplaceReleaseDirectoryRejection.StagingCreationFailed(parent),
                )
            }

        first.files().forEach { file ->
            try {
                Files.write(
                    staging.resolve(file.name.render()),
                    file.bytes(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
            } catch (_: IOException) {
                return rejectAfterCleanup(
                    staging,
                    MarketplaceReleaseDirectoryRejection.StagingWriteFailed(file.name),
                )
            } catch (_: SecurityException) {
                return rejectAfterCleanup(
                    staging,
                    MarketplaceReleaseDirectoryRejection.StagingWriteFailed(file.name),
                )
            }
        }

        val staged =
            when (val reading = readReleaseDirectory(staging)) {
                is ReleaseDirectoryReading.Read -> reading.files
                is ReleaseDirectoryReading.Rejected -> {
                    return rejectAfterCleanup(
                        staging,
                        MarketplaceReleaseDirectoryRejection.StagingReadRejected(reading.reason),
                    )
                }
            }
        when (val verification = first.verify(staged)) {
            MarketplaceReleaseVerification.Verified -> Unit
            is MarketplaceReleaseVerification.Rejected -> {
                return rejectAfterCleanup(
                    staging,
                    MarketplaceReleaseDirectoryRejection.StagingContentRejected(verification.reason),
                )
            }
        }

        if (Files.exists(normalizedOutput, LinkOption.NOFOLLOW_LINKS)) {
            return rejectAfterCleanup(
                staging,
                MarketplaceReleaseDirectoryRejection.OutputExists(normalizedOutput),
            )
        }
        try {
            Files.move(staging, normalizedOutput, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: IOException) {
            return rejectAfterCleanup(
                staging,
                MarketplaceReleaseDirectoryRejection.AtomicPromotionFailed(normalizedOutput),
            )
        } catch (_: SecurityException) {
            return rejectAfterCleanup(
                staging,
                MarketplaceReleaseDirectoryRejection.AtomicPromotionFailed(normalizedOutput),
            )
        }
        return MarketplaceReleaseDirectoryMaterialization.Written(normalizedOutput, first)
    }
}

internal enum class MarketplaceReleaseBuildPass {
    FIRST,
    SECOND,
}

internal sealed interface MarketplaceReleaseDirectoryMaterialization {
    data class Written(
        val output: Path,
        val release: MarketplaceRelease,
    ) : MarketplaceReleaseDirectoryMaterialization

    data class Unchanged(
        val output: Path,
        val release: MarketplaceRelease,
    ) : MarketplaceReleaseDirectoryMaterialization

    data class Rejected(
        val reason: MarketplaceReleaseDirectoryRejection,
    ) : MarketplaceReleaseDirectoryMaterialization
}

internal sealed interface MarketplaceReleaseDirectoryRejection {
    data class ParentUnavailable(val parent: Path) : MarketplaceReleaseDirectoryRejection

    data class BuildRejected(
        val pass: MarketplaceReleaseBuildPass,
        val reason: MarketplaceReleaseRejection,
    ) : MarketplaceReleaseDirectoryRejection

    data class NonDeterministicBuild(
        val reason: MarketplaceReleaseVerificationRejection,
    ) : MarketplaceReleaseDirectoryRejection

    data class OutputExists(val output: Path) : MarketplaceReleaseDirectoryRejection

    data class StagingCreationFailed(val parent: Path) : MarketplaceReleaseDirectoryRejection

    data class StagingWriteFailed(val asset: ReleaseAssetName) : MarketplaceReleaseDirectoryRejection

    data class StagingReadRejected(
        val reason: ReleaseDirectoryReadRejection,
    ) : MarketplaceReleaseDirectoryRejection

    data class StagingContentRejected(
        val reason: MarketplaceReleaseVerificationRejection,
    ) : MarketplaceReleaseDirectoryRejection

    data class AtomicPromotionFailed(val output: Path) : MarketplaceReleaseDirectoryRejection

    data class StagingCleanupFailed(val staging: Path) : MarketplaceReleaseDirectoryRejection
}

internal sealed interface ReleaseDirectoryReadRejection {
    data class NotDirectory(val path: Path) : ReleaseDirectoryReadRejection

    data class NonRegularEntry(val path: Path) : ReleaseDirectoryReadRejection

    data class InvalidAssetName(
        val candidate: String,
        val reason: ReleaseAssetNameRejection,
    ) : ReleaseDirectoryReadRejection

    data class InvalidAssetSize(
        val name: ReleaseAssetName,
        val actualBytes: Long,
    ) : ReleaseDirectoryReadRejection

    data class FileRejected(
        val name: ReleaseAssetName,
        val reason: ReleaseFileRejection,
    ) : ReleaseDirectoryReadRejection

    data class IoFailure(val path: Path) : ReleaseDirectoryReadRejection
}

private sealed interface ReleaseDirectoryReading {
    data class Read(val files: List<ReleaseFile>) : ReleaseDirectoryReading

    data class Rejected(val reason: ReleaseDirectoryReadRejection) : ReleaseDirectoryReading
}

private fun readReleaseDirectory(directory: Path): ReleaseDirectoryReading {
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        return ReleaseDirectoryReading.Rejected(ReleaseDirectoryReadRejection.NotDirectory(directory))
    }
    val entries =
        try {
            Files.list(directory).use { stream ->
                stream.sorted(Comparator.comparing { path: Path -> path.fileName.toString() }).toList()
            }
        } catch (_: IOException) {
            return ReleaseDirectoryReading.Rejected(ReleaseDirectoryReadRejection.IoFailure(directory))
        } catch (_: SecurityException) {
            return ReleaseDirectoryReading.Rejected(ReleaseDirectoryReadRejection.IoFailure(directory))
        }

    val files = mutableListOf<ReleaseFile>()
    entries.forEach { path ->
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return ReleaseDirectoryReading.Rejected(ReleaseDirectoryReadRejection.NonRegularEntry(path))
        }
        val candidate = path.fileName.toString()
        val name =
            when (val parsed = ReleaseAssetName.parse(candidate)) {
                is ReleaseAssetNameParsing.Parsed -> parsed.name
                is ReleaseAssetNameParsing.Rejected -> {
                    return ReleaseDirectoryReading.Rejected(
                        ReleaseDirectoryReadRejection.InvalidAssetName(candidate, parsed.reason),
                    )
                }
            }
        val size =
            try {
                Files.size(path)
            } catch (_: IOException) {
                return ReleaseDirectoryReading.Rejected(ReleaseDirectoryReadRejection.IoFailure(path))
            } catch (_: SecurityException) {
                return ReleaseDirectoryReading.Rejected(ReleaseDirectoryReadRejection.IoFailure(path))
            }
        if (size !in 1..MAX_RELEASE_ARTIFACT_BYTES.toLong()) {
            return ReleaseDirectoryReading.Rejected(
                ReleaseDirectoryReadRejection.InvalidAssetSize(name, size),
            )
        }
        val bytes =
            try {
                Files.readAllBytes(path)
            } catch (_: IOException) {
                return ReleaseDirectoryReading.Rejected(ReleaseDirectoryReadRejection.IoFailure(path))
            } catch (_: SecurityException) {
                return ReleaseDirectoryReading.Rejected(ReleaseDirectoryReadRejection.IoFailure(path))
            }
        when (val created = ReleaseFile.create(name, bytes)) {
            is ReleaseFileCreation.Created -> files += created.file
            is ReleaseFileCreation.Rejected -> {
                return ReleaseDirectoryReading.Rejected(
                    ReleaseDirectoryReadRejection.FileRejected(name, created.reason),
                )
            }
        }
    }
    return ReleaseDirectoryReading.Read(files)
}

private fun rejectAfterCleanup(
    staging: Path,
    reason: MarketplaceReleaseDirectoryRejection,
): MarketplaceReleaseDirectoryMaterialization =
    if (deleteStaging(staging)) {
        MarketplaceReleaseDirectoryMaterialization.Rejected(reason)
    } else {
        MarketplaceReleaseDirectoryMaterialization.Rejected(
            MarketplaceReleaseDirectoryRejection.StagingCleanupFailed(staging),
        )
    }

private fun deleteStaging(staging: Path): Boolean =
    try {
        if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            Files.walk(staging).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
