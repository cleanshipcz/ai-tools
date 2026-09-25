package cz.cleanship.aitools.engine.tools.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

/**
 * The editor that changes the members of one JSON object, or the elements of one array, and keeps every other byte of the file as it was.
 */
class JsonDocumentTest {

    private val file = File("/home/user/.claude.json")

    @Nested
    inner class ObjectMembers {

        @Test
        fun `should replace an owned member in place and keep the bytes of every other member, whatever their layout`() {
            // given
            // - uneven spacing, a unicode escape, an escaped slash and a big number, which a rewrite would normalize
            val existing = "{\"x\" :{\"k\":1} ,  \"b\": 2,\"y\": \"caf\\u00e9 \\/ 12345678901234567890\"}"

            // when
            val edited = JsonDocument.parse(existing, file).withMembers(emptyList(), owned = { it == "b" }, members = mapOf("b" to JsonPrimitive(5)))

            // then
            assertThat(edited).isEqualTo("{\"x\" :{\"k\":1} ,  \"b\": 5,\"y\": \"caf\\u00e9 \\/ 12345678901234567890\"}")
        }

        @ParameterizedTest
        @CsvSource(
            // - the first member
            "a, '{\n  \"b\": 2,\n  \"c\": 3\n}'",
            // - a member in the middle
            "b, '{\n  \"a\": 1,\n  \"c\": 3\n}'",
            // - the last member
            "c, '{\n  \"a\": 1,\n  \"b\": 2\n}'",
        )
        fun `should remove an owned member together with its separator`(removed: String, expected: String) {
            // given
            val existing = "{\n  \"a\": 1,\n  \"b\": 2,\n  \"c\": 3\n}"

            // when
            val edited = JsonDocument.parse(existing, file).withMembers(emptyList(), owned = { it == removed }, members = emptyMap())

            // then
            assertThat(edited).isEqualTo(expected)
        }

        @Test
        fun `should append a new member after the last one, indented like it, and give the original bytes back once it is removed`() {
            // given
            val existing = "{\n  \"mcpServers\": {\n    \"mine\": {\n      \"command\": \"npx\"\n    }\n  },\n  \"theme\": \"dark\"\n}\n"
            val document = JsonDocument.parse(existing, file)

            // when
            val added = document.withMembers(listOf("mcpServers"), owned = { it == "github" }, members = mapOf("github" to entry("url", "https://x")))
            val removed = JsonDocument.parse(added, file).withMembers(listOf("mcpServers"), owned = { it == "github" }, members = emptyMap())

            // then
            assertThat(added).isEqualTo(
                "{\n  \"mcpServers\": {\n    \"mine\": {\n      \"command\": \"npx\"\n    },\n    \"github\": {\n      \"url\": \"https://x\"\n    }\n  },\n  \"theme\": \"dark\"\n}\n",
            )
            assertThat(removed).isEqualTo(existing)
        }

        @Test
        fun `should fill an empty object on lines of its own, indented one level below the line it opens on`() {
            // given
            val existing = "{\n  \"mcpServers\": {},\n  \"theme\": \"dark\"\n}\n"

            // when
            val edited = JsonDocument.parse(existing, file).withMembers(listOf("mcpServers"), owned = { it == "github" }, members = mapOf("github" to entry("url", "https://x")))

            // then
            assertThat(edited).isEqualTo("{\n  \"mcpServers\": {\n    \"github\": {\n      \"url\": \"https://x\"\n    }\n  },\n  \"theme\": \"dark\"\n}\n")
        }

        @Test
        fun `should create the object a path names when it is missing, after the last member of its parent`() {
            // given
            val existing = "{\n    \"theme\": \"dark\"\n}"

            // when
            val edited = JsonDocument.parse(existing, file).withMembers(listOf("mcpServers"), owned = { it == "github" }, members = mapOf("github" to entry("url", "https://x")))

            // then
            // - the indentation of four spaces the file uses is kept for the lines the editor adds
            assertThat(edited).isEqualTo("{\n    \"theme\": \"dark\",\n    \"mcpServers\": {\n        \"github\": {\n            \"url\": \"https://x\"\n        }\n    }\n}")
        }

        @Test
        fun `should leave the file as it is when a path is missing and nothing is added`() {
            // given
            val existing = "{ \"theme\": \"dark\" }"

            // when
            val edited = JsonDocument.parse(existing, file).withMembers(listOf("mcpServers"), owned = { true }, members = emptyMap())

            // then
            assertThat(edited).isEqualTo(existing)
        }

        @Test
        fun `should end the lines it adds the way the lines of the file end`() {
            // given
            val existing = "{\r\n  \"mcpServers\": {\r\n    \"mine\": {}\r\n  }\r\n}\r\n"

            // when
            val edited = JsonDocument.parse(existing, file).withMembers(listOf("mcpServers"), owned = { it == "github" }, members = mapOf("github" to entry("url", "https://x")))

            // then
            assertThat(edited).isEqualTo("{\r\n  \"mcpServers\": {\r\n    \"mine\": {},\r\n    \"github\": {\r\n      \"url\": \"https://x\"\r\n    }\r\n  }\r\n}\r\n")
        }

        @ParameterizedTest
        @CsvSource(
            // - an empty object among other members
            "'{\n  \"mcpServers\": {},\n  \"theme\": \"dark\"\n}\n'",
            // - an empty object as the only member, on one line
            "'{\"mcpServers\": {}}'",
        )
        fun `should give the bytes of an empty object back once the member added to it is removed`(existing: String) {
            // given
            val document = JsonDocument.parse(existing, file)

            // when
            val added = document.withMembers(listOf("mcpServers"), owned = { it == "github" }, members = mapOf("github" to entry("url", "https://x")))
            val removed = JsonDocument.parse(added, file).withMembers(listOf("mcpServers"), owned = { it == "github" }, members = emptyMap())

            // then
            assertThat(added).contains("\"github\"")
            assertThat(removed).isEqualTo(existing)
        }

        @Test
        fun `should keep the whitespace inside an empty object the edit neither adds to nor removes from`() {
            // given
            val existing = "{\"mcpServers\": { }, \"theme\": \"dark\"}"

            // when
            val edited = JsonDocument.parse(existing, file).withMembers(listOf("mcpServers"), owned = { it == "github" }, members = emptyMap())

            // then
            assertThat(edited).isEqualTo(existing)
        }

        @ParameterizedTest
        @CsvSource(
            // - no file at all
            "true",
            // - an empty object
            "false",
        )
        fun `should create nothing when the edit adds nothing to a missing object or array`(missingFile: Boolean) {
            // given
            val existing = if (missingFile) null else "{}"

            // when
            val members = JsonDocument.parse(existing, file).withMembers(listOf("mcpServers"), owned = { true }, members = emptyMap())
            val elements = JsonDocument.parse(existing, file).withElements(listOf("permissions", "allow"), owned = { true }, elements = emptyList())

            // then
            assertThat(listOf(members, elements)).allSatisfy { assertThat(it).isEqualTo(existing ?: "{}\n") }
        }

        @Test
        fun `should read a missing or blank file as an empty object`() {
            // when
            val edited = JsonDocument.parse(null, file).withMembers(listOf("mcpServers"), owned = { it == "github" }, members = mapOf("github" to entry("url", "https://x")))

            // then
            assertThat(edited).isEqualTo("{\n  \"mcpServers\": {\n    \"github\": {\n      \"url\": \"https://x\"\n    }\n  }\n}\n")
        }
    }

    @Nested
    inner class ArrayElements {

        private val mine: (JsonElement) -> Boolean = { (it as? JsonPrimitive)?.content?.startsWith("mcp__github__") == true }

        @Test
        fun `should put the new elements where the first owned one stood and drop the other owned ones`() {
            // given
            val existing = "{\"permissions\": {\"allow\": [\"Bash(ls)\", \"mcp__github__old\", \"Read\", \"mcp__github__older\"]}}"

            // when
            val edited = JsonDocument.parse(existing, file).withElements(listOf("permissions", "allow"), owned = mine, elements = listOf(JsonPrimitive("mcp__github__get_me")))

            // then
            assertThat(edited).isEqualTo("{\"permissions\": {\"allow\": [\"Bash(ls)\", \"mcp__github__get_me\", \"Read\"]}}")
        }

        @Test
        fun `should remove every owned element and keep the foreign ones byte for byte`() {
            // given
            val existing = "{\n  \"permissions\": {\n    \"allow\": [\n      \"mcp__github__get_me\",\n      \"Bash(git \\u0073tatus)\"\n    ]\n  }\n}\n"

            // when
            val edited = JsonDocument.parse(existing, file).withElements(listOf("permissions", "allow"), owned = mine, elements = emptyList())

            // then
            assertThat(edited).isEqualTo("{\n  \"permissions\": {\n    \"allow\": [\n      \"Bash(git \\u0073tatus)\"\n    ]\n  }\n}\n")
        }

        @ParameterizedTest
        @CsvSource(
            // - an empty array among other members
            "'{\n  \"permissions\": {\n    \"allow\": [],\n    \"defaultMode\": \"plan\"\n  }\n}\n', allow",
            // - an empty array on one line
            "'{\"permissions\": {\"deny\": []}}', deny",
        )
        fun `should give the bytes of an empty array back once the elements added to it are removed`(
            existing: String,
            list: String,
        ) {
            // when
            val added = JsonDocument.parse(existing, file).withElements(listOf("permissions", list), owned = mine, elements = listOf(JsonPrimitive("mcp__github__x")))
            val removed = JsonDocument.parse(added, file).withElements(listOf("permissions", list), owned = mine, elements = emptyList())

            // then
            assertThat(added).contains("mcp__github__x")
            assertThat(removed).isEqualTo(existing)
        }

        @Test
        fun `should create the objects and the array a path names when they are missing`() {
            // given
            val existing = "{\n  \"model\": \"opus\"\n}\n"

            // when
            val edited = JsonDocument.parse(existing, file).withElements(listOf("permissions", "deny"), owned = mine, elements = listOf(JsonPrimitive("mcp__github__delete_repository")))

            // then
            assertThat(edited).isEqualTo("{\n  \"model\": \"opus\",\n  \"permissions\": {\n    \"deny\": [\n      \"mcp__github__delete_repository\"\n    ]\n  }\n}\n")
        }

        @Test
        fun `should append after the last element when the array holds no owned one`() {
            // given
            val existing = "{\"permissions\": {\"deny\": [\"WebFetch\"]}}"

            // when
            val edited = JsonDocument.parse(existing, file).withElements(listOf("permissions", "deny"), owned = mine, elements = listOf(JsonPrimitive("mcp__github__x"), JsonPrimitive("mcp__github__y")))

            // then
            assertThat(edited).isEqualTo("{\"permissions\": {\"deny\": [\"WebFetch\", \"mcp__github__x\", \"mcp__github__y\"]}}")
        }
    }

    @Nested
    inner class Refusals {

        @ParameterizedTest
        @CsvSource(
            // - a comment, which the edit would have to drop or keep in a file no strict reader accepts
            "'{ // mine\n \"a\": 1 }', not valid JSON",
            // - a trailing comma
            "'{ \"a\": 1, }', not valid JSON",
            // - an unquoted word, which the parser of the engine reads as a literal and every other reader refuses
            "'{ \"a\": mine }', not valid JSON",
            // - a number no reader accepts
            "'{ \"a\": [01] }', not valid JSON",
            // - an array at the top
            "'[1]', not a JSON object",
            // - a key declared twice
            "'{ \"a\": 1, \"a\": 2 }', '''a'' twice'",
            // - a line break inside a string, which JSON allows only escaped
            "'{ \"a\": \"mine\nmine\" }', not valid JSON at offset 12",
            // - a tab inside a string
            "'{ \"a\": \"mine\tmine\" }', not valid JSON at offset 12",
            // - a NUL character inside a key
            "'{ \"mi\u0000ne\": 1 }', not valid JSON at offset 5",
        )
        fun `should refuse a file it cannot edit without losing content, naming the file and never quoting it`(
            existing: String,
            expected: String,
        ) {
            // when / then
            assertThatThrownBy { JsonDocument.parse(existing, file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining(expected)
                .hasMessageNotContaining("mine")
        }

        @Test
        fun `should name a key declared twice with its line breaks and control characters escaped, so it can never forge a line of its own`() {
            // given
            // - a key that decodes to a line break followed by a fake log line and an escape sequence
            val existing = "{\"projects\": {\"a\\nERROR fake\\u001b[2J\": 1, \"a\\nERROR fake\\u001b[2J\": 2}}"

            // when / then
            assertThatThrownBy { JsonDocument.parse(existing, file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining("'a\\u000AERROR fake\\u001B[2J' twice")
                .hasMessageNotContaining("\n")
                .hasMessageNotContaining("\u001b")
        }

        @ParameterizedTest
        @CsvSource("[, ]", "'{\"a\": ', }")
        fun `should refuse a file nested deeper than the limit, naming the file and the limit, instead of overflowing the stack`(
            open: String,
            close: String,
        ) {
            // given
            // - 10 000 levels of arrays or of objects below the top object, a file of a few kilobytes
            val existing = "{\"deep\": " + open.repeat(DEEP) + "1" + close.repeat(DEEP) + "}"

            // when / then
            assertThatThrownBy { JsonDocument.parse(existing, file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining("512")
        }

        @Test
        fun `should read a file nested exactly as deep as the limit`() {
            // given
            // - the top object and 511 arrays inside it
            val existing = "{\"deep\": " + "[".repeat(LIMIT - 1) + "]".repeat(LIMIT - 1) + "}"

            // when
            val document = JsonDocument.parse(existing, file)

            // then
            assertThat(document.text).isEqualTo(existing)
        }

        @Test
        fun `should refuse a file nested one level deeper than the limit`() {
            // given
            val existing = "{\"deep\": " + "[".repeat(LIMIT) + "]".repeat(LIMIT) + "}"

            // when / then
            assertThatThrownBy { JsonDocument.parse(existing, file) }
                .isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining("512")
        }

        @Test
        fun `should count no bracket inside a string towards the depth`() {
            // given
            val existing = "{\"deep\": \"" + "[{".repeat(DEEP) + "\"}"

            // when
            val document = JsonDocument.parse(existing, file)

            // then
            assertThat(document.content.keys).containsExactly("deep")
        }

        @ParameterizedTest
        @CsvSource(
            // - a path through a value that is not an object
            "'{\"permissions\": []}', 'permissions', members",
            // - an array expected where an object is
            "'{\"permissions\": {\"allow\": {}}}', 'allow', elements",
        )
        fun `should refuse a path through a value of another kind, naming the file and the key`(
            existing: String,
            key: String,
            kind: String,
        ) {
            // given
            val document = JsonDocument.parse(existing, file)

            // when / then
            assertThatThrownBy {
                if (kind == "members") {
                    document.withMembers(listOf("permissions", "x"), owned = { true }, members = mapOf("y" to JsonPrimitive(1)))
                } else {
                    document.withElements(listOf("permissions", "allow"), owned = { true }, elements = listOf(JsonPrimitive("z")))
                }
            }.isInstanceOf(McpConfigFileException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining(key)
        }
    }

    private fun entry(key: String, value: String): JsonElement = buildJsonObject { put(key, value) }

    private companion object {
        const val LIMIT = 512
        const val DEEP = 10_000
    }
}
