package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.io.OutputStreamOutput
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

    companion object {
        private val LOG = LoggerFactory.getLogger(ExportService::class.java)
    }
}
