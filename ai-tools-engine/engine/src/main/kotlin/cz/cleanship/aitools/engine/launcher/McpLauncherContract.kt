package cz.cleanship.aitools.engine.launcher

import cz.cleanship.aitools.engine.env.ENVIRONMENT_VARIABLE_NAME

/**
 * What the launcher `scripts/mcp-launch` accepts and does, as far as the engine depends on it: `<launcher> <server> [--required NAME | --optional NAME]... -- <command> [<argument>...]`, the names it refuses, the values it counts as not set, and the keyring attributes it looks a secret up with.
 *
 * Immutable.
 */
object McpLauncherContract {
    // The tests of the engine compare with the script the options, the server ids and secret names it accepts, the refused patterns with their classes, the refusal reason, the values it counts as not set and the keyring attributes; LAUNCHER_ENVIRONMENT_REASON has no counterpart in the script, and SERVER_ID_TEXT is worded for the engine's messages.

    /** The option that names a secret the server cannot start without. */
    const val REQUIRED = "--required"

    /** The option that names a secret the server starts without when it is found nowhere. */
    const val OPTIONAL = "--optional"

    /** The argument that ends the options; the real command and its arguments follow it unchanged. */
    const val SEPARATOR = "--"

    /** What a server name the launcher accepts consists of, as a message states it. */
    const val SERVER_ID_TEXT = "starts with a letter or digit and holds only letters, digits, '.', '_' and '-'"

    /** Why the launcher refuses a secret name, as the end of a sentence; the launcher states the same reason. */
    const val REFUSAL_REASON = "a shell, the launcher, a program it starts, a loader or an interpreter gives that variable a meaning of its own"

    /** Why a name of [RefusedNameClass.LAUNCHER] may not reach the environment of the launcher, as the end of a sentence. */
    const val LAUNCHER_ENVIRONMENT_REASON =
        "the launcher's shell, the loader and C library of that shell, or a program the launcher starts reads a variable of that name, or the shell sets it itself, so its value could change what the launcher does or appear in a message before the server starts"

    /** The attribute naming the owner of a keyring item, as the launcher looks a secret up. */
    const val SERVICE_ATTRIBUTE = "service"

    /** The value of [SERVICE_ATTRIBUTE] of every secret the launcher reads. */
    const val SERVICE = "ai-tools-mcp"

    /** The attribute holding the name of the secret variable, as the launcher looks a secret up. */
    const val VARIABLE_ATTRIBUTE = "variable"

    /**
     * The secret names the launcher refuses, as the block `mcp_launch_refused_names` of the script writes them: each pattern with its class, class A first.
     */
    // The script keeps the same lines, each written as its class letter, a colon and the pattern, in its block mcp_launch_refused_names, which is the list it enforces; McpLauncherContractTest.AgreementWithTheScript and McpLaunchFileTest fail the build when the two differ.
    val REFUSED_NAMES: List<RefusedName> = listOf(
        launcher("MCP_LAUNCH_*"),
        launcher("PATH"),
        launcher("IFS"),
        launcher("HOME"),
        launcher("DISPLAY"),
        launcher("DBUS_*"),
        launcher("XDG_*"),
        launcher("G_*"),
        launcher("GIO_*"),
        launcher("POSIXLY_CORRECT"),
        launcher("LANG"),
        launcher("LANGUAGE"),
        launcher("LC_*"),
        launcher("NLSPATH"),
        launcher("LOCPATH"),
        launcher("_"),
        launcher("BASH*"),
        launcher("ENV"),
        launcher("SHELL"),
        launcher("SHELLOPTS"),
        launcher("SHLVL"),
        launcher("CDPATH"),
        launcher("PPID"),
        launcher("PWD"),
        launcher("OLDPWD"),
        launcher("OPTIND"),
        launcher("OPTARG"),
        launcher("OPTERR"),
        launcher("PS0"),
        launcher("PS1"),
        launcher("PS2"),
        launcher("PS3"),
        launcher("PS4"),
        launcher("LINENO"),
        launcher("RANDOM"),
        launcher("SRANDOM"),
        launcher("SECONDS"),
        launcher("UID"),
        launcher("EUID"),
        launcher("GROUPS"),
        launcher("HOSTNAME"),
        launcher("HOSTTYPE"),
        launcher("MACHTYPE"),
        launcher("OSTYPE"),
        launcher("MAIL"),
        launcher("MAILCHECK"),
        launcher("MAILPATH"),
        launcher("HISTCHARS"),
        launcher("HISTCMD"),
        launcher("HISTCONTROL"),
        launcher("HISTFILE"),
        launcher("HISTFILESIZE"),
        launcher("HISTIGNORE"),
        launcher("HISTSIZE"),
        launcher("HISTTIMEFORMAT"),
        launcher("FUNCNAME"),
        launcher("FUNCNEST"),
        launcher("GLOBIGNORE"),
        launcher("GLOBSORT"),
        launcher("EXECIGNORE"),
        launcher("FIGNORE"),
        launcher("TIMEFORMAT"),
        launcher("TMOUT"),
        launcher("PROMPT_COMMAND"),
        launcher("PROMPT_DIRTRIM"),
        launcher("PIPESTATUS"),
        launcher("DIRSTACK"),
        launcher("COMP_*"),
        launcher("COMPREPLY"),
        launcher("COPROC"),
        launcher("COLUMNS"),
        launcher("LINES"),
        launcher("EMACS"),
        launcher("INSIDE_EMACS"),
        launcher("EPOCHREALTIME"),
        launcher("EPOCHSECONDS"),
        launcher("FCEDIT"),
        launcher("HOSTFILE"),
        launcher("IGNOREEOF"),
        launcher("INPUTRC"),
        launcher("MAPFILE"),
        launcher("READLINE_*"),
        launcher("REPLY"),
        launcher("CHILD_MAX"),
        launcher("LD_*"),
        launcher("DYLD_*"),
        launcher("GLIBC_TUNABLES"),
        launcher("GCONV_PATH"),
        launcher("MALLOC_*"),
        server("HOSTALIASES"),
        server("LOCALDOMAIN"),
        server("RES_OPTIONS"),
        server("NODE_*"),
        server("NPM_*"),
        server("PYTHON*"),
        server("UV_*"),
        server("PIP_*"),
        server("PERL5LIB"),
        server("PERL5OPT"),
        server("PERL5DB"),
        server("PERLLIB"),
        server("RUBYOPT"),
        server("RUBYLIB"),
        server("JAVA_TOOL_OPTIONS"),
        server("JDK_JAVA_OPTIONS"),
        server("_JAVA_OPTIONS"),
        server("DOCKER_*"),
        server("CONTAINER_*"),
        server("CONTAINERS_*"),
        server("GIT_*"),
        server("SSL_*"),
        server("*_PROXY"),
        server("TMPDIR"),
        server("TMP"),
        server("TEMP"),
        server("REQUESTS_CA_BUNDLE"),
        server("CURL_CA_BUNDLE"),
    )

    /**
     * The patterns of [REFUSED_NAMES], in the same order.
     */
    val REFUSED_NAME_PATTERNS: List<String> = REFUSED_NAMES.map { it.pattern }

    private val SERVER_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    private val REFUSED_REGEXES: List<Pair<RefusedName, Regex>> = REFUSED_NAMES.map { refused ->
        refused to Regex(refused.pattern.split('*').joinToString(".*") { Regex.escape(it) })
    }

    /**
     * Returns the entry of [REFUSED_NAMES] whose pattern [name] matches as a whole once its ASCII letters are upper case, as the launcher compares it, or `null` when the launcher accepts the name.
     *
     * A name matching patterns of both classes gets the first pattern of class A.
     */
    // REFUSED_NAMES lists class A first, so the first match is of class A whenever one exists: G_PROXY matches G_* (A) and *_PROXY (B).
    fun refusedNameOf(name: String): RefusedName? {
        val upper = name.asciiUppercase()
        return REFUSED_REGEXES.firstOrNull { (_, regex) -> regex.matches(upper) }?.first
    }

    /**
     * Returns the pattern of [refusedNameOf] for [name], or `null` when the launcher accepts the name.
     */
    fun refusedPatternOf(name: String): String? = refusedNameOf(name)?.pattern

    /**
     * Returns whether the launcher refuses [name] as the name of a secret - see [refusedPatternOf].
     */
    fun refusesName(name: String): Boolean = refusedPatternOf(name) != null

    /**
     * Returns whether the launcher accepts [name] as the name of a secret variable at all: `[A-Za-z_][A-Za-z0-9_]*`, the form of every environment variable name the engine accepts.
     */
    fun acceptsSecretName(name: String): Boolean = ENVIRONMENT_VARIABLE_NAME.matches(name)

    /**
     * Returns whether the launcher accepts [serverId] as the name of the server it starts.
     */
    fun acceptsServerId(serverId: String): Boolean = SERVER_ID.matches(serverId)

    /**
     * Returns the values of the variable [name] that the launcher counts as not set: the empty value, and the references `${NAME}`, `${NAME:-}` and `${env:NAME}` a tool may leave unexpanded.
     */
    fun notSetValues(name: String): List<String> = listOf("", "\${$name}", "\${$name:-}", "\${env:$name}")

    /**
     * Returns whether the launcher counts [value], found in the keyring or the environment for the variable [name], as set - see [notSetValues].
     */
    fun countsAsSet(name: String, value: String): Boolean = value !in notSetValues(name)

    /**
     * Returns the attributes and values the launcher looks the secret [name] up with, in the order `secret-tool` takes them.
     */
    fun keyringAttributes(name: String): List<String> = listOf(SERVICE_ATTRIBUTE, SERVICE, VARIABLE_ATTRIBUTE, name)

    // The launcher lists the 26 letters rather than asking the locale, so this does the same: 'i' becomes 'I' in every locale, and no other character changes.
    private fun String.asciiUppercase(): String = map { if (it in 'a'..'z') it.uppercaseChar() else it }.joinToString("")
}

private fun launcher(pattern: String) = RefusedName(RefusedNameClass.LAUNCHER, pattern)

private fun server(pattern: String) = RefusedName(RefusedNameClass.SERVER, pattern)
