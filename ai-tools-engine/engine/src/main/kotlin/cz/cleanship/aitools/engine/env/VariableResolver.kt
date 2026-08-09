package cz.cleanship.aitools.engine.env

/**
 * Where a variable that no config file of the run declares is looked up.
 *
 * The environment of the process sits behind an interface rather than being read through [System.getenv] at the point
 * of use, so that a test can hand a resolver an environment of its own instead of mutating the one of the JVM it runs in.
 */
fun interface EnvironmentSource {

    /**
     * Returns the value the environment holds for [name], or `null` when it holds none.
     */
    fun read(name: String): String?

    companion object {
        /**
         * The environment of the running process, which is what a run that is not a test reads.
         */
        val PROCESS = EnvironmentSource { name -> System.getenv(name) }
    }
}

/**
 * Expands the `${NAME}` references a value declared in a YAML file carries - `locations.*` in `config.yml`,
 * `deploy.directory` in `project.yml` - before that value is resolved into a path.
 *
 * A name is looked up in [variables] first - the `env_vars` of `config.yml` merged with those of `config.local.yml`,
 * which win per key - and in [environment] second. A user can therefore keep a base that differs between machines out
 * of the manifests altogether, declaring it in the local config or exporting it, and still share the manifest that
 * references it.
 *
 * A reference is `${NAME}` where NAME matches `[A-Za-z_][A-Za-z0-9_]*`, and a value may carry any number of them.
 * A `${...}` group of any other shape - `${1ST}`, `${A-B}` - is not addressing a variable and is left exactly as
 * written, because a declared path is allowed to contain one.
 *
 * A name neither source carries fails the run with [UnresolvedVariableException]. It is never left in the value: the
 * adapters create the directory a `deploy.directory` resolves to, and delete under it when the project replaces its
 * artifacts, so a literal `${NAME}` reaching that far would write to - and clear out - a directory named after the
 * mistake rather than after the intent.
 *
 * Expansion is single-pass, and nested expansion is out of scope. A reference that survives that one pass therefore
 * fails with [UnexpandedReferenceException] rather than being expanded further, which is what keeps the alternative -
 * a path quietly holding a literal `${...}` - from ever being deployed to. One pass also cannot loop, not even when a
 * variable refers to itself.
 */
class VariableResolver(
    variables: Map<String, String> = emptyMap(),
    private val environment: EnvironmentSource = EnvironmentSource.PROCESS,
) {

    /**
     * The declared variables as they stood when this resolver was built. The map handed in is copied rather than
     * kept, so that a caller still holding it cannot change what a path resolves to later in the run.
     */
    private val declared = variables.toMap()

    /**
     * Returns [value] with every reference it carries expanded.
     *
     * @param origin where [value] was declared, named in a failure so that the author is told which field to fix -
     * for example `locations.agents of config.yml`, or `deploy.directory of project 'ai-tools'`
     * @throws UnresolvedVariableException if a referenced variable is declared neither by the config nor by the environment
     * @throws UnexpandedReferenceException if a reference survives the one pass this resolver makes
     */
    fun substitute(value: String, origin: String? = null): String {
        val substituted = REFERENCE.replace(value) { reference ->
            val name = reference.groupValues[1]
            declared[name] ?: environment.read(name) ?: throw UnresolvedVariableException(name, value, origin)
        }
        // The result is checked as a whole rather than each expansion on its own, because an expansion can also
        // assemble a reference together with the text around it: a variable holding a bare dollar turns the `{NAME}`
        // written after it into a reference that no expansion ever looked at.
        val leftover = REFERENCE.find(substituted) ?: return substituted
        throw UnexpandedReferenceException(leftover.groupValues[1], substituted, value, origin)
    }

    /**
     * Two resolvers are equal when they were built from the same variables and read the same environment - that is,
     * when they substitute every value the same way. Equality is written over the copy taken at construction rather
     * than over the map that was handed in, so that a caller mutating that map afterwards can neither make two
     * resolvers that resolve differently compare equal, nor move the hash of one already in a collection.
     *
     * [cz.cleanship.aitools.engine.models.EngineConfig] carries a resolver, and a data class that holds one is only
     * comparable by value as long as this is.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VariableResolver) return false
        return declared == other.declared && environment == other.environment
    }

    override fun hashCode(): Int = 31 * declared.hashCode() + environment.hashCode()

    /**
     * Names the variables without their values: a run declares them to keep machine-specific paths out of the
     * manifests, and whatever a user puts in one has no business being repeated into a log line.
     */
    override fun toString(): String = "VariableResolver(variables=${declared.keys}, environment=$environment)"

    companion object {
        private val REFERENCE = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""")
    }
}

/**
 * Thrown when a `${NAME}` reference of a declared path cannot be turned into the path that was meant.
 */
sealed class VariableSubstitutionException(message: String) : RuntimeException(message)

/**
 * Thrown when a referenced variable is declared neither under `env_vars` in the config files of the run nor in its
 * environment.
 */
class UnresolvedVariableException(
    val variable: String,
    val value: String,
    val origin: String? = null,
) : VariableSubstitutionException(
        "Unresolved variable '$variable' in \"$value\"${origin.readFrom()}. " +
            "Declare '$variable' under 'env_vars:' in config.yml or config.local.yml, or export it in the environment of the run.",
    )

/**
 * Thrown when a `${NAME}` reference survives the single pass [VariableResolver] makes, which happens when a variable
 * carries a reference in its own value and when an expansion assembles one together with the text around it. Either
 * way the reference is left unexpanded, and a path is never deployed to carrying one.
 *
 * @param variable the name of the reference that survived, which is not necessarily the one [value] asked for
 */
class UnexpandedReferenceException(
    val variable: String,
    val substituted: String,
    val value: String,
    val origin: String? = null,
) : VariableSubstitutionException(
        "Unexpanded reference '\${$variable}' left after substituting \"$value\"${origin.readFrom()}, which produced \"$substituted\". " +
            "Nested expansion is not supported: declare every variable with a value that is already expanded.",
    )

private fun String?.readFrom() = this?.let { ", read from $it" } ?: ""
