package cz.cleanship.aitools.engine.tools.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Which entries of an MCP config file and of a permissions file are the engine's to replace or remove, and what the MCP ledger records of them around an edit.
 */
class McpOwnershipTest {

    private val file = File("/project/.mcp.json")
    private val old = fingerprint('a')
    private val new = fingerprint('b')
    private val other = fingerprint('c')

    private val warnings = ListAppender<ILoggingEvent>()
    private val ledgerLogger = LoggerFactory.getLogger(McpLedger::class.java) as Logger

    @BeforeEach
    fun setUp() {
        warnings.start()
        ledgerLogger.addAppender(warnings)
    }

    @AfterEach
    fun tearDown() {
        ledgerLogger.detachAppender(warnings)
        warnings.stop()
        warnings.list.clear()
    }

    @Nested
    inner class ServerEntries {

        /**
         * Every present entry has the fingerprint `old`; `selected` and `manifestIds` are space-separated ids, `-` for none.
         */
        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            value = [
                // - a selected server owns its entry, whatever the ledger says
                "atlassian | atlassian | atlassian   | atlassian",
                // - while servers are selected, an entry named after any manifest of the run is the engine's
                "atlassian retired | atlassian | atlassian retired | atlassian retired",
                // - without a selection, a manifest id alone owns nothing
                "retired | - | atlassian retired | -",
                // - an entry named after nothing the run knows stays foreign
                "playwright | atlassian | atlassian | -",
            ],
        )
        fun `should own the entries the selection names, and the manifest ids only while servers are selected`(
            present: String,
            selected: String,
            manifestIds: String,
            expected: String,
        ) {
            // when
            val owned = McpOwnership.ownedServerEntries(file, present.ids().associateWith { old }, manifestIds.ids(), selected.ids(), recorded = emptyMap())

            // then
            assertThat(owned).isEqualTo(expected.ids())
            assertThat(warnings.list).isEmpty()
        }

        @Test
        fun `should own an entry the ledger records with the fingerprint it has now, even without a selection`() {
            // when
            val owned = McpOwnership.ownedServerEntries(file, mapOf("retired" to new, "playwright" to other), emptySet(), emptySet(), recorded = mapOf("retired" to setOf(new)))

            // then
            assertThat(owned).containsExactly("retired")
            assertThat(warnings.list).isEmpty()
        }

        /**
         * While an edit is pending, the ledger records both what the file held and what the edit writes, so the entry stays the engine's whether the commit ran or not.
         */
        @ParameterizedTest
        @CsvSource("a", "b")
        fun `should own an entry the ledger records with several fingerprints when it holds any of them`(held: Char) {
            // when
            val owned = McpOwnership.ownedServerEntries(file, mapOf("atlassian" to fingerprint(held)), emptySet(), emptySet(), recorded = mapOf("atlassian" to setOf(old, new)))

            // then
            assertThat(owned).containsExactly("atlassian")
            assertThat(warnings.list).isEmpty()
        }

        @Test
        fun `should leave an entry the ledger records with other fingerprints foreign, warning with the file and the entry`() {
            // when
            val owned = McpOwnership.ownedServerEntries(file, mapOf("retired" to other), emptySet(), emptySet(), recorded = mapOf("retired" to setOf(old, new)))

            // then
            assertThat(owned).isEmpty()
            assertThat(warnings()).singleElement().satisfies({
                assertThat(it).contains(file.absolutePath).contains("'retired'").contains("changed since the engine wrote it")
            })
        }

        @Test
        fun `should not warn about a changed entry the selection owns anyway`() {
            // when
            val owned = McpOwnership.ownedServerEntries(file, mapOf("atlassian" to other), emptySet(), setOf("atlassian"), recorded = mapOf("atlassian" to setOf(old)))

            // then
            assertThat(owned).containsExactly("atlassian")
            assertThat(warnings.list).isEmpty()
        }

        @Test
        fun `should own nothing and warn about nothing a file does not hold`() {
            // when
            val owned = McpOwnership.ownedServerEntries(file, emptyMap(), setOf("atlassian"), setOf("atlassian"), recorded = mapOf("gone" to setOf(old)))

            // then
            assertThat(owned).isEmpty()
            assertThat(warnings.list).isEmpty()
        }

        @Test
        fun `should escape the entry it warns about`() {
            // when
            McpOwnership.ownedServerEntries(file, mapOf("a\nERROR forged" to other), emptySet(), emptySet(), recorded = mapOf("a\nERROR forged" to setOf(old)))

            // then
            assertThat(warnings()).singleElement().satisfies({ assertThat(it).contains("'a\\u000AERROR forged'").doesNotContain("\n") })
        }
    }

    @Nested
    inner class PermissionEntries {

        @Test
        fun `should own only the entries the ledger records with the fingerprint they have now`() {
            // when
            val owned = McpOwnership.ownedPermissionEntries(
                file,
                present = mapOf("deny:mcp__github__push" to new, "deny:mcp__github__drop" to other, "deny:Read" to old),
                recorded = mapOf("deny:mcp__github__push" to setOf(new), "deny:mcp__github__drop" to setOf(old)),
            )

            // then
            assertThat(owned).containsExactly("deny:mcp__github__push")
            assertThat(warnings()).singleElement().satisfies({ assertThat(it).contains(file.absolutePath).contains("'deny:mcp__github__drop'") })
        }

        @Test
        fun `should own no entry written by hand, whatever the file holds`() {
            // when
            val owned = McpOwnership.ownedPermissionEntries(file, present = mapOf("deny:mcp__github__push" to new), recorded = emptyMap())

            // then
            assertThat(owned).isEmpty()
            assertThat(warnings.list).isEmpty()
        }
    }

    @Nested
    inner class Records {

        @Test
        fun `should record every fingerprint recorded before together with what the edit writes while the edit is pending`() {
            // given
            val recorded = mapOf("atlassian" to setOf(old), "gone" to setOf(other))
            val entries = mapOf("atlassian" to new, "added" to other)

            // when
            val pending = McpOwnership.pendingRecord(recorded, entries)

            // then
            assertThat(pending).isEqualTo(mapOf("atlassian" to setOf(old, new), "gone" to setOf(other), "added" to setOf(other)))
        }

        @Test
        fun `should keep one fingerprint of an entry the edit leaves as it was while the edit is pending`() {
            // when
            val pending = McpOwnership.pendingRecord(mapOf("atlassian" to setOf(new)), mapOf("atlassian" to new))

            // then
            assertThat(pending).isEqualTo(mapOf("atlassian" to setOf(new)))
        }

        @Test
        fun `should record exactly what the edit wrote once it is committed`() {
            // when
            val committed = McpOwnership.committedRecord(mapOf("atlassian" to new, "added" to other))

            // then
            assertThat(committed).isEqualTo(mapOf("atlassian" to setOf(new), "added" to setOf(other)))
        }
    }

    private fun String.ids(): Set<String> = if (trim() == "-") emptySet() else trim().split(" ").filter { it.isNotEmpty() }.toSet()

    private fun warnings() = warnings.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private fun fingerprint(digit: Char) = "sha256:" + digit.toString().repeat(64)
}
