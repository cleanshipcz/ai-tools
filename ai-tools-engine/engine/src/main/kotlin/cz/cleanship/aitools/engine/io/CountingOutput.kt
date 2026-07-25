package cz.cleanship.aitools.engine.io

/**
 * Forwards every write to [delegate] unchanged while counting the characters that pass through.
 *
 * A line break counts as a single character regardless of the platform line separator, so the count reflects
 * the logical length of the generated document. Useful for checking a document against a size limit imposed
 * by the consuming tool without buffering it in memory.
 */
class CountingOutput(
    private val delegate: Output,
) : Output {

    var characterCount: Int = 0
        private set

    override fun appendText(text: String) {
        characterCount += text.length + 1
        delegate.appendText(text)
    }

    override fun appendLine(line: String) {
        characterCount += line.length + 1
        delegate.appendLine(line)
    }

    override fun close() = delegate.close()
}
