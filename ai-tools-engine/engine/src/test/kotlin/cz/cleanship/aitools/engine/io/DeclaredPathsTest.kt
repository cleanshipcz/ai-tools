package cz.cleanship.aitools.engine.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class DeclaredPathsTest {

    @ParameterizedTest
    @CsvSource(
        "manifests, /base/manifests",
        "nested/manifests, /base/nested/manifests",
        "., /base/.",
        "../sibling, /base/../sibling",
        "/absolute/elsewhere, /absolute/elsewhere",
    )
    fun `should resolve a declared path against the working directory unless it is absolute`(
        declared: String,
        expected: String,
    ) {
        // given
        val workingDirectory = File("/base")

        // when
        val resolved = workingDirectory.resolveDeclaredPath(declared)

        // then
        assertThat(resolved.path).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "~, /home/me",
        "~/, /home/me",
        "~/Documents/Projects, /home/me/Documents/Projects",
        "~/../shared, /home/me/../shared",
    )
    fun `should resolve a leading tilde against the home directory`(declared: String, expected: String) {
        // given
        val workingDirectory = File("/base")
        val home = File("/home/me")

        // when
        val resolved = workingDirectory.resolveDeclaredPath(declared, home)

        // then
        assertThat(resolved.path).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        // - the home of another user is not looked up, so this is a relative path starting with a folder named ~other
        "~other/projects, /base/~other/projects",
        "projects/~, /base/projects/~",
        "projects/~/skills, /base/projects/~/skills",
        "/absolute/~/skills, /absolute/~/skills",
        "~~/projects, /base/~~/projects",
    )
    fun `should keep a tilde that is not a leading home reference as written`(declared: String, expected: String) {
        // given
        val workingDirectory = File("/base")
        val home = File("/home/me")

        // when
        val resolved = workingDirectory.resolveDeclaredPath(declared, home)

        // then
        assertThat(resolved.path).isEqualTo(expected)
    }

    @Test
    fun `should resolve a leading tilde against the home of the user running the engine by default`() {
        // given
        val workingDirectory = File("/base")

        // when
        val resolved = workingDirectory.resolveDeclaredPath("~/projects")

        // then
        assertThat(resolved).isEqualTo(File(System.getProperty("user.home"), "projects"))
    }

    @Test
    fun `should return an absolute file when the working directory itself is relative`() {
        // given
        // - the engine logs and deletes under the resolved path, so it must never stay relative
        val workingDirectory = File("workspace")

        // when
        val resolved = workingDirectory.resolveDeclaredPath("manifests")

        // then
        assertThat(resolved.isAbsolute).isTrue()
        assertThat(resolved.path).endsWith(File("workspace", "manifests").path)
    }
}
