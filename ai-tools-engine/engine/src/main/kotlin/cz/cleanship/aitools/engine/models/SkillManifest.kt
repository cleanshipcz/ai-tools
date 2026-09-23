package cz.cleanship.aitools.engine.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A skill: instructions an assistant loads on demand, either declared in this manifest or, for a pointer skill, taken from a plain `SKILL.md` skill in the folder [source] names.
 */
@Serializable
data class SkillManifest(
    override val id: String,
    /**
     * What the skill does, as its generated skill files describe it.
     *
     * For a pointer skill, it is the `description` of the frontmatter of that folder's `SKILL.md`. A skill returned by [cz.cleanship.aitools.engine.services.LoaderService.loadSkill] always has a description that is not blank.
     */
    override val description: String = "", // Optional in YAML only because a pointer skill takes its description from the SKILL.md frontmatter; the loader still rejects a skill without a source that declares none.
    override val metadata: ManifestMetadata,
    val sections: List<SkillSection> = emptyList(),
    /**
     * The companion files copied next to the generated skill file.
     *
     * For a pointer skill, the loader sets it to every file of that folder other than its `SKILL.md`, each copied to the same relative path.
     */
    val files: List<SkillFile> = emptyList(),
    /**
     * The folder holding a plain `SKILL.md` skill whose description, body and companion files this skill deploys, or `null` for a skill declared entirely in this manifest.
     *
     * It may reference the variables of the run, such as `${PROJECTS_FOLDER}`. After substitution, a path that is `~` or starts with `~/` resolves against the home directory of the user running the engine, and any other relative path against the directory of the manifest file. The loader rejects a manifest that declares it together with `description`, `sections` or `files`.
     */
    val source: String? = null,
    /**
     * The text of the `SKILL.md` of [source] after its frontmatter, starting at its first line that is not empty, which the generated skill file holds unchanged, apart from ending in a line break.
     *
     * It is `null` for a skill without a [source] and for a skill whose [source] the loader has not resolved yet.
     */
    @Transient val body: String? = null, // Transient, so only the loader sets it: a manifest declaring a body of its own could contradict its source folder.
) : VersionedManifest

@Serializable(with = SkillSectionSerializer::class)
sealed class SkillSection {
    data class TextSection(val text: String) : SkillSection()

    data class RulesetSection(val ruleset: String) : SkillSection()

    data class FragmentSection(val fragment: String) : SkillSection()
}

@Serializable(with = SkillFileSerializer::class)
data class SkillFile(
    val source: String,
    val target: String,
)

internal object SkillFileSerializer : KSerializer<SkillFile> {

    private val delegateSerializer = MapSerializer(String.serializer(), String.serializer())

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: SkillFile) {
        val map = if (value.source == value.target) {
            mapOf("path" to value.source)
        } else {
            mapOf("source" to value.source, "target" to value.target)
        }
        encoder.encodeSerializableValue(delegateSerializer, map)
    }

    override fun deserialize(decoder: Decoder): SkillFile {
        val map = decoder.decodeSerializableValue(delegateSerializer)
        return when {
            "path" in map -> {
                val path = map.getValue("path")
                SkillFile(source = path, target = path)
            }
            "source" in map && "target" in map -> SkillFile(
                source = map.getValue("source"),
                target = map.getValue("target"),
            )
            else -> throw SerializationException(
                "Expected 'path' or 'source'+'target' keys. Got keys: ${map.keys}",
            )
        }
    }
}

internal object SkillSectionSerializer : KSerializer<SkillSection> {

    private val delegateSerializer = MapSerializer(String.serializer(), String.serializer())

    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: SkillSection) {
        val map = when (value) {
            is SkillSection.TextSection -> mapOf("text" to value.text)
            is SkillSection.RulesetSection -> mapOf("ruleset" to value.ruleset)
            is SkillSection.FragmentSection -> mapOf("fragment" to value.fragment)
        }
        encoder.encodeSerializableValue(delegateSerializer, map)
    }

    override fun deserialize(decoder: Decoder): SkillSection {
        val map = decoder.decodeSerializableValue(delegateSerializer)
        return when {
            "text" in map -> SkillSection.TextSection(
                text = map.getValue("text"),
            )
            "ruleset" in map -> SkillSection.RulesetSection(
                ruleset = map.getValue("ruleset"),
            )
            "fragment" in map -> SkillSection.FragmentSection(
                fragment = map.getValue("fragment"),
            )
            else -> throw SerializationException(
                "Unknown skill section type. Expected one of: text, ruleset, fragment. Got keys: ${map.keys}",
            )
        }
    }
}
