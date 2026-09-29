package cz.cleanship.aitools.engine.services

/** What the author of a pointer manifest can choose instead of what its `server.json` derives, as the end of a sentence that starts with "select". */
internal const val POINTER_CHOICES = "another package or remote of its server.json with 'select:', if it declares one, or point 'source' at another server.json"

/** What the author of a pointer manifest can do about a server its `server.json` derives, as one sentence. */
internal const val POINTER_REMEDY = "Select $POINTER_CHOICES."

/**
 * Returns the end of a message about an MCP server after its statement of the problem: [inline], which starts with its own punctuation, or for a [pointer] server a full stop and [POINTER_REMEDY].
 */
// A pointer manifest declares no transport and no variables, so what its server.json derives changes only with another part of that file or another file.
internal fun remedy(pointer: Boolean, inline: String) = if (pointer) ". $POINTER_REMEDY" else inline
