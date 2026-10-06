package cz.cleanship.aitools.engine.models

/**
 * The secrets manager of the machine, as `secrets_manager` of `config.yml` or `config.local.yml` names it.
 *
 * When a tool starts a stdio MCP server, the server reads from this manager every secret that its manifest does not declare `from: environment`.
 *
 * @property configValue the value `secrets_manager` names it with
 */
enum class SecretsManagerKind(val configValue: String) {

    /** The libsecret keyring, read through `secret-tool` by the launcher `scripts/mcp-launch` of the repository the engine runs from, with the environment of the tool as the fallback. */
    LIBSECRET("libsecret"),

    /** No secrets manager: every secret is read from the environment of the tool, and every server is rendered without the launcher. */
    ENVIRONMENT("environment"),
    ;

    companion object {
        /** The secrets manager of a machine whose config files name none. */
        val DEFAULT = LIBSECRET

        /**
         * Returns the secrets manager [value] names, or `null` when it names none this engine knows; the comparison is exact.
         */
        fun ofConfigValue(value: String): SecretsManagerKind? = entries.firstOrNull { it.configValue == value }
    }
}
