package cz.cleanship.aitools.engine.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = VersionSerializer::class)
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val suffix: String? = null
) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (suffix == other.suffix) return 0
        if (suffix == null) return 1
        if (other.suffix == null) return -1
        return suffix.compareTo(other.suffix)
    }

    override fun toString(): String {
        val base = "$major.$minor.$patch"
        return if (suffix != null) "$base-$suffix" else base
    }

    companion object {
        fun parse(version: String): Version {
            val pattern = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-(.+))?$""")
            val match = pattern.matchEntire(version) ?: throw IllegalArgumentException("Invalid version format: $version. Expected MAJOR.MINOR.PATCH(-SUFFIX)")
            
            val (major, minor, patch, suffix) = match.destructured
            return Version(major.toInt(), minor.toInt(), patch.toInt(), suffix.ifBlank { null })
        }
    }
}

object VersionSerializer : KSerializer<Version> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Version", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Version) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Version {
        return Version.parse(decoder.decodeString())
    }
}
