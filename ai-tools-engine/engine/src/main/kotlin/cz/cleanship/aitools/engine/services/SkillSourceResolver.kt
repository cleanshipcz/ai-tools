package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.env.VariableSubstitutionException
import cz.cleanship.aitools.engine.io.resolveDeclaredPath
import cz.cleanship.aitools.engine.models.SkillFile
import cz.cleanship.aitools.engine.models.SkillManifest
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.relativeTo

/**
 * Turns a pointer skill, a [SkillManifest] that declares a [SkillManifest.source], into the skill the plain `SKILL.md` skill in that folder describes.
 *
 * @param variables the variables a [SkillManifest.source] is substituted with - see [VariableResolver]. The default declares none and falls back to the environment of the process.
 * @param userHome the directory a leading `~` of a substituted [SkillManifest.source] stands for - see [resolveDeclaredPath]. The default is the home directory of the user running the engine.
 */
class SkillSourceResolver(
    private val variables: VariableResolver = VariableResolver(),
    private val userHome: File = File(System.getProperty("user.home")),
) {

    /**
     * Returns [skill] with the description and body of the `SKILL.md` in its source folder - the body being the text after the frontmatter from its first line that is not empty - and with every other file of that folder as its [SkillManifest.files], together with the folder itself.
     *
     * The source is substituted with the variables of this resolver; a result that is `~` or starts with `~/` then resolves against the home directory, and any other relative result against the directory of [manifestFile]. A byte order mark before the frontmatter is skipped. Only `name` and `description` are read from the frontmatter; any other key is ignored with a warning. Nothing in the source folder is modified.
     *
     * @param manifestFile the file [skill] was read from, whose directory a relative source resolves against
     * @throws IllegalArgumentException if [skill] declares no source
     * @throws InvalidSkillSourceException if [skill] also declares a `description`, `sections` or `files`; if its source references a variable that cannot be substituted, is not an existing directory, holds no readable `SKILL.md`, cannot be walked for its companion files, or holds a link that points at nothing; or if that `SKILL.md` has no frontmatter, one that is not valid YAML, one that is not a YAML mapping, one without a text `name` and `description`, or one whose `name` is not the id of [skill]
     */
    fun resolve(skill: SkillManifest, manifestFile: File): ResolvedSkill {
        val declared = requireNotNull(skill.source) { "Skill '${skill.id}' declares no source." }
        requireNoOwnContent(skill)
        val directory = resolveDirectory(skill, declared, manifestFile)
        val skillFile = directory.resolve(SKILL_FILE)
        if (!skillFile.isFile) {
            throw InvalidSkillSourceException("The 'source' folder '${directory.absolutePath}' holds no $SKILL_FILE: '${skillFile.absolutePath}' does not exist.")
        }
        val (frontmatter, body) = splitFrontmatter(skillFile)
        val description = readFrontmatter(skill.id, skillFile, frontmatter)
        return ResolvedSkill(
            skill = skill.copy(
                description = description,
                body = body,
                files = companionFiles(directory).map { SkillFile(source = it, target = it) },
            ),
            directory = directory,
        )
    }

    private fun requireNoOwnContent(skill: SkillManifest) {
        val ownContent = listOfNotNull(
            "description".takeIf { skill.description.isNotEmpty() },
            "sections".takeIf { skill.sections.isNotEmpty() },
            "files".takeIf { skill.files.isNotEmpty() },
        )
        if (ownContent.isNotEmpty()) {
            throw InvalidSkillSourceException(
                "A skill declaring 'source' takes its content from the $SKILL_FILE in that folder, but this one also declares ${ownContent.joinToString { "'$it'" }}. Remove ${if (ownContent.size == 1) "it" else "them"}, or remove 'source'.",
            )
        }
    }

    private fun resolveDirectory(skill: SkillManifest, declared: String, manifestFile: File): File {
        val substituted = try {
            variables.substitute(declared, origin = "'source' of skill '${skill.id}'")
        } catch (ex: VariableSubstitutionException) {
            throw InvalidSkillSourceException(ex.message.orEmpty(), ex)
        }
        // The manifest's own directory is the base, not the working directory of the run: a pointer is written next to the skill it describes, and must keep pointing at the same folder wherever the engine is started from.
        val directory = manifestFile.absoluteFile.parentFile.resolveDeclaredPath(substituted, userHome)
        val problem = when {
            !directory.exists() -> "does not exist"
            !directory.isDirectory -> "is not a directory"
            else -> return directory
        }
        throw InvalidSkillSourceException("The 'source' '$declared' resolves to '${directory.absolutePath}', which $problem.")
    }

    private fun splitFrontmatter(skillFile: File): Pair<String, String> {
        val text = try {
            skillFile.readText()
        } catch (ex: IOException) {
            throw InvalidSkillSourceException("'${skillFile.absolutePath}' cannot be read: ${ex.message}", ex)
        }.removePrefix(BYTE_ORDER_MARK)
        val match = FRONTMATTER.find(text)
            ?: throw InvalidSkillSourceException("'${skillFile.absolutePath}' does not start with a YAML frontmatter: a '---' line, 'name' and 'description', and a closing '---' line.")
        // The empty lines between the frontmatter and the content only separate the two; every adapter separates its own header from the content the same way, so keeping them would double the gap.
        return match.groupValues[1] to text.substring(match.range.last + 1).trimStart('\r', '\n')
    }

    /**
     * Returns the description the [frontmatter] of [skillFile] declares, having checked that it names the skill [skillId].
     */
    private fun readFrontmatter(skillId: String, skillFile: File, frontmatter: String): String {
        val entries = parseMapping(skillFile, frontmatter)
        val name = requireText(entries, "name", skillFile)
        val description = requireText(entries, "description", skillFile)
        if (name != skillId) {
            throw InvalidSkillSourceException("The frontmatter of '${skillFile.absolutePath}' names the skill '$name', but the manifest declares the id '$skillId'. Make them the same.")
        }
        val ignored = entries.keys - READ_KEYS
        if (ignored.isNotEmpty()) {
            LOG.warn("Skill '{}' ignores the frontmatter key(s) {} of '{}': only 'name' and 'description' are deployed.", skillId, ignored, skillFile.absolutePath)
        }
        return description
    }

    private fun parseMapping(skillFile: File, frontmatter: String): Map<String, Any> {
        if (frontmatter.isBlank()) return emptyMap()
        val node = try {
            Yaml.default.parseToYamlNode(frontmatter)
        } catch (ex: YamlException) {
            throw InvalidSkillSourceException("The frontmatter of '${skillFile.absolutePath}' is not valid YAML: ${ex.message}", ex)
        }
        if (node !is YamlMap) {
            throw InvalidSkillSourceException("The frontmatter of '${skillFile.absolutePath}' is not a YAML mapping of 'name' and 'description'.")
        }
        // A scalar value is kept as its text and anything else as the node itself, which is enough to tell a text field from one that is not.
        return node.entries.entries.associate { (key, value) -> key.content to ((value as? YamlScalar)?.content ?: value) }
    }

    private fun requireText(entries: Map<String, Any>, key: String, skillFile: File): String {
        val value = entries[key]
        if (value !is String || value.isBlank()) {
            throw InvalidSkillSourceException("The frontmatter of '${skillFile.absolutePath}' declares no text '$key'. A plain skill declares both 'name' and 'description'.")
        }
        return value
    }

    /**
     * Returns the path of every file under [directory] other than its own `SKILL.md`, relative to [directory], with `/` separators, sorted.
     */
    private fun companionFiles(directory: File): List<String> {
        val root = directory.toPath()
        // Links are followed so that a linked file is deployed as the content it points at, the way a plain skill folder is read by the assistant itself; a link cycle is reported by the walk rather than looped over.
        return try {
            Files.walk(root, FileVisitOption.FOLLOW_LINKS).use { paths ->
                paths
                    .peek { requireLinkTargetExists(directory, it) }
                    .filter { it.isRegularFile() && it != root.resolve(SKILL_FILE) }
                    .map { it.relativeTo(root).joinToString("/") }
                    .sorted()
                    .toList()
            }
        } catch (ex: IOException) {
            throw unreadable(directory, ex)
        } catch (ex: UncheckedIOException) {
            // The stream of Files.walk reports a failure met while iterating - a link cycle, an unreadable subfolder - wrapped in this rather than as an IOException.
            throw unreadable(directory, ex.cause ?: ex)
        }
    }

    // A walk that follows links still lists a link whose target is gone, as the link itself; leaving it out would deploy a skill whose instructions refer to a file that is not there.
    private fun requireLinkTargetExists(directory: File, path: Path) {
        if (path.isSymbolicLink() && !path.exists()) {
            throw InvalidSkillSourceException("The 'source' folder '${directory.absolutePath}' holds the link '${path.toAbsolutePath()}', which points at nothing. Restore its target or remove the link.")
        }
    }

    private fun unreadable(directory: File, cause: Throwable) =
        InvalidSkillSourceException("The 'source' folder '${directory.absolutePath}' cannot be read: ${cause.message}", cause)

    companion object {
        private const val SKILL_FILE = "SKILL.md"
        private const val BYTE_ORDER_MARK = "\uFEFF"
        private val READ_KEYS = setOf("name", "description")

        // The opening '---' must be the very first line of the file, as it is for every assistant reading a SKILL.md; group 1 is the YAML between the two delimiter lines.
        private val FRONTMATTER =
            Regex("""\A---[ \t]*\r?\n(.*?)^---[ \t]*(?:\r?\n|\z)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))

        private val LOG = LoggerFactory.getLogger(SkillSourceResolver::class.java)
    }
}

/**
 * A skill whose [SkillManifest.source] has been read, together with the folder it was read from.
 *
 * @property skill the skill with the description, body and companion files of its source folder
 * @property directory the source folder, which the companion files of [skill] are copied from
 */
data class ResolvedSkill(
    val skill: SkillManifest,
    val directory: File,
)

/**
 * Thrown when the [SkillManifest.source] of a skill cannot be turned into the skill it points at. Like [InvalidManifestIdException], it travels wrapped in a [ManifestLoadingException], so the author is told which manifest to fix.
 */
class InvalidSkillSourceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
