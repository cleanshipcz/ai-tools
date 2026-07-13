package cz.cleanship.telemetry.logging

import io.opentelemetry.context.Context
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpanAttributesContextTest {

    @Test
    fun `should return empty map when no attributes in context`() {
        // given
        // - no attributes set in context

        // when
        val result = SpanAttributesContext.current()

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `should store and retrieve attributes from context`() {
        // given
        val attributes = mapOf("key1" to "value1", "key2" to 42)
        val contextWithAttrs = SpanAttributesContext.withAttributes(Context.root(), attributes)

        // when
        val result = contextWithAttrs.makeCurrent().use {
            SpanAttributesContext.current()
        }

        // then
        assertThat(result).containsEntry("key1", "value1")
        assertThat(result).containsEntry("key2", 42)
    }

    @Test
    fun `should merge attributes with parent context`() {
        // given
        val parentAttrs = mapOf("parent_key" to "parent_value", "shared_key" to "parent")
        val parentContext = SpanAttributesContext.withAttributes(Context.root(), parentAttrs)

        val childAttrs = mapOf("child_key" to "child_value", "shared_key" to "child")

        // when
        val mergedContext = SpanAttributesContext.withAttributes(parentContext, childAttrs)
        val result = mergedContext.makeCurrent().use {
            SpanAttributesContext.current()
        }

        // then
        // - parent-only attribute preserved
        assertThat(result).containsEntry("parent_key", "parent_value")
        // - child-only attribute present
        assertThat(result).containsEntry("child_key", "child_value")
        // - shared key overridden by child (deepest wins)
        assertThat(result).containsEntry("shared_key", "child")
    }

    @Test
    fun `should preserve all parent attributes when merging with empty child`() {
        // given
        val parentAttrs = mapOf("key1" to "value1", "key2" to "value2")
        val parentContext = SpanAttributesContext.withAttributes(Context.root(), parentAttrs)

        // when
        val mergedContext = SpanAttributesContext.withAttributes(parentContext, emptyMap())
        val result = mergedContext.makeCurrent().use {
            SpanAttributesContext.current()
        }

        // then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(parentAttrs)
    }

    @Test
    fun `should handle multiple nesting levels`() {
        // given
        val level1 = mapOf("level" to "1", "l1_only" to "l1")
        val level2 = mapOf("level" to "2", "l2_only" to "l2")
        val level3 = mapOf("level" to "3", "l3_only" to "l3")

        val ctx1 = SpanAttributesContext.withAttributes(Context.root(), level1)
        val ctx2 = SpanAttributesContext.withAttributes(ctx1, level2)
        val ctx3 = SpanAttributesContext.withAttributes(ctx2, level3)

        // when
        val result = ctx3.makeCurrent().use {
            SpanAttributesContext.current()
        }

        // then
        assertThat(result).containsEntry("level", "3")
        assertThat(result).containsEntry("l1_only", "l1")
        assertThat(result).containsEntry("l2_only", "l2")
        assertThat(result).containsEntry("l3_only", "l3")
    }

    @Test
    fun `should handle various attribute types`() {
        // given
        val attributes = mapOf(
            "string" to "hello",
            "int" to 42,
            "long" to 123L,
            "double" to 3.14,
            "boolean" to true,
            "null_value" to null,
        )
        val context = SpanAttributesContext.withAttributes(Context.root(), attributes)

        // when
        val result = context.makeCurrent().use {
            SpanAttributesContext.current()
        }

        // then
        assertThat(result).containsEntry("string", "hello")
        assertThat(result).containsEntry("int", 42)
        assertThat(result).containsEntry("long", 123L)
        assertThat(result).containsEntry("double", 3.14)
        assertThat(result).containsEntry("boolean", true)
        assertThat(result).containsEntry("null_value", null)
    }
}
