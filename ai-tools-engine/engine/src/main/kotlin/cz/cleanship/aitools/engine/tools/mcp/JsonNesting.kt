package cz.cleanship.aitools.engine.tools.mcp

/**
 * Returns the offset of the first bracket that opens an object or array deeper than [limit] levels in [text], or `null` when it nests no deeper; brackets inside strings do not count, and a string left open ends the scan, since parsing then fails on its own.
 */
internal fun nestingBeyond(text: String, limit: Int): Int? {
    var depth = 0
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            '{', '[' -> if (++depth > limit) return index
            '}', ']' -> depth--
            '"' -> index = closingQuote(text, index) ?: return null
        }
        index++
    }
    return null
}

/**
 * Returns the index of the quote that closes the string opening at [start] in [text], or `null` when the text ends first.
 */
private fun closingQuote(text: String, start: Int): Int? {
    var index = start + 1
    while (index < text.length && text[index] != '"') index += if (text[index] == '\\') 2 else 1
    return index.takeIf { it < text.length }
}
