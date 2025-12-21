package cz.cleanship.aitools.engine.models

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class VersionTest {

    @ParameterizedTest
    @CsvSource(
        "1.0.0, 1, 0, 0",
        "2.11.3, 2, 11, 3",
        "0.0.1, 0, 0, 1",
    )
    fun `should parse version without suffix`(input: String, major: Int, minor: Int, patch: Int) {
        // when
        val version = Version.parse(input)

        // then
        assertThat(version.major).isEqualTo(major)
        assertThat(version.minor).isEqualTo(minor)
        assertThat(version.patch).isEqualTo(patch)
        assertThat(version.suffix).isNull()
    }

    @ParameterizedTest
    @CsvSource(
        "1.0.0-alpha, 1, 0, 0, alpha",
        "2.0.0-beta.1, 2, 0, 0, beta.1",
        "3.4.5-RC-1, 3, 4, 5, RC-1",
    )
    fun `should parse version with suffix`(input: String, major: Int, minor: Int, patch: Int, suffix: String) {
        // when
        val version = Version.parse(input)

        // then
        assertThat(version.major).isEqualTo(major)
        assertThat(version.minor).isEqualTo(minor)
        assertThat(version.patch).isEqualTo(patch)
        assertThat(version.suffix).isEqualTo(suffix)
    }

    @ParameterizedTest
    @CsvSource(
        "1.0",
        "v1.0.0",
        "1.0.0.4",
    )
    fun `should throw exception for invalid format`(input: String) {
        // when
        val exception = assertThatThrownBy { Version.parse(input) }

        // then
        exception.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should convert to string correctly`() {
        assertThat(Version(1, 0, 0).toString()).isEqualTo("1.0.0")
        assertThat(Version(1, 2, 3, "beta").toString()).isEqualTo("1.2.3-beta")
    }

    @Test
    fun `should compare versions correctly`() {
        // when
        val v100 = Version.parse("1.0.0")
        val v101 = Version.parse("1.0.1")
        val v110 = Version.parse("1.1.0")
        val v200 = Version.parse("2.0.0")
        val v100alpha = Version.parse("1.0.0-alpha")
        val v100beta = Version.parse("1.0.0-beta")

        // then
        assertThat(v100).isLessThan(v101)
        assertThat(v101).isLessThan(v110)
        assertThat(v110).isLessThan(v200)

        // - suffix handling: 1.0.0-alpha < 1.0.0-beta < 1.0.0
        assertThat(v100alpha).isLessThan(v100beta)
        assertThat(v100beta).isLessThan(v100)

        assertThat(v100).isEqualByComparingTo(Version(1, 0, 0))
    }

    @Test
    fun `should serialize and deserialize correctly`() {
        // given
        val version = Version(2, 5, 1, "rc1")

        // when
        val json = Json.encodeToString(VersionSerializer, version)
        val deserialized = Json.decodeFromString(VersionSerializer, json)

        // then
        assertThat(json).isEqualTo("\"2.5.1-rc1\"")
        assertThat(deserialized).isEqualTo(version)
    }
}
