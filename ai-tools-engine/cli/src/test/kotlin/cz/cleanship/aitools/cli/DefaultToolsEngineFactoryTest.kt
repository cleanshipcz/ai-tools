package cz.cleanship.aitools.cli

import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.models.SecretsManagerKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class DefaultToolsEngineFactoryTest {

    private lateinit var secretsManagers: SecretsManagerFactory

    // - the environment the config files of the run were loaded with; no real one is read
    private val environment = EnvironmentSource { name -> mapOf("PATH" to "/test/bin")[name] }
    private val workingDirectory = File("workspace")

    @BeforeEach
    fun setUp() {
        secretsManagers = mockk()
    }

    @Test
    fun `should build the secrets manager for the working directory with the environment the variables of the run read`() {
        // given
        every { secretsManagers.create(SecretsManagerKind.LIBSECRET, workingDirectory, environment) } returns null

        // when
        DefaultToolsEngineFactory(secretsManagers).create(
            tools = emptyList(),
            workingDirectory = workingDirectory,
            variables = VariableResolver(emptyMap(), environment),
            userHome = File("home"),
            dryRun = true,
            secretsManager = SecretsManagerKind.LIBSECRET,
        )

        // then
        // - the processor the factory returns does not show its engine, so the call itself is what proves which environment the manager reads
        verify { secretsManagers.create(SecretsManagerKind.LIBSECRET, workingDirectory, environment) }
    }
}
