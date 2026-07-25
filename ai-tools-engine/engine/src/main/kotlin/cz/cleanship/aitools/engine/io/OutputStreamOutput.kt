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

    override fun close() {
        writer.flush()
        writer.close()
    }
}
