package cz.cleanship.aitools.engine.utils

import cz.cleanship.aitools.engine.data.sourceBackedSkill
import java.io.File

/** The content of the one companion file [sourceBackedSkill] declares. */
const val SOURCE_BACKED_TEMPLATE = "task template"

/**
 * Writes the `SKILL.md` and the companion files of [sourceBackedSkill] into a source folder under [directory] and returns that folder.
 */
fun writeSourceBackedSkillFiles(directory: File): File {
    val sourceDir = directory.resolve("plain-skills/${sourceBackedSkill.id}")
    val template = sourceDir.resolve(sourceBackedSkill.files.single().source)
    template.parentFile.mkdirs()
    template.writeText(SOURCE_BACKED_TEMPLATE)
    // A real source folder holds its SKILL.md next to the companion files, so a test can prove a deploy leaves it alone.
    sourceDir.resolve("SKILL.md").writeText("---\nname: ${sourceBackedSkill.id}\ndescription: ${sourceBackedSkill.description}\n---\n\n${sourceBackedSkill.body}")
    return sourceDir
}

/**
 * Returns the text of every file under this directory by its path relative to it, following links the way a deploy reads them.
 */
fun File.contentSnapshot(): Map<String, String> =
    walkTopDown().filter { it.isFile }.associate { it.relativeTo(this).path to it.readText() }
