package cz.cleanship.aitools.engine.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class PathOverlapTest {

    @TempDir
    lateinit var tempDir: File

    @ParameterizedTest
    @CsvSource(
        "source, source, SAME",
        "source/nested, source, INSIDE",
        "source, source/nested, CONTAINS",
        "source/./nested/.., source, SAME",
        "source-other, source, ",
        "elsewhere, source, ",
    )
    fun `should tell how two paths lie relative to each other`(path: String, other: String, expected: PathOverlap?) {
        // given
        // - the paths are compared as written, none of them needs to exist
        val file = tempDir.resolve(path)
        val otherFile = tempDir.resolve(other)

        // when
        val overlap = file.overlapWith(otherFile)

        // then
        assertThat(overlap).isEqualTo(expected)
    }

    @Test
    fun `should see through a link to a folder`() {
        // given
        val source = tempDir.resolve("checkout/skills/jira-ticket")
        source.mkdirs()
        val link = tempDir.resolve("home/.claude/skills/jira-ticket")
        link.parentFile.mkdirs()
        Files.createSymbolicLink(link.toPath(), source.toPath())

        // when
        val overlap = link.overlapWith(source)

        // then
        assertThat(overlap).isEqualTo(PathOverlap.SAME)
    }

    @Test
    fun `should see through a link on the way to a path that does not exist yet`() {
        // given
        // - the generated folder is not there yet, but its parent is a link into the source folder
        val source = tempDir.resolve("checkout/skills")
        source.mkdirs()
        val link = tempDir.resolve("home/.claude/skills")
        link.parentFile.mkdirs()
        Files.createSymbolicLink(link.toPath(), source.toPath())

        // when
        val overlap = link.resolve("jira-ticket").overlapWith(source)

        // then
        assertThat(overlap).isEqualTo(PathOverlap.INSIDE)
    }

    @Test
    fun `should resolve a link before the parent step that follows it`() {
        // given
        // - 'lnk' leads to 'deep/inner', so on disk 'lnk/../proj' is 'deep/proj', while the same path read as text would be 'proj'
        val source = tempDir.resolve("deep/proj/.claude/skills/probe")
        source.mkdirs()
        tempDir.resolve("deep/inner").mkdirs()
        tempDir.resolve("proj/.claude/skills/probe").mkdirs()
        Files.createSymbolicLink(tempDir.resolve("lnk").toPath(), tempDir.resolve("deep/inner").toPath())
        val path = File(tempDir, "lnk/../proj/.claude/skills/probe")

        // when
        val overlap = path.overlapWith(source)

        // then
        assertThat(overlap).isEqualTo(PathOverlap.SAME)
    }

    @Test
    fun `should resolve a link before the parent step that follows it when the rest of the path does not exist yet`() {
        // given
        // - the generated folder is not there yet, and the only part of its path that exists climbs out of a link
        val source = tempDir.resolve("deep/proj")
        source.mkdirs()
        tempDir.resolve("deep/inner").mkdirs()
        Files.createSymbolicLink(tempDir.resolve("lnk").toPath(), tempDir.resolve("deep/inner").toPath())
        val path = File(tempDir, "lnk/../proj/.claude/skills/probe")

        // when
        val overlap = path.overlapWith(source)

        // then
        assertThat(overlap).isEqualTo(PathOverlap.INSIDE)
    }

    @Test
    fun `should report no overlap for a link that leads elsewhere`() {
        // given
        val source = tempDir.resolve("checkout/skills/jira-ticket")
        source.mkdirs()
        val elsewhere = tempDir.resolve("elsewhere")
        elsewhere.mkdirs()
        val link = tempDir.resolve("checkout/skills/jira-ticket-link")
        Files.createSymbolicLink(link.toPath(), elsewhere.toPath())

        // when
        val overlap = link.overlapWith(source)

        // then
        assertThat(overlap).isNull()
    }

    @Nested
    inner class ScanBelow {

        @Test
        fun `should find every link below a directory without following a loop`() {
            // given
            // - 'a/loop' leads back to the folder holding it, and 'self' leads to itself, so following either would never end
            val root = tempDir.resolve("replaced")
            root.resolve("a").mkdirs()
            Files.createSymbolicLink(root.resolve("a/loop").toPath(), Path.of("../a"))
            Files.createSymbolicLink(root.resolve("self").toPath(), Path.of("self"))

            // when
            val scan = root.scanBelow()

            // then
            assertThat(scan.links).containsExactlyInAnyOrder(
                SymbolicLink(root.resolve("a/loop"), root.resolve("a").toPath().toRealPath()),
                SymbolicLink(root.resolve("self"), root.toPath().toRealPath().resolve("self")),
            )
            assertThat(scan.unreadableFolders).isEmpty()
        }

        @Test
        fun `should report the path a dangling link would lead to`() {
            // given
            val root = tempDir.resolve("replaced")
            root.mkdirs()
            val missing = tempDir.resolve("missing/folder")
            Files.createSymbolicLink(root.resolve("dangling").toPath(), missing.toPath())

            // when
            val scan = root.scanBelow()

            // then
            assertThat(scan.links).containsExactly(SymbolicLink(root.resolve("dangling"), tempDir.toPath().toRealPath().resolve("missing/folder")))
        }

        @Test
        fun `should resolve a relative link against the folder holding it`() {
            // given
            // - '../target' is read from 'replaced/sub', not from the working directory of the process
            val root = tempDir.resolve("replaced")
            root.resolve("sub").mkdirs()
            root.resolve("target").mkdirs()
            Files.createSymbolicLink(root.resolve("sub/rel").toPath(), Path.of("../target"))

            // when
            val scan = root.scanBelow()

            // then
            assertThat(scan.links).containsExactly(SymbolicLink(root.resolve("sub/rel"), root.resolve("target").toPath().toRealPath()))
        }

        @Test
        fun `should name each link under the path as given when that path is itself a link`() {
            // given
            // - '.claude' is a link to the real folder, which holds a link of its own
            val real = tempDir.resolve("real")
            real.mkdirs()
            val elsewhere = tempDir.resolve("elsewhere")
            elsewhere.mkdirs()
            Files.createSymbolicLink(real.resolve("vendor").toPath(), elsewhere.toPath())
            val alias = tempDir.resolve("project/.claude")
            alias.parentFile.mkdirs()
            Files.createSymbolicLink(alias.toPath(), real.toPath())

            // when
            val scan = alias.scanBelow()

            // then
            assertThat(scan.links).containsExactly(SymbolicLink(alias.resolve("vendor"), elsewhere.toPath().toRealPath()))
        }

        @Test
        fun `should report a folder it cannot read under the path as given and still walk the rest`() {
            // given
            val root = tempDir.resolve("replaced")
            val locked = root.resolve("locked")
            locked.mkdirs()
            root.resolve("open").mkdirs()
            val elsewhere = tempDir.resolve("elsewhere")
            elsewhere.mkdirs()
            Files.createSymbolicLink(root.resolve("open/vendor").toPath(), elsewhere.toPath())
            locked.setReadable(false)
            try {
                // - a superuser reads the folder regardless of its permissions, so the failure cannot be provoked there
                assumeFalse(locked.canRead())

                // when
                val scan = root.scanBelow()

                // then
                assertThat(scan.unreadableFolders).containsExactly(locked)
                assertThat(scan.links).containsExactly(SymbolicLink(root.resolve("open/vendor"), elsewhere.toPath().toRealPath()))
            } finally {
                locked.setReadable(true)
            }
        }

        @ParameterizedTest
        @CsvSource("missing", "file.txt")
        fun `should find nothing below a path that is not an existing directory`(name: String) {
            // given
            tempDir.resolve("file.txt").writeText("Not a folder.\n")

            // when
            val scan = tempDir.resolve(name).scanBelow()

            // then
            assertThat(scan.links).isEmpty()
            assertThat(scan.unreadableFolders).isEmpty()
        }
    }
}
