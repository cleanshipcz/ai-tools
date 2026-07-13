package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.io.OutputStreamOutput
import cz.cleanship.aitools.engine.models.SkillFile
import cz.cleanship.aitools.engine.models.VersionedManifest
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream

class ExportService {

    fun <T : VersionedManifest> export(
        entity: T,
        targetFile: File,
        outputConsumer: (Output) -> Unit,
    ) {
        targetFile.parentFile.mkdirs()
        OutputStreamOutput(FileOutputStream(targetFile)).use(outputConsumer)
        LOG.info("Exported ${entity.javaClass.simpleName} ${entity.id} to ${targetFile.absolutePath}")
    }

    fun copySkillFiles(
        skillFiles: List<SkillFile>,
        sourceDir: File?,
        targetDir: File,
    ) {
        for (skillFile in skillFiles) {
            val sourceFile = resolveSource(skillFile.source, sourceDir)
            val targetFile = targetDir.resolve(skillFile.target)
            targetFile.parentFile.mkdirs()
            sourceFile.copyTo(targetFile, overwrite = true)
            LOG.info("Copied skill file {} to {}", sourceFile.absolutePath, targetFile.absolutePath)
        }
    }

    private fun resolveSource(source: String, sourceDir: File?): File {
        val file = File(source)
        if (file.isAbsolute) return file
        requireNotNull(sourceDir) {
            "Cannot resolve relative skill file '$source' without a source directory. " +
                "Use a directory-based skill or provide an absolute path."
        }
        return sourceDir.resolve(source)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ExportService::class.java)
    }
}
