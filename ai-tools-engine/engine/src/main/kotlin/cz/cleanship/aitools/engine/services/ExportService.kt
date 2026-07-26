package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.io.OutputStreamOutput
import cz.cleanship.aitools.engine.models.SkillFile
import cz.cleanship.aitools.engine.models.VersionedManifest
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ExportService {

    /**
     * Exports [entity] to [targetFile] atomically.
     *
     * Content resolution happens inside [outputConsumer], so it can fail half-way through writing. To make sure a
     * failure never ships a truncated artifact, the content is written to a temporary file in the target directory
     * first and only moved onto [targetFile] once [outputConsumer] has completed successfully. On failure the
     * temporary file is removed and any previously exported [targetFile] is left untouched.
     *
     * @throws Exception whatever [outputConsumer] throws, after the temporary file has been cleaned up
     */
    fun <T : VersionedManifest> export(
        entity: T,
        targetFile: File,
        outputConsumer: (Output) -> Unit,
    ) {
        val targetDir = targetFile.parentFile
        targetDir.mkdirs()
        val temporaryFile = File.createTempFile("${targetFile.name}.", ".tmp", targetDir)
        try {
            OutputStreamOutput(FileOutputStream(temporaryFile)).use(outputConsumer)
            Files.move(temporaryFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            // No-op once the move above succeeded, cleans up the partial write otherwise.
            temporaryFile.delete()
        }
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
