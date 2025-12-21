package cz.cleanship.aitools.engine.tools

fun interface Printer<T> {
    fun print(entity: T, output: Output): Output;
}
