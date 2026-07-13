package cz.cleanship.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class CalculatorTest {

    private lateinit var calculator: Calculator

    @BeforeEach
    fun setUp() {
        calculator = Calculator()
    }

    @Test
    fun `should add two numbers`() {
        // given
        val a = 1
        val b = 2
        // when
        val result = calculator.add(a, b)
        // then
        assertThat(result).isEqualTo(3)
    }
}
