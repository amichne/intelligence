package intelligence.cli.portable

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object MarketplaceTransactionJournalParser {
    fun parse(bytes: ByteArray): MarketplaceTransactionJournalParsing {
        val root =
            when (
                val parsed =
                    StrictCanonicalJson.parseObject(
                        bytes,
                        MAX_MARKETPLACE_TRANSACTION_JSON_BYTES,
                    )
            ) {
                is StrictCanonicalJsonObjectParsing.Parsed -> parsed.root
                is StrictCanonicalJsonObjectParsing.Rejected -> {
                    return MarketplaceTransactionJournalParsing.Rejected(
                        parsed.reason.toTransactionRejection(),
                    )
                }
            }
        val decoded =
            try {
                MarketplaceTransactionJournalDecoder.decode(root)
            } catch (rejected: MarketplaceTransactionJournalRejected) {
                return MarketplaceTransactionJournalParsing.Rejected(rejected.reason)
            }
        val journal =
            when (
                val materialized =
                    MarketplaceTransactionJournal.materialize(
                        oldIntentSha256 = decoded.records[0].oldSha256,
                        oldLockSha256 = decoded.records[1].oldSha256,
                        newIntentSha256 = decoded.records[0].newSha256,
                        newLockSha256 = decoded.records[1].newSha256,
                    )
            ) {
                is MarketplaceTransactionJournalMaterialization.Materialized -> materialized.journal
                is MarketplaceTransactionJournalMaterialization.Rejected -> {
                    return MarketplaceTransactionJournalParsing.Rejected(materialized.reason)
                }
            }
        if (decoded.transactionId != journal.transactionId) {
            return MarketplaceTransactionJournalParsing.Rejected(
                MarketplaceTransactionJournalRejection.UnexpectedTransactionId,
            )
        }
        decoded.records.zip(journal.records).forEach { (actual, expected) ->
            if (actual.stagedPath != expected.stagedPath) {
                return MarketplaceTransactionJournalParsing.Rejected(
                    MarketplaceTransactionJournalRejection.UnexpectedStagedPath(expected.file),
                )
            }
            if (actual.backupPath != expected.backupPath) {
                return MarketplaceTransactionJournalParsing.Rejected(
                    MarketplaceTransactionJournalRejection.UnexpectedBackupPath(expected.file),
                )
            }
        }
        if (!bytes.contentEquals(journal.canonicalBytes())) {
            return MarketplaceTransactionJournalParsing.Rejected(
                MarketplaceTransactionJournalRejection.NonCanonicalJson,
            )
        }
        return MarketplaceTransactionJournalParsing.Parsed(journal)
    }
}

private object MarketplaceTransactionJournalDecoder {
    fun decode(root: JsonObject): DecodedMarketplaceTransactionJournal {
        root.requireTransactionFields("$", ROOT_FIELDS)
        val type = root.requiredTransactionString("$", "type")
        if (type != MARKETPLACE_TRANSACTION_TYPE) {
            transactionReject(MarketplaceTransactionJournalRejection.UnsupportedType(type))
        }
        val version = root.requiredTransactionLong("$", "schemaVersion")
        if (version != MARKETPLACE_TRANSACTION_SCHEMA_VERSION.toLong()) {
            transactionReject(MarketplaceTransactionJournalRejection.UnsupportedSchemaVersion(version))
        }
        val transactionId =
            when (val parsed = MarketplaceTransactionId.parse(root.requiredTransactionString("$", "transactionId"))) {
                is MarketplaceTransactionIdParsing.Parsed -> parsed.id
                is MarketplaceTransactionIdParsing.Rejected -> {
                    transactionReject(MarketplaceTransactionJournalRejection.InvalidTransactionId(parsed.reason))
                }
            }
        val elements = root.requiredTransactionArray("$", "files")
        if (elements.size != ConsumerPersistedFile.entries.size) {
            transactionReject(MarketplaceTransactionJournalRejection.UnexpectedFileSet)
        }
        val records = elements.mapIndexed { index, element -> decodeRecord(index, element) }
        if (records.map(TransactionFileRecord::file) != ConsumerPersistedFile.entries) {
            transactionReject(MarketplaceTransactionJournalRejection.UnexpectedFileSet)
        }
        return DecodedMarketplaceTransactionJournal(transactionId, records)
    }

    private fun decodeRecord(
        index: Int,
        element: JsonElement,
    ): TransactionFileRecord {
        val path = "$.files[$index]"
        val source = element.requiredTransactionObject(path)
        source.requireTransactionFields(path, FILE_FIELDS)
        val targetPath = source.requiredTransactionString(path, "path")
        val file = ConsumerPersistedFile.entries.singleOrNull { candidate -> candidate.targetPath == targetPath }
            ?: transactionReject(MarketplaceTransactionJournalRejection.InvalidTargetPath(index, targetPath))
        val oldDigest = source.requiredNullableDigest("$path.oldSha256")
        val newDigest = source.requiredDigest("$path.newSha256")
        val staged =
            when (val parsed = MarketplaceTransactionPath.parse(source.requiredTransactionString(path, "stagedPath"))) {
                is MarketplaceTransactionPathParsing.Parsed -> parsed.path
                MarketplaceTransactionPathParsing.Rejected -> {
                    transactionReject(MarketplaceTransactionJournalRejection.UnexpectedStagedPath(file))
                }
            }
        val backup =
            when (val parsed = MarketplaceTransactionPath.parse(source.requiredTransactionString(path, "backupPath"))) {
                is MarketplaceTransactionPathParsing.Parsed -> parsed.path
                MarketplaceTransactionPathParsing.Rejected -> {
                    transactionReject(MarketplaceTransactionJournalRejection.UnexpectedBackupPath(file))
                }
            }
        return TransactionFileRecord(file, oldDigest, newDigest, staged, backup)
    }
}

private data class DecodedMarketplaceTransactionJournal(
    val transactionId: MarketplaceTransactionId,
    val records: List<TransactionFileRecord>,
)

private class MarketplaceTransactionJournalRejected(
    val reason: MarketplaceTransactionJournalRejection,
) : RuntimeException(null, null, false, false)

private fun transactionReject(reason: MarketplaceTransactionJournalRejection): Nothing =
    throw MarketplaceTransactionJournalRejected(reason)

private fun StrictCanonicalJsonRejection.toTransactionRejection(): MarketplaceTransactionJournalRejection =
    when (this) {
        is StrictCanonicalJsonRejection.DocumentTooLarge ->
            MarketplaceTransactionJournalRejection.JsonDocumentTooLarge(actualBytes.toLong(), maximumBytes)
        StrictCanonicalJsonRejection.InvalidUtf8 -> MarketplaceTransactionJournalRejection.InvalidUtf8
        StrictCanonicalJsonRejection.MalformedJson -> MarketplaceTransactionJournalRejection.MalformedJson
        StrictCanonicalJsonRejection.RootMustBeObject -> MarketplaceTransactionJournalRejection.RootMustBeObject
        StrictCanonicalJsonRejection.NonCanonicalJson -> MarketplaceTransactionJournalRejection.NonCanonicalJson
    }

private fun JsonObject.requireTransactionFields(
    path: String,
    expected: List<String>,
) {
    expected.firstOrNull { field -> field !in this }?.let { missing ->
        transactionReject(MarketplaceTransactionJournalRejection.MissingField(path, missing))
    }
    keys.filterNot(expected::contains).sorted().firstOrNull()?.let { unknown ->
        transactionReject(MarketplaceTransactionJournalRejection.UnknownField(path, unknown))
    }
}

private fun JsonObject.requiredTransactionString(
    path: String,
    field: String,
): String = this[field].requiredTransactionString("$path.$field")

private fun JsonElement?.requiredTransactionString(path: String): String {
    val primitive = this as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        transactionReject(MarketplaceTransactionJournalRejection.WrongFieldType(path, "string"))
    }
    return primitive.content
}

private fun JsonObject.requiredTransactionLong(
    path: String,
    field: String,
): Long {
    val primitive = this[field] as? JsonPrimitive
    return primitive?.takeUnless(JsonPrimitive::isString)?.longOrNull
        ?: transactionReject(
            MarketplaceTransactionJournalRejection.WrongFieldType("$path.$field", "safe integer"),
        )
}

private fun JsonObject.requiredTransactionArray(
    path: String,
    field: String,
): JsonArray =
    this[field] as? JsonArray
        ?: transactionReject(MarketplaceTransactionJournalRejection.WrongFieldType("$path.$field", "array"))

private fun JsonElement.requiredTransactionObject(path: String): JsonObject =
    this as? JsonObject
        ?: transactionReject(MarketplaceTransactionJournalRejection.WrongFieldType(path, "object"))

private fun JsonObject.requiredNullableDigest(path: String): Sha256Digest? {
    val value = this[path.substringAfterLast('.')]
    if (value == JsonNull) return null
    return value.requiredDigest(path)
}

private fun JsonObject.requiredDigest(path: String): Sha256Digest =
    this[path.substringAfterLast('.')].requiredDigest(path)

private fun JsonElement?.requiredDigest(path: String): Sha256Digest {
    val candidate = requiredTransactionString(path)
    return when (val parsed = Sha256Digest.parse(candidate)) {
        is Sha256DigestParsing.Parsed -> parsed.digest
        is Sha256DigestParsing.Rejected -> {
            transactionReject(MarketplaceTransactionJournalRejection.InvalidDigest(path, parsed.reason))
        }
    }
}

private val ROOT_FIELDS = listOf("files", "schemaVersion", "transactionId", "type")
private val FILE_FIELDS = listOf("backupPath", "newSha256", "oldSha256", "path", "stagedPath")
