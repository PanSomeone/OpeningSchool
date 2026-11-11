package com.openschedule.core.data

import com.openschedule.core.plugin.PluginHomeWidget
import com.openschedule.core.plugin.PluginViewNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginViewValidatorTest {

    @Test
    fun coercesWidgetFieldsIntoRange() {
        val widget = PluginHomeWidget(
            id = "  counter ",
            title = "标题",
            columns = 9,
            rows = 0,
            view = PluginViewNode(text = "hi"),
        )
        val sanitized = PluginViewValidator.sanitize(widget)
        assertEquals("counter", sanitized.id)
        assertEquals(2, sanitized.columns)
        assertEquals(1, sanitized.rows)
        assertEquals("hi", sanitized.view.text)
    }

    @Test
    fun degradesUnknownNodeTypeToText() {
        val widget = PluginHomeWidget(view = PluginViewNode(type = "marquee", text = "滚动"))
        val sanitized = PluginViewValidator.sanitize(widget)
        assertEquals(PluginViewNode().type, sanitized.view.type)
    }

    @Test
    fun dropsChildrenOfLeafNodes() {
        val widget = PluginHomeWidget(
            view = PluginViewNode(
                type = "text",
                text = "x",
                children = listOf(PluginViewNode(text = "child")),
            ),
        )
        val sanitized = PluginViewValidator.sanitize(widget)
        assertTrue(sanitized.view.children.isEmpty())
    }

    @Test
    fun capsNodeCount() {
        fun deep(depth: Int): PluginViewNode = if (depth == 0) {
            PluginViewNode(text = "leaf")
        } else {
            PluginViewNode(type = "column", children = List(4) { deep(depth - 1) })
        }
        val widget = PluginHomeWidget(view = deep(6))
        fun count(node: PluginViewNode): Int = 1 + node.children.sumOf(::count)
        val sanitized = PluginViewValidator.sanitize(widget)
        assertTrue(count(sanitized.view) <= PluginViewValidator.MAX_NODES)
    }

    @Test
    fun stripsUnsafeActionNames() {
        val widget = PluginHomeWidget(
            view = PluginViewNode(type = "button", text = "ok", action = "do it!"),
        )
        val sanitized = PluginViewValidator.sanitize(widget)
        assertEquals("doit", sanitized.view.action)
    }

    @Test
    fun normalizesUnknownEmphasisAndSize() {
        val widget = PluginHomeWidget(
            view = PluginViewNode(
                emphasis = "neon",
                size = "giant",
            ),
        )
        val sanitized = PluginViewValidator.sanitize(widget)
        assertEquals(PluginViewNode.EMPHASIS_NONE, sanitized.view.emphasis)
        assertEquals(PluginViewNode.SIZE_MEDIUM, sanitized.view.size)
    }
}
