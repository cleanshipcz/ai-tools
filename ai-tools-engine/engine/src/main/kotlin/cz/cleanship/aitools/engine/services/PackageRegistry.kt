package cz.cleanship.aitools.engine.services

/**
 * A package registry of the MCP Registry `server.json`, with the one runner a package of it starts with and the grammar its package names and versions must follow.
 *
 * Nothing a `server.json` contributes to a command is used before it matches the grammar of its registry, and none of the grammars admits text that starts with `-`, holds whitespace, or names another package, so no text of the file reaches a position where the runner reads its own options.
 */
internal enum class PackageRegistry(
    val registryType: String,
    val runner: String,
    val grammar: String,
) {
    // Package names follow validate-npm-package-name (lower case, URL-safe, at most 214 characters, optionally scoped); versions are a SemVer 2.0.0 version, a single-comparator range or a dist-tag, never an `npm:` alias or a git or URL spec. npm reads any spec ending in .tgz, .tar or .tar.gz as a local file, so neither a name nor a version may end so: only specs npm resolves from the registry are accepted.
    NPM(
        registryType = "npm",
        runner = "npx",
        grammar = "npm package name and SemVer version or dist-tag",
    ) {
        override fun isName(identifier: String) =
            identifier.length <= NPM_MAX_NAME_LENGTH && NPM_NAME.matches(identifier) && !TARBALL.matches(identifier)

        override fun isVersion(version: String) =
            (SEMVER.matches(version) || NPM_RANGE.matches(version) || NPM_TAG.matches(version)) && !TARBALL.matches(version)

        override fun reference(identifier: String, version: String?) = if (version == null) identifier else "$identifier@$version"
    },

    // Project names follow PEP 508; versions follow the version pattern of PEP 440.
    PYPI("pypi", "uvx", "PEP 508 project name and PEP 440 version") {
        override fun isName(identifier: String) = PEP_508_NAME.matches(identifier)

        override fun isVersion(version: String) = PEP_440_VERSION.matches(version)

        override fun reference(identifier: String, version: String?) = if (version == null) identifier else "$identifier==$version"
    },

    // Image references follow the grammar of github.com/distribution/reference; a version is a tag or a digest.
    OCI("oci", "docker", "OCI image reference and tag or digest") {
        override fun isName(identifier: String) = OCI_REFERENCE.matches(identifier)

        override fun isVersion(version: String) = OCI_TAG.matches(version) || OCI_DIGEST.matches(version)

        // An identifier that already carries a tag or a digest is used as written, which is how most oci packages of the registry pin their image.
        override fun reference(identifier: String, version: String?) = when {
            version == null || OCI_PINNED.matches(identifier) -> identifier
            OCI_DIGEST.matches(version) -> "$identifier@$version"
            else -> "$identifier:$version"
        }
    },
    ;

    /** Returns whether [identifier] is a package name of this registry. */
    abstract fun isName(identifier: String): Boolean

    /** Returns whether [version] is a version of this registry. */
    abstract fun isVersion(version: String): Boolean

    /** Returns the argument the runner installs [identifier] at [version] from, or at its default version when [version] is `null`. */
    abstract fun reference(identifier: String, version: String?): String

    /** Returns whether [identifier] is a package name of this registry that a runner cannot read as one of its options. */
    fun accepts(identifier: String): Boolean = !identifier.startsWith("-") && isName(identifier)

    /** Returns whether [version] is a version of this registry that a runner cannot read as one of its options. */
    fun acceptsVersion(version: String): Boolean = !version.startsWith("-") && isVersion(version)

    companion object {
        /** Returns the registry of the `registryType` [registryType], or `null` when the engine cannot start a package of it. */
        fun of(registryType: String): PackageRegistry? = entries.firstOrNull { it.registryType == registryType }
    }
}

private const val NPM_MAX_NAME_LENGTH = 214
private val NPM_NAME = Regex("""(?:@[a-z0-9-*~][a-z0-9-*._~]*/)?[a-z0-9-~][a-z0-9-._~]*""")
private val NPM_TAG = Regex("""[A-Za-z][A-Za-z0-9._-]*""")
private val NPM_RANGE =
    Regex("""(?:[~^]|[<>]=?|=)?v?(?:0|[1-9]\d*|[xX*])(?:\.(?:0|[1-9]\d*|[xX*])){0,2}(?:-[0-9A-Za-z.-]+)?""")
private val TARBALL = Regex(""".*\.(?:tgz|tar|tar\.gz)""", RegexOption.IGNORE_CASE)
private val SEMVER = Regex(
    """(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?""",
)
private val PEP_508_NAME = Regex("""[A-Z0-9]|[A-Z0-9][A-Z0-9._-]*[A-Z0-9]""", RegexOption.IGNORE_CASE)
private val PEP_440_VERSION = Regex(
    """v?(?:[0-9]+!)?[0-9]+(?:\.[0-9]+)*(?:[-_.]?(?:a|b|c|rc|alpha|beta|pre|preview)[-_.]?[0-9]*)?(?:-[0-9]+|[-_.]?(?:post|rev|r)[-_.]?[0-9]*)?(?:[-_.]?dev[-_.]?[0-9]*)?(?:\+[a-z0-9]+(?:[-_.][a-z0-9]+)*)?""",
    RegexOption.IGNORE_CASE,
)
private const val OCI_PATH_COMPONENT = """[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*"""
private const val OCI_DOMAIN_COMPONENT = """(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])"""
private const val OCI_DOMAIN = """$OCI_DOMAIN_COMPONENT(?:\.$OCI_DOMAIN_COMPONENT)*(?::[0-9]+)?"""
private const val OCI_NAME = """(?:$OCI_DOMAIN/)?$OCI_PATH_COMPONENT(?:/$OCI_PATH_COMPONENT)*"""
private const val OCI_TAG_PATTERN = """[\w][\w.-]{0,127}"""
private const val OCI_DIGEST_PATTERN = """[A-Za-z][A-Za-z0-9]*(?:[-_+.][A-Za-z][A-Za-z0-9]*)*:[0-9a-fA-F]{32,}"""
private val OCI_REFERENCE = Regex("""$OCI_NAME(?::$OCI_TAG_PATTERN)?(?:@$OCI_DIGEST_PATTERN)?""")
private val OCI_PINNED =
    Regex("""$OCI_NAME(?::$OCI_TAG_PATTERN|@$OCI_DIGEST_PATTERN|:$OCI_TAG_PATTERN@$OCI_DIGEST_PATTERN)""")
private val OCI_TAG = Regex(OCI_TAG_PATTERN)
private val OCI_DIGEST = Regex(OCI_DIGEST_PATTERN)
