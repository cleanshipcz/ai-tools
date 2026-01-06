package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output

fun interface Printer<T> {
    fun print(entity: T, output: Output): Output
}
