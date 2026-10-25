package com.openschedule.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openschedule.core.data.Announcement
import com.openschedule.core.data.AnnouncementStore
import com.openschedule.core.ui.EmptyState
import com.openschedule.core.ui.LocalFloatingNavSpace
import com.openschedule.core.ui.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun AnnouncementDialog(
    announcement: Announcement,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(announcement.title)
                if (announcement.date.isNotBlank()) {
                    Text(
                        text = announcement.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = announcement.content.ifBlank { "暂无内容" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

@Composable
fun AnnouncementHistoryScreen(
    store: AnnouncementStore,
    onBack: () -> Unit,
) {
    val announcements by store.announcements.collectAsStateWithLifecycle()
    val readIds by store.readIds.collectAsStateWithLifecycle()
    val refreshing by store.refreshing.collectAsStateWithLifecycle()
    val unreadCount = announcements.count { it.id !in readIds }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<Announcement?>(null) }

    LaunchedEffect(Unit) {
        val ok = store.refresh()
        if (!ok && announcements.isEmpty()) {
            snackbar.showSnackbar("暂时无法获取公告")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "公告",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { store.refresh() } },
                        enabled = !refreshing,
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新公告")
                        }
                    }
                },
            )

            if (announcements.isEmpty()) {
                if (!refreshing) {
                    EmptyState(
                        icon = Icons.Filled.Campaign,
                        title = "暂无公告",
                        hint = "新的更新说明会显示在这里",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = LocalFloatingNavSpace.current + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Text(
                                text = if (unreadCount > 0) "$unreadCount 条未读" else "全部已读",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (unreadCount > 0) {
                                TextButton(onClick = { store.markAllRead() }) {
                                    Text("全部已读")
                                }
                            }
                        }
                    }
                    items(announcements, key = { it.id }) { announcement ->
                        AnnouncementCard(
                            announcement = announcement,
                            unread = announcement.id !in readIds,
                            onClick = {
                                store.markRead(announcement.id)
                                selected = announcement
                            },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalFloatingNavSpace.current + 16.dp),
        )
    }

    selected?.let { announcement ->
        AnnouncementDialog(
            announcement = announcement,
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    unread: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unread) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (announcement.date.isNotBlank()) {
                    Text(
                        text = announcement.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (announcement.content.isNotBlank()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = announcement.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
