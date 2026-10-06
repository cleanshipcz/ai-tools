package cz.cleanship.aitools.engine.tools

/**
 * Renders manifest prose as a value of the YAML frontmatter every tool puts in front of its markdown files.
 */
object Frontmatter {

    /**
     * Returns [text] as a single-line, double-quoted YAML scalar.
     *
     * Line breaks are collapsed to spaces, and every other control character is escaped, a tab as `\t` and the others as `\xNN`.
     */
    // Descriptions are free prose, so writing them as a plain scalar is not safe: a `: ` inside the sentence turns the line into a nested mapping, which GitHub Copilot rejects with "mapping values are not allowed in this context", and a leading `#`, `[`, `{`, `*`, `&`, `!`, `|`, `>`, `%`, `@` or quote, or a ` #` anywhere, changes the meaning as well. Inside a double-quoted scalar only `\`, `"` and the characters YAML does not print are special, so the value is always quoted instead of relying on a heuristic to decide when quoting is needed. Line breaks are collapsed because every tool expects the description on one line.
    fun value(text: String): String {
        val escaped = text
            .lines()
            .joinToString(" ")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\t", "\\t")
            .replace(NON_PRINTABLE) {
                "\\x" + it.value
                    .single()
                    .code
                    .toString(HEX)
                    .padStart(2, '0')
            }
        return "\"$escaped\""
    }

    /**
     * Returns [values] as a single-line YAML flow sequence, such as `[github, "odd: id"]`: a value that starts with a letter, holds only letters, digits, `_`, `.` and `-`, and is no word YAML reads as a boolean or null, is written plain, and any other one as [value] writes it.
     */
    fun list(
        values: List<String>,
    ): String = values.joinToString(prefix = "[", postfix = "]") { if (isPlain(it)) it else value(it) }

    private fun isPlain(text: String): Boolean = PLAIN.matches(text) && text.lowercase() !in YAML_WORDS

    private val PLAIN = Regex("[A-Za-z][A-Za-z0-9_.-]*")

    private const val HEX = 16

    private val YAML_WORDS = setOf("true", "false", "null", "yes", "no", "on", "off", "y", "n")

    // The C0 controls but the tab, DEL and the C1 controls, none of which YAML allows unescaped in a scalar; U+0085 is also a line break to YAML 1.1.
    private val NON_PRINTABLE = Regex("[\\x00-\\x08\\x0A-\\x1F\\x7F-\\x9F]")
}
