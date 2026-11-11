package com.openschedule.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openschedule.core.plugin.PluginViewNode
import com.openschedule.core.plugin.PluginViewTypes

/**
 * Native renderer for a plugin's declarative view tree. The tree is already
 * sanitized at the runtime boundary (whitelisted types, capped depth and text
 * lengths), so this layer only maps the small node vocabulary to Compose.
 */
@Composable
fun PluginViewContent(
    node: PluginViewNode,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (node.type) {
        PluginViewTypes.COLUMN -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(node.spacing.dp),
        ) {
            node.children.forEach { PluginViewContent(it, onAction) }
        }

        PluginViewTypes.ROW -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(node.spacing.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEach { PluginViewContent(it, onAction) }
        }

        PluginViewTypes.CARD -> Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(node.spacing.dp),
            ) {
                node.children.forEach { PluginViewContent(it, onAction) }
            }
        }

        PluginViewTypes.BUTTON -> Surface(
            modifier = modifier.clickable {
                if (node.action.isNotBlank()) onAction(node.action)
            },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (node.icon.isNotBlank()) {
                    Icon(
                        imageVector = NavIcons.icon(node.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(iconSize(node.size)),
                    )
                }
                if (node.text.isNotBlank()) {
                    Text(
                        text = node.text,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                node.children.forEach { PluginViewContent(it, onAction) }
            }
        }

        PluginViewTypes.ICON -> Icon(
            imageVector = NavIcons.icon(node.icon),
            contentDescription = node.text.ifBlank { null },
            tint = emphasisColor(node.emphasis),
            modifier = modifier.size(iconSize(node.size)),
        )

        PluginViewTypes.SPACER -> Spacer(modifier = modifier.height(node.spacing.coerceAtLeast(4).dp))

        PluginViewTypes.DIVIDER -> HorizontalDivider(modifier = modifier.fillMaxWidth())

        // TEXT and any unknown-but-sanitized node.
        else -> Text(
            text = node.text,
            style = textStyle(node.size),
            color = emphasisColor(node.emphasis),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

@Composable
private fun emphasisColor(emphasis: String) = when (emphasis) {
    PluginViewNode.EMPHASIS_PRIMARY -> MaterialTheme.colorScheme.primary
    PluginViewNode.EMPHASIS_SECONDARY -> MaterialTheme.colorScheme.onSurfaceVariant
    PluginViewNode.EMPHASIS_ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun textStyle(size: String) = when (size) {
    PluginViewNode.SIZE_SMALL -> MaterialTheme.typography.bodySmall
    PluginViewNode.SIZE_LARGE -> MaterialTheme.typography.bodyLarge
    PluginViewNode.SIZE_TITLE -> MaterialTheme.typography.titleLarge
    else -> MaterialTheme.typography.bodyMedium
}

private fun iconSize(size: String) = when (size) {
    PluginViewNode.SIZE_SMALL -> 16.dp
    PluginViewNode.SIZE_LARGE -> 24.dp
    PluginViewNode.SIZE_TITLE -> 28.dp
    else -> 20.dp
}
