package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.io.escapedForMessage
import cz.cleanship.aitools.engine.io.holdsControlCharacter
import cz.cleanship.aitools.engine.services.ExportService
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * The record of the MCP entries the engine wrote into the files of one deploy target, kept at [RELATIVE_PATH] below a project directory or the home, so a later deploy can remove an entry its deployment no longer selects, and only while that entry still holds what the engine wrote.
 *
 * Its file is the JSON object `{"version": 2, "files": {"<path>": {"<entry>": "sha256:<hex>", ...}}}`: each path is relative to the target, separated by `/`, and never leaves it, and each entry is named as its file names it - a server id, or a permission entry such as `deny:mcp__github__delete_repository` - with the fingerprint of what the engine wrote there, or the sorted list of several such fingerprints, never a value or a command. It is read once by [load] and written by [record]. Not thread-safe.
 */
class McpLedger private constructor(
    private val managed: ManagedConfigFile,
    private val exportService: ExportService,
    private var written: String?,
    private val records: MutableMap<String, Map<String, Set<String>>>,
) {

    /** The file of this ledger. */
    val file: File get() = managed.file

    /**
     * Returns every fingerprint recorded for each entry the engine wrote into the file at [path], relative to the target and separated by `/`, by entry name; empty when it recorded none. An entry has several fingerprints while an edit of its file is pending, or when the ledger could not be written once that edit was committed.
     */
    fun recorded(path: String): Map<String, Set<String>> = records[path].orEmpty()

    /**
     * Records that the file at [path], relative to the target and separated by `/`, holds exactly the entries [entries] of the engine, each with one of the fingerprints recorded for it, and writes the ledger through the [ExportService] it was loaded with when that changes the record, so a dry run keeps the record for the files after it and writes nothing. A file with no entry is dropped from the record, and a ledger left without any file is deleted, together with its directory when that is left empty; a directory that cannot be listed or deleted is kept and logged as a warning.
     *
     * @throws McpConfigFileException naming [file] if it cannot be written or deleted, or changed since it was read
     */
    fun record(path: String, entries: Map<String, Set<String>>) {
        if (records[path].orEmpty() == entries) return
        if (entries.isEmpty()) records.remove(path) else records[path] = entries.mapValues { (_, fingerprints) -> fingerprints.toSortedSet() }.toSortedMap()
        val target = managed.target()
        val previous = written
        if (records.isEmpty()) {
            if (previous != null) {
                managed.delete(exportService, target, describedBy = "the MCP ledger", previous = previous)
                removeEmptyDirectory()
            }
            written = null
            return
        }
        val content = render()
        managed.write(exportService, target, content, describedBy = "the MCP ledger ${described()}", previous = previous)
        written = content
    }

    // Names every file and entry, never a fingerprint, which says nothing to a reader.
    private fun described(): String =
        records.toSortedMap().entries.joinToString(", ", "{", "}") { (path, entries) -> path.escapedForMessage() + "=" + entries.keys.sorted().joinToString(", ", "[", "]") { it.escapedForMessage() } }

    private fun render(): String = PRETTY.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put(VERSION, CURRENT_VERSION)
            putJsonObject(FILES) {
                records.toSortedMap().forEach { (path, entries) -> putJsonObject(path) { entries.toSortedMap().forEach { (name, fingerprints) -> put(name, rendered(fingerprints)) } } }
            }
        },
    ) + "\n"

    // One fingerprint stays a text, the form every ledger holds between two edits.
    private fun rendered(fingerprints: Set<String>): JsonElement =
        fingerprints.singleOrNull()?.let(::JsonPrimitive) ?: JsonArray(fingerprints.sorted().map(::JsonPrimitive))

    // A directory of the engine's own that is left empty is removed with the ledger; a link, or a directory holding anything else, stays. A dry run deleted nothing, so the directory is not empty then.
    // Removing it is tidying up after a ledger that is already deleted: a directory that cannot be listed, or that gained an entry in the meantime, changes nothing the engine owns, so it is kept with a warning rather than failing the export that deleted the ledger.
    private fun removeEmptyDirectory() {
        val directory = file.parentFile.toPath()
        try {
            if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && Files.list(directory).use { it.findAny().isEmpty }) {
                Files.deleteIfExists(directory)
            }
        } catch (ex: IOException) {
            LOG.warn("Kept the directory '{}' of the deleted MCP ledger, which could not be removed ({}). Remove it by hand once it is empty.", directory, ex.javaClass.simpleName)
        }
    }

    companion object {
        /** The path of the ledger relative to its target. */
        const val RELATIVE_PATH = ".ai-tools/mcp-ledger.json"

        private val LOG = LoggerFactory.getLogger(McpLedger::class.java)

        private const val VERSION = "version"
        private const val FILES = "files"
        private const val CURRENT_VERSION = 2
        private const val FIRST_VERSION = 1

        @OptIn(ExperimentalSerializationApi::class)
        private val PRETTY = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        private val ENTRY = Regex("""[^/\\]+""")
        private val FINGERPRINT = Regex("""sha256:[0-9a-f]{64}""")

        /**
         * Returns the ledger of [root], which records nothing when [root] has none.
         *
         * @param exportService writes and deletes the ledger file, so a dry run only logs it
         * @throws McpConfigFileException naming the ledger, and never quoting it, if it cannot be reached where [root] allows, cannot be read, or is not a ledger of version 2 whose every path is relative to [root] without leaving it and whose every entry is named like a manifest id or a permission entry and carries a fingerprint or a non-empty list of fingerprints; a ledger of version 1, which records no fingerprint, is refused naming its version
         */
        fun load(root: TargetRoot, exportService: ExportService): McpLedger {
            val managed = ManagedConfigFile(root.directory.resolve(RELATIVE_PATH), root)
            val content = managed.read(managed.target())
            val records = content?.let { parse(it, managed.file) }.orEmpty()
            return McpLedger(managed, exportService, content, records.toMutableMap())
        }

        private fun parse(content: String, file: File): Map<String, Map<String, Set<String>>> {
            val root = JsonDocument.parseStrictly(content, file)

            fun invalid(problem: String): Nothing = throw McpConfigFileException(
                "'${file.absolutePath}' is not an MCP ledger the engine can read: $problem. The engine leaves every MCP file of its target untouched rather than guess what it wrote; remove the ledger, and deploy again.",
            )
            // A key is named, never a value: the engine writes only these two keys, so any other one is what a reader needs to find.
            val unknown = root.keys - setOf(VERSION, FILES)
            if (unknown.isNotEmpty()) invalid("it holds ${unknown.joinToString { "'${it.escapedForMessage()}'" }} besides 'version' and 'files'")
            when ((root[VERSION] as? JsonPrimitive)?.takeUnless { it.isString }?.content) {
                CURRENT_VERSION.toString() -> Unit
                // A ledger of the first version names ids only: trusting it would let any file of that shape remove the entries it names, whatever they hold.
                FIRST_VERSION.toString() -> invalid("it is of version $FIRST_VERSION, which records no fingerprint of what the engine wrote, and this engine reads only version $CURRENT_VERSION")
                else -> invalid("its version is not $CURRENT_VERSION")
            }
            val files = root[FILES] as? JsonObject ?: invalid("its 'files' are not an object")
            return files.mapValues { (path, entries) ->
                if (!isRelativeInside(path)) invalid("it names a path that is not relative to its target, or leaves it")
                val named = entries as? JsonObject ?: invalid("it records the entries of a file without a fingerprint of each")
                named.mapValues { (name, fingerprint) ->
                    if (!isEntryName(name)) invalid("it records entries that are not named like a manifest id or a permission entry")
                    fingerprintsOf(fingerprint) ?: invalid("it records an entry without a fingerprint of the form 'sha256:' followed by 64 lowercase hexadecimal digits")
                }
            }
        }

        // A text is one fingerprint; a list, the fingerprints of an entry whose edit was pending, each of which must be one.
        private fun fingerprintsOf(recorded: JsonElement): Set<String>? {
            val texts = when (recorded) {
                is JsonPrimitive -> listOf(recorded)
                is JsonArray -> recorded.map { it as? JsonPrimitive ?: return null }.takeIf { it.isNotEmpty() }
                else -> null
            }
            return texts?.map { text -> text.takeIf { it.isString && FINGERPRINT.matches(it.content) }?.content ?: return null }?.toSet()
        }

        private fun isEntryName(name: String): Boolean = ENTRY.matches(name) && name != "." && name != ".." && !name.holdsControlCharacter()

        private fun isRelativeInside(path: String): Boolean {
            val segments = path.split('/')
            return path.isNotEmpty() && !path.startsWith("/") && '\\' !in path && !path.holdsControlCharacter() && segments.none { it.isEmpty() || it == "." || it == ".." }
        }
    }
}
