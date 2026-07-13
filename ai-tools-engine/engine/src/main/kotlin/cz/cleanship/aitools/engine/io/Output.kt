package cz.cleanship.aitools.engine.io

import java.io.Closeable

interface Output : AutoCloseable, Closeable {

    fun appendText(text: String)

    fun appendLine(line: String = "")

    fun appendTextTopic(header: String, content: String?)

    fun appendListTopic(header: String, items: List<String>?)
}
