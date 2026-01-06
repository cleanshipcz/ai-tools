package cz.cleanship.aitools.engine.io

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

class OutputStreamOutput(
    outputStream: OutputStream,
) : Output {

    private val writer = BufferedWriter(OutputStreamWriter(outputStream))

    override fun appendText(text: String) {
        writer.write(text)
        writer.newLine()
    }

    override fun appendLine(line: String) {
        writer.appendLine(line)
    }

    override fun appendTextTopic(header: String, content: String?) {
        if (content != null) {
            appendLine(header)
            appendLine()
            appendText(content)
            appendLine()
        }
    }

    override fun appendListTopic(header: String, items: List<String>?) {
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

    override fun close() {
        writer.flush()
        writer.close()
    }
}
