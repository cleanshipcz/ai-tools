package cz.cleanship.aitools.engine.utils

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.io.OutputStreamOutput
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class StringOutput(
    private val outputStream: OutputStream = ByteArrayOutputStream(),
) : Output by OutputStreamOutput(outputStream) {
    fun getContent(): String = outputStream.toString()
}
