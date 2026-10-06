package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.io.escapedForMessage
import cz.cleanship.aitools.engine.services.MessageWithheldException
import cz.cleanship.aitools.engine.services.jsonOffset
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * A JSON file edited in place: one object or array at a path is changed, and every byte outside what changes is kept, the layout, the escapes and the numbers of the file included.
 *
 * Every edit is parsed again and compared with what it should hold before it is returned. Immutable.
 *
 * @property text the content of the file
 * @property content the content of the file, parsed
 */
internal class JsonDocument private constructor(
    val text: String,
    val content: JsonObject,
    private val root: Container,
    private val file: File,
) {
    private val lineEnding = if (CRLF.findAll(text).count() * 2 > text.count { it == '\n' }) "\r\n" else "\n"
    private val indentUnit = INDENTED_LINE.find(text)?.groupValues?.get(1) ?: DEFAULT_INDENT

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = indentUnit
    }

    /**
     * Returns the text of this file once the object at [path] holds [members] in place of the members [owned] accepts, keeping every other member byte for byte.
     *
     * A member [owned] accepts is replaced in place when [members] holds its key, and removed otherwise; a member of [members] the object does not hold is added after its last member. An object left without members is written `{}` when only whitespace remains inside it. An object of [path] that is missing is created, unless nothing is added, and then the text is returned unchanged.
     *
     * @param owned whether the engine owns the member of a key; it must accept every key of [members]
     * @throws McpConfigFileException naming the file and the key if a value on [path] is not an object, or if the edit would not hold exactly what it should
     */
    fun withMembers(path: List<String>, owned: (String) -> Boolean, members: Map<String, JsonElement>): String =
        edit(path, MemberEdit(owned, members))

    /**
     * Returns the text of this file once the array at [path] holds [elements] in place of the elements [owned] accepts, keeping every other element byte for byte.
     *
     * The elements of [elements] take the place of the first element [owned] accepts, or follow the last element when there is none; every other element [owned] accepts is removed. An array left without elements is written `[]` when only whitespace remains inside it. An array of [path], or an object above it, that is missing is created, unless [elements] is empty, and then the text is returned unchanged.
     *
     * @param owned whether the engine owns an element; it must accept every element of [elements]
     * @throws McpConfigFileException naming the file and the key if a value on [path] is not an object, or its last value not an array, or if the edit would not hold exactly what it should
     */
    fun withElements(path: List<String>, owned: (JsonElement) -> Boolean, elements: List<JsonElement>): String =
        edit(path, ElementEdit(owned, elements))

    private fun edit(path: List<String>, edit: Edit): String {
        val replaced = editIn(root, path, edit, key = null) ?: return text
        val result = text.substring(0, root.start) + replaced + text.substring(root.end)
        verify(result, edit.applyTo(content, path))
        return result
    }

    /**
     * Returns the new text of [container], the value of [key], once [edit] is applied at [path] below it, or `null` when nothing changes.
     */
    private fun editIn(container: Container, path: List<String>, edit: Edit, key: String?): String? {
        // Every container on the path is an object; the last one is what the edit applies to.
        val expectsObject = path.isNotEmpty() || edit.onObject
        if (container.isObject != expectsObject) throw wrongKind(key, expectsObject)
        if (path.isEmpty()) return edit.splice(container)
        val next = path.first()
        val item = container.items.firstOrNull { it.key == next }
        if (item == null) {
            val fresh = edit.fresh(path.drop(1)) ?: return null
            return MemberEdit(owned = { it == next }, members = mapOf(next to fresh)).splice(container)
        }
        val value = item.value as? Container ?: throw wrongKind(next, expectsObject = path.size > 1 || edit.onObject)
        val replaced = editIn(value, path.drop(1), edit, next) ?: return null
        return text.substring(container.start, value.start) + replaced + text.substring(value.end, container.end)
    }

    private fun wrongKind(key: String?, expectsObject: Boolean): McpConfigFileException {
        val what = if (key == null) "is not a JSON object" else "holds a '$key' that is not a JSON ${if (expectsObject) "object" else "array"}"
        return McpConfigFileException("'${file.absolutePath}' $what, so the engine leaves it untouched. Fix the file or remove it, and deploy again.")
    }

    /**
     * Fails unless [result] parses strictly to [expected].
     */
    private fun verify(result: String, expected: JsonObject) {
        if (parseStrictly(result, file) != expected) {
            throw McpConfigFileException(
                "'${file.absolutePath}' would lose or change content the engine does not own if it were rewritten, so the engine leaves it untouched. Report the file layout that caused it.",
            )
        }
    }

    /**
     * One member or element of a container as it stands in the text: what precedes it after the comma or the opening bracket, itself, and what follows it before the next comma; the last one has no trail, since what follows it belongs to the closing of the container.
     */
    private data class Segment(val lead: String, val body: String, val trail: String, val item: Item)

    private fun segments(container: Container): List<Segment> = container.items.mapIndexed { index, item ->
        val leadStart = if (index == 0) container.start + 1 else text.indexOf(',', container.items[index - 1].value.end) + 1
        val trailEnd = if (index == container.items.lastIndex) item.value.end else text.indexOf(',', item.value.end)
        Segment(text.substring(leadStart, item.start), text.substring(item.start, item.value.end), text.substring(item.value.end, trailEnd), item)
    }

    /**
     * Returns the text of [container] holding [pieces] as its members or elements, in order, each written after the lead it carries.
     */
    private fun assemble(container: Container, pieces: List<Pair<String, String>>): String {
        val segments = segments(container)
        val emptyBefore = segments.isEmpty()
        val closing = if (emptyBefore) {
            text.substring(container.start + 1, container.end - 1)
        } else {
            text.substring(
                container.items
                    .last()
                    .value.end,
                container.end - 1,
            )
        }
        val (open, close) = if (container.isObject) '{' to '}' else '[' to ']'
        // The edit removed everything: the whitespace left is what separated the last member from the bracket, which filling an empty `{}` or `[]` added, so dropping it gives an emptied container back its bytes. A container that held nothing keeps its whitespace.
        if (!emptyBefore && pieces.isEmpty() && closing.isBlank()) return "$open$close"
        // A container that held nothing gets its members on lines of their own, closed on the line of its opening, when it held no line break.
        val closed = if (emptyBefore && pieces.isNotEmpty() && '\n' !in closing) lineEnding + indentOf(container.start) else closing
        return open + pieces.joinToString(",") { (lead, body) -> lead + body } + closed + close
    }

    /**
     * Returns the lead of a member or element added after [segments]: the lead of the last one when it follows a comma or breaks the line; a space after the only one, whose lead follows the opening bracket instead; or, in an empty container opened at [start], a line break and the indentation one level below the line it opens on.
     */
    private fun newLead(segments: List<Segment>, start: Int): String {
        val last = segments.lastOrNull() ?: return lineEnding + indentOf(start) + indentUnit
        return if (segments.size > 1 || '\n' in last.lead) last.lead else " "
    }

    /** Returns the whitespace the line holding the position [position] starts with. */
    private fun indentOf(position: Int): String {
        val lineStart = text.lastIndexOf('\n', position - 1) + 1
        return text.substring(lineStart).takeWhile { it == ' ' || it == '\t' }
    }

    /**
     * Returns [value] as the text of a member or element written after [lead]: over several lines indented like the lead when the lead breaks the line, on one line otherwise.
     */
    private fun render(value: JsonElement, lead: String): String {
        if ('\n' !in lead) return Json.encodeToString(JsonElement.serializer(), value)
        val indent = lead.substringAfterLast('\n')
        return pretty.encodeToString(JsonElement.serializer(), value).replace("\n", lineEnding + indent)
    }

    private sealed interface Edit {
        /** Whether this edit applies to an object rather than an array. */
        val onObject: Boolean

        /** Returns the value to create at the missing [rest] of the path below a missing key, or `null` when this edit adds nothing. */
        fun fresh(rest: List<String>): JsonElement?

        /** Returns the new text of [container], the value this edit applies to. */
        fun Container.spliced(): String

        /** Returns [content] with this edit applied at [path], as the result of the edit must parse to. */
        fun applyTo(content: JsonObject, path: List<String>): JsonObject
    }

    private fun Edit.splice(container: Container): String = with(this) { container.spliced() }

    private inner class MemberEdit(private val owned: (String) -> Boolean, private val members: Map<String, JsonElement>) : Edit {
        override val onObject = true

        override fun fresh(rest: List<String>): JsonElement? = if (members.isEmpty()) null else nested(rest, JsonObject(members))

        override fun Container.spliced(): String {
            val segments = segments(this)
            val pieces = mutableListOf<Pair<String, String>>()
            segments.forEachIndexed { index, segment ->
                val key = requireNotNull(segment.item.key)
                val trail = segment.trail.takeIf { index < segments.lastIndex }.orEmpty()
                when {
                    !owned(key) -> pieces += segment.lead to segment.body + trail
                    key in members -> pieces += segment.lead to member(key, members.getValue(key), segment.lead) + trail
                }
            }
            val present = segments.mapNotNullTo(mutableSetOf()) { it.item.key }
            val lead = newLead(segments, start)
            members.filterKeys { it !in present }.forEach { (key, value) -> pieces += lead to member(key, value, lead) }
            return assemble(this, pieces)
        }

        private fun member(key: String, value: JsonElement, lead: String) = JsonPrimitive(key).toString() + ": " + render(value, lead)

        override fun applyTo(content: JsonObject, path: List<String>): JsonObject = at(content, path) { current ->
            val existing = (current as? JsonObject).orEmpty()
            if (current == null && members.isEmpty()) null else JsonObject(existing.filterKeys { !owned(it) } + members)
        }
    }

    private inner class ElementEdit(private val owned: (JsonElement) -> Boolean, private val elements: List<JsonElement>) : Edit {
        override val onObject = false

        override fun fresh(rest: List<String>): JsonElement? = if (elements.isEmpty()) null else nested(rest, JsonArray(elements))

        override fun Container.spliced(): String {
            val segments = segments(this)
            val pieces = mutableListOf<Pair<String, String>>()
            var placed = false
            segments.forEachIndexed { index, segment ->
                val trail = segment.trail.takeIf { index < segments.lastIndex }.orEmpty()
                when {
                    !owned(Json.parseToJsonElement(segment.body)) -> pieces += segment.lead to segment.body + trail
                    !placed -> {
                        elements.forEach { pieces += segment.lead to render(it, segment.lead) }
                        placed = true
                    }
                }
            }
            if (!placed) {
                val lead = newLead(segments, start)
                elements.forEach { pieces += lead to render(it, lead) }
            }
            return assemble(this, pieces)
        }

        override fun applyTo(content: JsonObject, path: List<String>): JsonObject = at(content, path) { current ->
            val existing = (current as? JsonArray).orEmpty()
            if (current == null && elements.isEmpty()) {
                null
            } else {
                val first = existing.indexOfFirst(owned).takeIf { it >= 0 } ?: existing.size
                JsonArray(existing.take(first).filterNot(owned) + elements + existing.drop(first).filterNot(owned))
            }
        }
    }

    companion object {
        /** The deepest a file may nest its objects and arrays, the top object counted as the first level. */
        const val MAX_DEPTH = 512

        private const val DEFAULT_INDENT = "  "
        private val CRLF = Regex("\r\n")
        private val INDENTED_LINE = Regex("\n([ \t]+)\\S")

        /**
         * Returns the document of [text], read as an empty object when it is `null` or blank.
         *
         * @param file the file [text] was read from, named in a failure
         * @throws McpConfigFileException naming [file], and never quoting [text], for every refusal of [parseStrictly]
         */
        fun parse(text: String?, file: File): JsonDocument {
            val source = if (text.isNullOrBlank()) "{}\n" else text
            val content = parseStrictly(source, file)
            val root = Scanner(source).document()
            return JsonDocument(source, content, root, file)
        }

        /**
         * Returns [text] parsed strictly as a JSON object.
         *
         * @throws McpConfigFileException naming [file], and never quoting [text], if it nests its values deeper than [MAX_DEPTH] levels, is not strictly valid JSON - a comment, a trailing comma, an unquoted value other than a number, `true`, `false` or `null`, and a control character inside a string all count as invalid - declares a key twice in one object, or is not an object
         */
        fun parseStrictly(text: String, file: File): JsonObject {
            val element = parsed(text, file)
            // Only the position is named: the string around the character may hold a token.
            val control = controlCharacterInString(text)
            val duplicate = duplicateKey(text)
            val problem = when {
                control != null ->
                    "is not valid JSON at offset $control: a string holds a control character that JSON allows only escaped, so the engine leaves it untouched rather than rewrite it. Fix the file or remove it, and deploy again."
                !element.isConformant() ->
                    "is not valid JSON: it holds a value that is neither quoted nor a number, true, false or null, so the engine leaves it untouched rather than rewrite it. Fix the file or remove it, and deploy again."
                duplicate != null -> "declares the key '${duplicate.escapedForMessage()}' twice in one object, so the engine leaves it untouched rather than rewrite it without one of them. Remove one, and deploy again."
                element !is JsonObject -> "is not a JSON object, so the engine leaves it untouched. Fix the file or remove it, and deploy again."
                else -> return element
            }
            throw McpConfigFileException("'${file.absolutePath}' $problem")
        }

        /**
         * Returns [text] parsed as JSON by the parser of the engine, which accepts more than JSON allows.
         *
         * @throws McpConfigFileException naming [file], and never quoting [text], if it nests its values deeper than [MAX_DEPTH] levels or the parser refuses it
         */
        private fun parsed(text: String, file: File): JsonElement {
            // Every reader of the text recurses once per level, the parser included, so a file of a few kilobytes of brackets would otherwise end the run with a StackOverflowError instead of failing this file.
            nestingBeyond(text, MAX_DEPTH)?.let { offset ->
                throw McpConfigFileException(
                    "'${file.absolutePath}' nests its values deeper than $MAX_DEPTH levels at offset $offset, so the engine leaves it untouched rather than read it. Fix the file or remove it, and deploy again.",
                )
            }
            return try {
                Json.parseToJsonElement(text)
            } catch (ex: SerializationException) {
                // The message of the parser quotes the file around the error, which may hold a token, so only the offset it names is repeated.
                throw McpConfigFileException(
                    "'${file.absolutePath}' is not valid JSON${ex.jsonOffset()}, so the engine leaves it untouched rather than rewrite it. " +
                        "A comment or a trailing comma counts as invalid too, because rewriting the file would drop it. Fix the file or remove it, and deploy again.",
                    MessageWithheldException(ex),
                )
            }
        }
    }
}

/**
 * Returns whether every unquoted value of this element is a number, `true`, `false` or `null`, as JSON requires; the parser of the engine reads any unquoted word as a value.
 */
private fun JsonElement.isConformant(): Boolean = when (this) {
    is JsonObject -> values.all { it.isConformant() }
    is JsonArray -> all { it.isConformant() }
    is JsonPrimitive -> isString || this == JsonNull || content == "true" || content == "false" || JSON_NUMBER.matches(content)
}

private val JSON_NUMBER = Regex("""-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?""")

/**
 * Returns [leaf] nested in one object per key of [path], outermost first.
 */
private fun nested(
    path: List<String>,
    leaf: JsonElement,
): JsonElement = path.foldRight(leaf) { key, inner -> JsonObject(mapOf(key to inner)) }

/**
 * Returns [content] with the value at [path] replaced by what [change] returns for the value there, or `null` when it is missing; a `null` result leaves [content] as it is.
 */
private fun at(content: JsonObject, path: List<String>, change: (JsonElement?) -> JsonElement?): JsonObject {
    if (path.isEmpty()) return change(content) as? JsonObject ?: content
    val key = path.first()
    val current = content[key]
    val changed = when {
        path.size == 1 -> change(current)
        current == null -> at(JsonObject(emptyMap()), path.drop(1), change).takeIf { it.isNotEmpty() }
        // A value of another kind fails the edit of the text before its result is compared with this.
        current !is JsonObject -> null
        else -> at(current, path.drop(1), change)
    }
    return if (changed == null) content else JsonObject(content + (key to changed))
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

/** A value of the text: [start] is its first character, [end] the one after its last. */
private sealed interface Node {
    val start: Int
    val end: Int
}

private data class Scalar(override val start: Int, override val end: Int) : Node

private data class Container(override val start: Int, override val end: Int, val isObject: Boolean, val items: List<Item>) : Node

/**
 * A member of an object, whose [start] is the start of its key, or an element of an array, whose [start] is the start of its value; its [key] is `null` in an array.
 */
private data class Item(val key: String?, val start: Int, val value: Node)

/**
 * Finds where every value of a text that already parsed as JSON starts and ends.
 */
private class Scanner(private val text: String) {
    private var index = 0

    fun document(): Container = value() as Container

    private fun value(): Node {
        skipWhitespace()
        return when (text[index]) {
            '{' -> container(isObject = true, close = '}')
            '[' -> container(isObject = false, close = ']')
            '"' -> {
                val start = index
                index = stringEnd(start) + 1
                Scalar(start, index)
            }
            else -> {
                val start = index
                while (index < text.length && text[index] !in SCALAR_END) index++
                Scalar(start, index)
            }
        }
    }

    private fun container(isObject: Boolean, close: Char): Container {
        val start = index++
        val items = mutableListOf<Item>()
        skipWhitespace()
        if (text[index] == close) return Container(start, ++index, isObject, items)
        while (true) {
            skipWhitespace()
            val itemStart = index
            val key = if (isObject) {
                val keyEnd = stringEnd(index)
                Json.parseToJsonElement(text.substring(index, keyEnd + 1)).jsonPrimitive.content.also {
                    index = keyEnd + 1
                    skipWhitespace()
                    index++
                }
            } else {
                null
            }
            items += Item(key, itemStart, value())
            skipWhitespace()
            if (text[index++] == close) return Container(start, index, isObject, items)
        }
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    private fun stringEnd(start: Int): Int {
        var position = start + 1
        while (text[position] != '"') position += if (text[position] == '\\') 2 else 1
        return position
    }

    companion object {
        private val SCALAR_END = setOf(',', '}', ']', ' ', '\t', '\n', '\r')
    }
}

/**
 * Returns the first key [json], which parses as JSON, declares twice in one object, or `null` when it declares none twice; the parser keeps only the last of them, silently.
 */
internal fun duplicateKey(json: String): String? {
    // One set of keys per object being read, and null for an array, innermost last.
    val open = ArrayDeque<MutableSet<String>?>()
    var index = 0
    while (index < json.length) {
        when (json[index]) {
            '{' -> open.addLast(mutableSetOf())
            '[' -> open.addLast(null)
            '}', ']' -> open.removeLast()
            '"' -> {
                val end = stringEnd(json, index)
                if (isKey(json, end) && open.lastOrNull()?.add(keyText(json, index, end)) == false) return keyText(json, index, end)
                index = end
            }
        }
        index++
    }
    return null
}

/**
 * Returns the offset of the first character below U+0020 that [json], which parses as JSON, holds inside a string, or `null` when it holds none; RFC 8259 allows such a character in a string only escaped, and the parser of the engine accepts it raw.
 */
private fun controlCharacterInString(json: String): Int? {
    var index = 0
    while (index < json.length) {
        if (json[index] == '"') {
            val end = stringEnd(json, index)
            (index + 1 until end).firstOrNull { json[it] < ' ' }?.let { return it }
            index = end
        }
        index++
    }
    return null
}

/**
 * Returns whether the string closing at [end] is followed by a colon, which makes it a key.
 */
private fun isKey(json: String, end: Int): Boolean {
    var next = end + 1
    while (next < json.length && json[next].isWhitespace()) next++
    return next < json.length && json[next] == ':'
}

private fun keyText(json: String, start: Int, end: Int): String = Json.parseToJsonElement(json.substring(start, end + 1)).jsonPrimitive.content

/**
 * Returns the index of the quote closing the string that opens at [start].
 */
private fun stringEnd(json: String, start: Int): Int {
    var index = start + 1
    while (json[index] != '"') index += if (json[index] == '\\') 2 else 1
    return index
}
