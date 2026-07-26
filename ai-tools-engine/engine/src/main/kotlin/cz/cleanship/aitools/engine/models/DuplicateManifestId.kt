package cz.cleanship.aitools.engine.models

import java.io.File

/**
 * Two distinct manifest files of the same kind declaring the same id.
 *
 * Manifest ids are the primary key of the whole engine, so a collision is an authoring error rather than something
 * to resolve silently: every manifest sharing the contested id is left out of the index and this record is reported
 * instead, so that no run can pick a winner behind the author's back.
 */
data class DuplicateManifestId(
    val id: String,
    val firstFile: File,
    val secondFile: File,
) {
    /** The authoring error to report, naming the id and both files so that either one of them can be renamed. */
    val message: String
        get() = "Duplicate manifest id '$id' declared in both ${firstFile.absolutePath} and " +
            "${secondFile.absolutePath}. Manifest ids must be unique - rename one of them."
}
