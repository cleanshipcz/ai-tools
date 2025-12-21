package cz.cleanship.telemetry.logging

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.spi.LoggingEventBuilder

class Slf4jExtensionsTest {

    @Test
    fun `info extension logs message with key-value fields`() {
        // given
        val builder = mockk<LoggingEventBuilder>(relaxed = true)
        val log = mockk<Logger>()
        every { log.atInfo() } returns builder

        // when
        log.info(message = "hello", fields = mapOf("k" to 123, "s" to "v"))

        // then
        verify { builder.addKeyValue("k", 123) }
        verify { builder.addKeyValue("s", "v") }
        verify { builder.log("hello") }
    }

    @Test
    fun `error extension includes message and fields`() {
        // given
        val builder = mockk<LoggingEventBuilder>(relaxed = true)
        val log = mockk<Logger>()
        every { log.atError() } returns builder
        val ex = IllegalStateException("boom")

        // when
        log.error(message = "failed", fields = mapOf("op" to "test"), t = ex)

        // then
        verify { builder.addKeyValue("op", "test") }
        verify { builder.setCause(ex) }
        verify { builder.log("failed") }
    }

    @Test
    fun `warn extension logs message with key-value fields`() {
        // given
        val builder = mockk<LoggingEventBuilder>(relaxed = true)
        val log = mockk<Logger>()
        every { log.atWarn() } returns builder

        // when
        log.warn(message = "heads-up", fields = mapOf("k" to 1))

        // then
        verify { builder.addKeyValue("k", 1) }
        verify { builder.log("heads-up") }
    }

    @Test
    fun `debug extension logs message with key-value fields`() {
        // given
        val builder = mockk<LoggingEventBuilder>(relaxed = true)
        val log = mockk<Logger>()
        every { log.atDebug() } returns builder

        // when
        log.debug(message = "details", fields = mapOf("a" to "b"))

        // then
        verify { builder.addKeyValue("a", "b") }
        verify { builder.log("details") }
    }
}
