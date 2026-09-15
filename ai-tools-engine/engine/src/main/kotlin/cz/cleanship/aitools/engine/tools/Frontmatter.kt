package cz.cleanship.aitools.engine.tools

/**
 * Renders manifest prose as a value of the YAML frontmatter every tool puts in front of its markdown files.
 */
object Frontmatter {

    /**
     * Returns [text] as a single-line, double-quoted YAML scalar.
     *
     * Descriptions are free prose, so writing them as a plain scalar is not safe: a `: ` inside the sentence turns
     * the line into a nested mapping, which GitHub Copilot rejects with "mapping values are not allowed in this
     * context", and a leading `#`, `[`, `{`, `*`, `&`, `!`, `|`, `>`, `%`, `@` or quote, or a ` #` anywhere,
     * changes the meaning as well. Inside a double-quoted scalar only `\` and `"` are special, so the value is
     * always quoted instead of relying on a heuristic to decide when quoting is needed. Line breaks are collapsed
     * to spaces because every tool expects the description on one line.
     */
    fun value(text: String): String {
        val escaped = text
            .lines()
            .joinToString(" ")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
