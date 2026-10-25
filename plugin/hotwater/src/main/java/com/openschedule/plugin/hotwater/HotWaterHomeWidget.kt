package com.openschedule.plugin.hotwater

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HotWaterHomeWidget(
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val viewModel = rememberHotWaterViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < 170.dp
        val statusText = when {
            !state.signedIn -> "未登录"
            state.devices.isEmpty() -> "还没有设备"
            else -> state.statusLine
        }
        val detailText = when {
            !state.signedIn -> "点击登录"
            state.devices.isEmpty() -> "点击添加"
            state.needsSetup -> "点击设置"
            else -> state.selected?.displayName ?: state.accountName.orEmpty()
        }
        val compactText = when {
            !state.signedIn -> "点击登录"
            state.devices.isEmpty() -> "点击添加"
            state.needsSetup -> listOf(state.statusLine, "点击设置")
                .filter { it.isNotBlank() }
                .joinToString(" · ")

            else -> listOf(statusText, detailText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onOpen)
                .padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                )
                Text(
                    text = "热水",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            val canToggle = state.signedIn && state.devices.isNotEmpty()
            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = compactText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.signedIn && state.isRunning) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (canToggle) {
                        PowerAction(
                            running = state.isRunning,
                            busy = state.isBusy,
                            compact = true,
                            onToggle = viewModel::toggleWater,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.isRunning) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (canToggle) {
                        PowerAction(
                            running = state.isRunning,
                            busy = state.isBusy,
                            compact = false,
                            onToggle = viewModel::toggleWater,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerAction(
    running: Boolean,
    busy: Boolean,
    compact: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(
        onClick = { if (!busy) onToggle() },
        enabled = !busy,
        modifier = Modifier.size(if (compact) 32.dp else 40.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = if (running) "关闭热水" else "打开热水",
                tint = if (running) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(if (compact) 18.dp else 24.dp),
            )
        }
    }
}
