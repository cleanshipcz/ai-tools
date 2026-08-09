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
