package cz.cleanship.aitools.engine.io

/**
 * Drops everything written to it.
 *
 * A dry run renders every artifact through this output: the printer runs to completion, so every reference it
 * resolves is resolved and every failure it would raise is raised, while the text it produces goes nowhere. It is
 * an object because it carries no state, and closing it is a no-op for the same reason.
 */
object DiscardingOutput : Output {

    override fun appendText(text: String) = Unit

    override fun appendLine(line: String) = Unit

    override fun close() = Unit
}
