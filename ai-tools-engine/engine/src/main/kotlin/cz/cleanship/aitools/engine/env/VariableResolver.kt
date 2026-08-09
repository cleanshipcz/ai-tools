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
 * Expansion is single-pass. Nested expansion is out of scope, so a variable whose own value carries a reference fails
 * with [NestedVariableReferenceException] rather than being expanded further; failing is what keeps the alternative,
 * a path quietly holding a literal `${...}`, from ever being deployed to. One pass also cannot loop, not even when a
 * variable refers to itself.
 */
class VariableResolver(
    private val variables: Map<String, String> = emptyMap(),
    private val environment: EnvironmentSource = EnvironmentSource.PROCESS,
) {

    /**
     * Returns [value] with every reference it carries expanded.
     *
     * @param origin where [value] was declared, named in a failure so that the author is told which field to fix -
     * for example `locations.agents of config.yml or config.local.yml`, or `deploy.directory of project 'ai-tools'`
     * @throws UnresolvedVariableException if a referenced variable is declared neither by the config nor by the environment
     * @throws NestedVariableReferenceException if a referenced variable expands to a value carrying a reference of its own
     */
    fun substitute(value: String, origin: String? = null): String = REFERENCE.replace(value) { reference ->
        val name = reference.groupValues[1]
        val expansion = variables[name] ?: environment.read(name) ?: throw UnresolvedVariableException(name, value, origin)
        if (REFERENCE.containsMatchIn(expansion)) throw NestedVariableReferenceException(name, expansion, value, origin)
        expansion
    }

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
 * Thrown when a referenced variable expands to a value carrying a reference of its own, which this single-pass
 * substitution deliberately does not expand - see [VariableResolver].
 */
class NestedVariableReferenceException(
    val variable: String,
    val expansion: String,
    val value: String,
    val origin: String? = null,
) : VariableSubstitutionException(
        "Variable '$variable' expands to \"$expansion\", which carries a reference of its own, and nested expansion is not supported. " +
            "Declare '$variable' with an already expanded value. Referenced in \"$value\"${origin.readFrom()}.",
    )

private fun String?.readFrom() = this?.let { ", read from $it" } ?: ""
