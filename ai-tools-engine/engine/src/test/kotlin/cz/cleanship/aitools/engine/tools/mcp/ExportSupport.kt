package cz.cleanship.aitools.engine.tools.mcp

/**
 * Prepares the edit of [context] and commits it at once, as the engine does once the MCP ledger records what the edit writes.
 */
internal fun McpConfigExporter.export(context: McpContext) {
    prepare(context)?.commit()
}

/**
 * Prepares the edit of [context] and commits it at once, as the engine does once the MCP ledger records what the edit writes.
 */
internal fun McpPermissionsExporter.export(context: McpPermissionsContext) {
    prepare(context)?.commit()
}

/**
 * Returns these fingerprints by entry name as the [McpLedger] records them once an edit is committed: one fingerprint for each entry.
 */
internal fun Map<String, String>.asRecorded(): Map<String, Set<String>> = mapValues { (_, fingerprint) -> setOf(fingerprint) }
