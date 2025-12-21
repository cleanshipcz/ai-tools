package cz.cleanship.aitools.engine.tools

import java.io.BufferedWriter
import java.io.Closeable
import java.io.OutputStream
import java.io.OutputStreamWriter

class Output(
    outputStream: OutputStream
) : Closeable {

    private val writer = BufferedWriter(OutputStreamWriter(outputStream))

    fun appendText(text: String) {
        writer.write(text)
        writer.newLine()
    }

    fun appendLine(line: String = "") {
        writer.appendLine(line)
    }

    override fun close() {
        writer.flush()
        writer.close()
    }
}
