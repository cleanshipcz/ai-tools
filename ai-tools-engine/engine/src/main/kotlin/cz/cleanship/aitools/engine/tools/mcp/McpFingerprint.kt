package cz.cleanship.aitools.engine.tools.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 * Returns the fingerprint of this entry of a config file, `sha256:` followed by 64 lowercase hexadecimal digits: the same for every text that parses to the same content, whatever its layout, its escapes and the order of the keys of its objects, and another one for any other content.
 *
 * A JSON number is fingerprinted as it is written, so `1000`, `1e3` and `1000.0` give three fingerprints.
 */
internal fun JsonElement.fingerprint(): String {
    // A tool may write its file again in a layout of its own, as Claude Code does with ~/.claude.json; a digest of the bytes would take every entry it did not change for one changed by hand.
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical().toByteArray(Charsets.UTF_8))
    return FINGERPRINT_PREFIX + digest.joinToString("") { "%02x".format(it) }
}

/** The start of every fingerprint of [fingerprint]. */
internal const val FINGERPRINT_PREFIX = "sha256:"

/**
 * Returns this element as one line of JSON whose object keys are sorted, so two elements with the same content give the same text.
 */
private fun JsonElement.canonical(): String = when (this) {
    is JsonObject -> entries.sortedBy { it.key }.joinToString(",", "{", "}") { (key, value) -> JsonPrimitive(key).toString() + ":" + value.canonical() }
    is JsonArray -> joinToString(",", "[", "]") { it.canonical() }
    is JsonPrimitive -> toString()
}
