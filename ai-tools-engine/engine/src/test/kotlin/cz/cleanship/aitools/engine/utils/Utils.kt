package cz.cleanship.aitools.engine.utils

import cz.cleanship.aitools.engine.tools.Printer

fun <T> getExpectedOutput(printer: Printer<T>, entity: T): String {
    val output = StringOutput()
    output.use {
        printer.print(entity, output)
    }
    return output.getContent()
}
