package com.openschedule.core.data

import com.openschedule.core.plugin.PluginHomeWidget
import com.openschedule.core.plugin.PluginScreen
import com.openschedule.core.plugin.PluginViewNode
import com.openschedule.core.plugin.PluginViewTypes

/**
 * Sanitizes declarative views and home widgets coming from plugin scripts.
 * Everything a module registers is coerced into the small, whitelisted shape
 * the host renderer understands; unknown types, oversized text, unknown
 * emphasis/size keys and over-deep trees are silently degraded rather than
 * trusted.
 */
object PluginViewValidator {

    const val MAX_DEPTH = 8
    const val MAX_NODES = 64
    const val MAX_TEXT = 200
    const val MAX_TITLE = 40
    const val MAX_CHILDREN = 16

    private val EMPHASIS = setOf(
        PluginViewNode.EMPHASIS_NONE,
        PluginViewNode.EMPHASIS_PRIMARY,
        PluginViewNode.EMPHASIS_SECONDARY,
        PluginViewNode.EMPHASIS_ERROR,
    )
    private val SIZES = setOf(
        PluginViewNode.SIZE_SMALL,
        PluginViewNode.SIZE_MEDIUM,
        PluginViewNode.SIZE_LARGE,
        PluginViewNode.SIZE_TITLE,
    )

    fun sanitize(widget: PluginHomeWidget): PluginHomeWidget {
        val counter = NodeCounter()
        return widget.copy(
            id = widget.id.trim()
                .filter { it.isLetterOrDigit() || it in "._-" }
                .take(32)
                .ifBlank { "widget" },
            title = widget.title.trim().take(MAX_TITLE),
            iconKey = widget.iconKey.trim().take(32).ifBlank { "extension" },
            columns = widget.columns.coerceIn(1, 2),
            rows = widget.rows.coerceIn(1, 2),
            view = sanitizeNode(widget.view, 0, counter),
        )
    }

    fun sanitize(screen: PluginScreen): PluginScreen {
        val counter = NodeCounter()
        return screen.copy(
            title = screen.title.trim().take(MAX_TITLE),
            iconKey = screen.iconKey.trim().take(32).ifBlank { "extension" },
            view = sanitizeNode(screen.view, 0, counter),
        )
    }

    private fun sanitizeNode(node: PluginViewNode, depth: Int, counter: NodeCounter): PluginViewNode {
        counter.count++
        val rawType = node.type.trim().lowercase().take(16)
        val type = if (rawType in PluginViewTypes.ALL) rawType else PluginViewTypes.TEXT
        val children = if (type in PluginViewTypes.CONTAINERS && depth < MAX_DEPTH) {
            buildList {
                for (child in node.children.take(MAX_CHILDREN)) {
                    if (counter.count >= MAX_NODES) break
                    add(sanitizeNode(child, depth + 1, counter))
                }
            }
        } else {
            emptyList()
        }
        return PluginViewNode(
            type = type,
            text = node.text.take(MAX_TEXT),
            icon = node.icon.trim().take(32),
            emphasis = node.emphasis.trim().lowercase().take(12)
                .let { if (it in EMPHASIS) it else PluginViewNode.EMPHASIS_NONE },
            size = node.size.trim().lowercase().take(12)
                .let { if (it in SIZES) it else PluginViewNode.SIZE_MEDIUM },
            action = node.action.trim()
                .filter { it.isLetterOrDigit() || it in "._-" }
                .take(64),
            spacing = node.spacing.coerceIn(0, 24),
            children = children,
        )
    }

    private class NodeCounter {
        var count = 0
    }
}
