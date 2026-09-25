package cz.cleanship.aitools.engine.io

/**
 * Returns this text as a message or a log line may name it when it was read from a file or a manifest the engine does not trust, such as a key of `~/.claude.json` or an id of an MCP ledger: every character below U+0020, from U+007F to U+009F, U+2028, U+2029, every format character of Unicode (category `Cf`, such as U+200B, U+202E, U+2066 and U+FEFF) and every lone surrogate is written as `\uXXXX`, a format character outside the basic plane as the `\uXXXX` of each of its two halves, and text longer than [MAX_UNTRUSTED_TEXT] characters once escaped is cut to that length, its last character an ellipsis.
 */
internal fun String.escapedForMessage(): String {
    val escaped = StringBuilder()
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        val character = String(Character.toChars(codePoint))
        val piece = if (codePoint.isUnsafeInMessage()) character.toCharArray().joinToString("") { "\\u%04X".format(it.code) } else character
        // An escape or a character outside the basic plane is kept whole or left out whole, so the cut never leaves half of one behind.
        if (escaped.length + piece.length > MAX_UNTRUSTED_TEXT || (escaped.length + piece.length == MAX_UNTRUSTED_TEXT && index + Character.charCount(codePoint) < length)) {
            return escaped.append(ELLIPSIS).toString()
        }
        escaped.append(piece)
        index += Character.charCount(codePoint)
    }
    return escaped.toString()
}

/**
 * Returns whether this text holds a character [escapedForMessage] escapes.
 */
internal fun String.holdsControlCharacter(): Boolean = codePoints().anyMatch { it.isUnsafeInMessage() }

// A line break, a carriage return or an escape sequence would let text of a file start a line of its own in a log or a terminal, where it could pass for a line the engine wrote; a format character, such as a bidirectional override or a zero width space, starts no line but makes a terminal show text reversed or hidden inside a line the engine wrote; a lone surrogate cannot be encoded at all.
private fun Int.isUnsafeInMessage(): Boolean =
    this < ' '.code || this in DEL..LAST_C1 || this == LINE_SEPARATOR || this == PARAGRAPH_SEPARATOR || this in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code || Character.getType(this) == Character.FORMAT.toInt()

/** The longest a text [escapedForMessage] returns may be, in characters. */
internal const val MAX_UNTRUSTED_TEXT = 160

private const val ELLIPSIS = '…'
private const val DEL = 0x7F
private const val LAST_C1 = 0x9F
private const val LINE_SEPARATOR = 0x2028
private const val PARAGRAPH_SEPARATOR = 0x2029
