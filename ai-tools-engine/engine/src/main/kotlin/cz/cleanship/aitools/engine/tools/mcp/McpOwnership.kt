package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.io.escapedForMessage
import org.slf4j.LoggerFactory
import java.io.File

// Every decision about which entries of an MCP file are the engine's is made here, and so is what the MCP ledger records of them around an edit: the exporters and the planner pass what they read and apply what this returns, so a change to the rule is made once.

/**
 * Decides which entries of an MCP config file or a permissions file the engine owns, and what the [McpLedger] records of them around an edit.
 */
internal object McpOwnership {

    /**
     * Returns the names of the server entries of [present] the engine owns in [file]: every entry [selected] names, every entry [manifestIds] names while [selected] is not empty, and every entry whose fingerprint now is one [recorded] records for it. Logs a warning naming [file] and each entry of [present] that [recorded] records with other fingerprints only and that is not owned otherwise; such an entry is left in place.
     *
     * @param present the fingerprint of every entry [file] holds, by entry name
     * @param manifestIds the id of every MCP server manifest of the run
     * @param selected the ids of the servers the deployment selects
     * @param recorded every fingerprint the ledger records for each entry of [file], by entry name
     */
    fun ownedServerEntries(
        file: File,
        present: Map<String, String>,
        manifestIds: Set<String>,
        selected: Set<String>,
        recorded: Map<String, Set<String>>,
    ): Set<String> {
        // Inside a deployment that selects servers, an entry named after any MCP server manifest of the run is the engine's; the ledger adds the entries it wrote whose manifest is gone, and alone decides for a deployment that selects none, each only while the entry still holds what the engine wrote.
        val byName = present.keys.filterTo(mutableSetOf()) { it in selected || (selected.isNotEmpty() && it in manifestIds) }
        val owned = byName + recorded.matchedIn(present)
        reportChangedRecords(file, recorded, present, owned)
        return owned
    }

    /**
     * Logs a warning naming [file] and each entry of [owned] that [recorded] does not record with what it holds and that an edit writing [written] replaces with other content or removes; what the entry holds is never logged.
     *
     * @param present the fingerprint of every entry [file] holds, by entry name
     * @param owned the names of the entries of [present] the engine owns - see [ownedServerEntries]
     * @param recorded every fingerprint the ledger records for each entry of [file], by entry name
     * @param written the fingerprint of every entry of the engine the edit writes, by entry name
     */
    fun reportUnrecordedTakeovers(
        file: File,
        present: Map<String, String>,
        owned: Set<String>,
        recorded: Map<String, Set<String>>,
        written: Map<String, String>,
    ) {
        // Such an entry is owned only by its name, so it may be one the user wrote by hand, credential included, that the edit is about to drop.
        owned.filter { name -> recorded[name]?.contains(present[name]) != true && written[name] != present[name] }.sorted().forEach { name ->
            LOG.warn(
                "'{}' holds the entry '{}', which the MCP ledger does not record: the engine owns it only because it is named after an MCP server manifest, and {}. Whatever the entry holds, such as a credential written by hand, is not kept; rename the entry to keep it.",
                file.absolutePath,
                name.escapedForMessage(),
                if (name in written) "replaces it with the server it deploys" else "removes it, since the deployment does not select it",
            )
        }
    }

    /**
     * Returns the names of the permission entries of [present], such as `deny:mcp__github__push`, the engine owns in [file]: exactly those whose fingerprint now is one [recorded] records for them, so an entry written by hand is never owned. Logs a warning naming [file] and each entry of [present] that [recorded] records with other fingerprints only; such an entry is left in place.
     *
     * @param present the fingerprint of every permission entry [file] holds, by entry name
     * @param recorded every fingerprint the ledger records for each entry of [file], by entry name
     */
    fun ownedPermissionEntries(
        file: File,
        present: Map<String, String>,
        recorded: Map<String, Set<String>>,
    ): Set<String> {
        // A deny rule the user wrote is a security control of theirs, so ownership of a permission entry comes from the ledger alone, never from its name.
        val owned = recorded.matchedIn(present)
        reportChangedRecords(file, recorded, present, owned)
        return owned
    }

    /**
     * Returns what the ledger records for a file while an edit of it is pending: every fingerprint of [recorded] together with the fingerprint of each entry of [entries], so an entry the edit changes is owned whether the file holds what it held or what the edit writes.
     *
     * @param recorded every fingerprint the ledger records for each entry of the file, by entry name
     * @param entries the fingerprint of every entry of the engine the file holds once the edit is committed, by entry name
     */
    fun pendingRecord(
        recorded: Map<String, Set<String>>,
        entries: Map<String, String>,
    ): Map<String, Set<String>> = (recorded.keys + entries.keys).associateWith { name -> recorded[name].orEmpty() + listOfNotNull(entries[name]) }

    /**
     * Returns what the ledger records for a file once an edit of it is committed: the one fingerprint of each entry of [entries].
     *
     * @param entries the fingerprint of every entry of the engine the file holds, by entry name
     */
    fun committedRecord(
        entries: Map<String, String>,
    ): Map<String, Set<String>> = entries.mapValues { (_, fingerprint) -> setOf(fingerprint) }

    private fun Map<String, Set<String>>.matchedIn(present: Map<String, String>): Set<String> =
        present.filterTo(mutableMapOf()) { (name, fingerprint) -> this[name]?.contains(fingerprint) == true }.keys

    // Such an entry changed since the engine wrote it, or the ledger is not one the engine wrote.
    private fun reportChangedRecords(
        file: File,
        recorded: Map<String, Set<String>>,
        present: Map<String, String>,
        owned: Set<String>,
    ) {
        present.filter { (name, fingerprint) -> name !in owned && recorded[name]?.contains(fingerprint) == false }.keys.sorted().forEach { name ->
            LOG.warn(
                "'{}' holds the entry '{}' with other content than the MCP ledger records for it: it changed since the engine wrote it, or the ledger is not one the engine wrote. The engine leaves the entry in place and records it no more.",
                file.absolutePath,
                name.escapedForMessage(),
            )
        }
    }

    // Logged under the ledger, whose records the warning is about.
    private val LOG = LoggerFactory.getLogger(McpLedger::class.java)
}
