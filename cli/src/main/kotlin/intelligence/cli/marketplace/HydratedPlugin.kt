package intelligence.cli.marketplace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class HydratedPlugin(
    val skills: Set<String> = emptySet(),
    val agents: Set<String> = emptySet(),
    val hooks: Set<String> = emptySet(),
    val instructions: Set<String> = emptySet(),
    val references: List<HydratedReference> = emptyList(),
) {
    fun paths(kind: PrimitiveKind): List<String> =
        when (kind) {
            PrimitiveKind.Skill -> skills
            PrimitiveKind.Agent -> agents
            PrimitiveKind.Hook -> hooks
            PrimitiveKind.Instruction -> instructions
        }.sorted()

    fun toReferenceJson(): JsonArray =
        JsonArray(
            references
                .sortedWith(compareBy(HydratedReference::type, HydratedReference::sourcePath, HydratedReference::targetPath))
                .map { it.toJson() }
        )
}

internal data class HydratedReference(
    val type: String,
    val name: String?,
    val sourcePath: String,
    val targetPath: String,
    val sha256: String,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("type", type)
            if (!name.isNullOrBlank()) {
                put("name", name)
            }
            put("sourcePath", sourcePath)
            put("targetPath", targetPath)
            put("sha256", sha256)
        }
}

internal data class HydratedPluginBuilder(
    private val skills: MutableSet<String> = linkedSetOf(),
    private val agents: MutableSet<String> = linkedSetOf(),
    private val hooks: MutableSet<String> = linkedSetOf(),
    private val instructions: MutableSet<String> = linkedSetOf(),
    private val references: MutableList<HydratedReference> = mutableListOf(),
) {
    fun addPath(kind: PrimitiveKind, path: String) {
        when (kind) {
            PrimitiveKind.Skill -> skills.add(path)
            PrimitiveKind.Agent -> agents.add(path)
            PrimitiveKind.Hook -> hooks.add(path)
            PrimitiveKind.Instruction -> instructions.add(path)
        }
    }

    fun addReference(reference: HydratedReference) {
        references.add(reference)
    }

    fun build(): HydratedPlugin =
        HydratedPlugin(
            skills = skills.toSet(),
            agents = agents.toSet(),
            hooks = hooks.toSet(),
            instructions = instructions.toSet(),
            references = references.toList(),
        )
}
