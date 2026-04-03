package cz.cleanship.aitools.engine.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class SkillManifest(
    override val id: String,
    override val description: String,
    override val metadata: ManifestMetadata,
    val sections: List<SkillSection> = emptyList(),
    val files: List<SkillFile> = emptyList(),
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
