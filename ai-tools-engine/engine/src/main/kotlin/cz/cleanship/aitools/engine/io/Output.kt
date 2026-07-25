package cz.cleanship.aitools.engine.io

import java.io.Closeable

interface Output : AutoCloseable, Closeable {

    fun appendText(text: String)

    fun appendLine(line: String = "")

    /**
     * Composed from the [appendText] and [appendLine] primitives so that a decorating [Output] only has to
     * override those two to observe or transform everything that is written.
     */
    fun appendTextTopic(header: String, content: String?) {
        if (content != null) {
            appendLine(header)
            appendLine()
            appendText(content)
            appendLine()
        }
    }

    /**
     * Composed from the [appendLine] primitive, see [appendTextTopic].
     */
    fun appendListTopic(header: String, items: List<String>?) {
        if (items?.isNotEmpty() == true) {
            appendLine(header)
            appendLine()
            for (item in items) {
                val indentedItemLines = item.lines().joinToString("\n  ")
                appendLine("- $indentedItemLines")
            }
            appendLine()
        }
    }
}
