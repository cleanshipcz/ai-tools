package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.launcher.McpLauncherContract
import cz.cleanship.aitools.engine.process.ProgramRunner
import cz.cleanship.aitools.engine.process.SystemProgramRunner
import org.slf4j.LoggerFactory
import java.io.File

/**
 * The libsecret keyring as the secrets manager of the machine: a tool starts a server through the launcher `scripts/mcp-launch`, which reads each secret with `secret-tool` when the server starts, falling back to the environment of the tool; the engine itself only asks whether an item exists, through [probe].
 *
 * The launcher is checked once, and each name asked about once, per instance, which is one run. Not thread-safe.
 *
 * @param checkout the ai-tools repository the engine runs from, the directory holding its `config.yml`, whose launcher is rendered by its absolute real path - see [McpLauncherFile]
 * @param environment the environment of the run, whose `PATH` is searched for `secret-tool`
 * @param runner finds `secret-tool` on that `PATH`, and starts the programs of [probe]
 * @param probe tells whether the keyring holds an item for a secret
 * @param ownership reports who owns the launcher and who may write it
 */
class LibsecretSecretsManager(
    checkout: File,
    private val environment: EnvironmentSource,
    private val runner: ProgramRunner = SystemProgramRunner,
    private val probe: SecretServiceProbe = SecretServiceProbe(environment, runner = runner),
    ownership: LauncherFileOwnership = LauncherFileOwnership.SYSTEM,
) : McpSecretsManager {

    private val launcherFile = McpLauncherFile(checkout, ownership)
    private val presences = mutableMapOf<String, SecretPresence>()
    private var secretToolChecked = false

    // Checked on first use rather than at construction, so a run that renders no server through the launcher never looks at it.
    private val launcherState: McpLauncherFile.State by lazy { launcherFile.check() }

    override val label: String = "the keyring"

    /**
     * Returns [transport] started by the launcher: its absolute real path is the command, and its arguments are [serverId], `--required` or `--optional` and the name of each of [secrets] as the secret is required or not, `--`, then the command of [transport] and its arguments unchanged.
     *
     * The references of [secrets] in the environment are made optional, since the launcher itself refuses to start the server without a required one, and a tool that expands an unset required reference to its literal text reports it as missing. Every other entry is kept. `DBUS_SESSION_BUS_ADDRESS` and `XDG_RUNTIME_DIR`, which the launcher reaches the keyring through, are added to the forwarded names.
     *
     * The first call of [launch] or [presenceOf] logs a warning when `secret-tool` is not on the `PATH` of [environment].
     *
     * @throws McpServerResolvingException naming [serverId] and the launcher when a tool may not be given the launcher to start - see [McpLauncherFile.check]; naming [serverId] alone when the launcher would refuse the id; naming [serverId], the secret and the pattern it matches when the launcher would refuse the name of a secret
     */
    override fun launch(
        serverId: String,
        transport: ResolvedMcpTransport.Stdio,
        secrets: List<McpValue.Secret>,
    ): ResolvedMcpTransport.Stdio {
        warnOnceWithoutSecretTool()
        val command = when (val state = launcherState) {
            is McpLauncherFile.State.Usable -> state.path
            is McpLauncherFile.State.Unusable -> throw McpServerResolvingException(serverId, "MCP server '$serverId' ${state.problem}")
        }
        requireLaunchable(serverId, secrets)
        val supplied = secrets.mapTo(mutableSetOf()) { it.variable }
        return transport.copy(
            command = command,
            args = buildList {
                add(serverId)
                secrets.forEach { secret ->
                    add(if (secret.required) McpLauncherContract.REQUIRED else McpLauncherContract.OPTIONAL)
                    add(secret.variable)
                }
                add(McpLauncherContract.SEPARATOR)
                add(transport.command)
                addAll(transport.args)
            },
            env = transport.env.mapValues { (_, value) -> if (value is McpValue.Secret && value.variable in supplied) value.copy(required = false) else value },
            forwarded = (transport.forwarded + BUS_VARIABLES).distinct(),
        )
    }

    /**
     * Returns whether the keyring holds an item for the secret [name], asking [probe] once per name and returning that answer for every later call.
     *
     * The first call of [launch] or [presenceOf] logs a warning when `secret-tool` is not on the `PATH` of [environment].
     */
    override fun presenceOf(name: String): SecretPresence {
        warnOnceWithoutSecretTool()
        return presences.getOrPut(name) { probe.search(name) }
    }

    /**
     * Returns whether the launcher counts [value] of the environment variable [name] as set - see [McpLauncherContract.countsAsSet].
     */
    override fun countsAsSet(name: String, value: String): Boolean = McpLauncherContract.countsAsSet(name, value)

    override fun storeCommand(name: String): String = "secret-tool store --label='ai-tools MCP $name' ${McpLauncherContract.keyringAttributes(name).joinToString(" ")}"

    /**
     * Fails when the launcher would refuse [serverId] or the name of one of [secrets]; the loader refuses both already, so this guards a server built any other way.
     */
    private fun requireLaunchable(serverId: String, secrets: List<McpValue.Secret>) {
        val problem = if (!McpLauncherContract.acceptsServerId(serverId)) {
            "MCP server '$serverId' cannot be started through the launcher, which accepts only an id that ${McpLauncherContract.SERVER_ID_TEXT}."
        } else {
            secrets.firstNotNullOfOrNull { secret -> McpLauncherContract.refusedPatternOf(secret.variable)?.let { secret.variable to it } }?.let { (name, pattern) ->
                "MCP server '$serverId' cannot pass the secret variable '$name' to the launcher, which refuses every secret name matching '$pattern' without regard to case: ${McpLauncherContract.REFUSAL_REASON}."
            } ?: return
        }
        throw McpServerResolvingException(serverId, problem)
    }

    private fun warnOnceWithoutSecretTool() {
        if (secretToolChecked) return
        secretToolChecked = true
        if (runner.find(SECRET_TOOL, environment.read("PATH")) == null) {
            LOG.warn(
                "The secrets manager of this machine is libsecret, but $SECRET_TOOL is not on the PATH of this run. " +
                    "The launcher reads every secret from the environment of the tool unless the PATH of the tool holds $SECRET_TOOL. " +
                    "Install $SECRET_TOOL (package libsecret-tools), or set 'secrets_manager: environment' in config.local.yml.",
            )
        }
    }

    companion object {
        private const val SECRET_TOOL = "secret-tool"

        // The launcher reaches the session bus, and so the keyring, through these; Codex clears the environment of a server unless they are forwarded.
        private val BUS_VARIABLES = listOf("DBUS_SESSION_BUS_ADDRESS", "XDG_RUNTIME_DIR")

        private val LOG = LoggerFactory.getLogger(LibsecretSecretsManager::class.java)
    }
}
