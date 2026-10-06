package cz.cleanship.aitools.engine.tools.mcp

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class McpFingerprintTest {

    @Test
    fun `should give sha256 followed by 64 lowercase hexadecimal digits`() {
        // when
        val fingerprint = Json.parseToJsonElement("""{"command": "npx"}""").fingerprint()

        // then
        assertThat(fingerprint).matches("sha256:[0-9a-f]{64}")
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            // - another layout
            """{"command":"npx","args":["-y"]} | {  "command" :   "npx" ,	"args" : [ "-y" ]  }""",
            // - the keys of an object in another order
            """{"command":"npx","args":["-y"]} | {"args":["-y"],"command":"npx"}""",
            // - an escape for a character written as it is
            """{"command":"caf\u00e9"} | {"command":"café"}""",
        ],
    )
    fun `should give the same fingerprint for every text of the same content`(
        first: String,
        second: String,
    ) {
        // when
        val fingerprints = listOf(first, second).map { Json.parseToJsonElement(it).fingerprint() }

        // then
        assertThat(fingerprints[0]).isEqualTo(fingerprints[1])
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            // - another value
            """{"command":"npx"} | {"command":"uvx"}""",
            // - an array in another order
            """{"args":["a","b"]} | {"args":["b","a"]}""",
            // - a number where a text was
            """{"port":"1"} | {"port":1}""",
            // - a number written another way is fingerprinted as it is written
            """{"port":1000} | {"port":1e3}""",
            """{"port":1000} | {"port":1000.0}""",
        ],
    )
    fun `should give another fingerprint for any other content, and for a number written another way`(
        first: String,
        second: String,
    ) {
        // when
        val fingerprints = listOf(first, second).map { Json.parseToJsonElement(it).fingerprint() }

        // then
        assertThat(fingerprints[0]).isNotEqualTo(fingerprints[1])
    }
}
