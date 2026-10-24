package com.openschedule.feature.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.openschedule.core.model.TimetableImporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    importers: List<TimetableImporter>,
    loading: Boolean,
    importing: Boolean,
    onRefresh: () -> Unit,
    onImport: (TimetableImporter, Map<String, String>) -> Unit,
    onOpenWeb: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<TimetableImporter?>(null) }
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var customUrlOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val importer = selected
            if (importer == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "导入课表",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRefresh, enabled = !loading && !importing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新扫描适配器")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                Hint("从学校官网导入")
                builtInSchoolPortals.forEach { portal ->
                    ImportChoice(
                        icon = Icons.Filled.School,
                        title = portal.name,
                        subtitle = portal.note,
                        enabled = !importing,
                    ) { onOpenWeb(portal.name, portal.url) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                ImportChoice(
                    icon = Icons.Filled.Language,
                    title = "自定义网址",
                    subtitle = "",
                    enabled = !importing,
                ) { customUrlOpen = true }
                Text(
                    text = "统一认证登录可能需要短信验证；登录并打开课表页后再点导入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                Hint("账号导入")
                if (loading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                if (importers.isEmpty() && !loading) {
                    Text(
                        text = "没有可用的账号导入方式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                importers.forEach { item ->
                    ImportChoice(
                        icon = Icons.Filled.School,
                        title = item.name,
                        subtitle = item.description,
                        enabled = !importing,
                    ) {
                        selected = item
                        values = item.configFields.associate { it.key to it.defaultValue }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        text = importer.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }

                importer.configFields.forEach { field ->
                    OutlinedTextField(
                        value = values[field.key] ?: "",
                        onValueChange = { values = values + (field.key to it) },
                        label = { Text(field.label) },
                        placeholder = if (field.placeholder.isBlank()) null else {
                            { Text(field.placeholder) }
                        },
                        singleLine = true,
                        visualTransformation = if (field.secret) {
                            PasswordVisualTransformation()
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onImport(importer, values) },
                    enabled = !importing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("导入", fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = { selected = null }, enabled = !importing) {
                    Text("换一个导入方式")
                }
            }
        }
    }

    if (customUrlOpen) {
        CustomUrlDialog(
            onOpen = { url ->
                customUrlOpen = false
                onOpenWeb("课表导入", url)
            },
            onDismiss = { customUrlOpen = false },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun ImportChoice(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomUrlDialog(
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("https://") }
    val normalized = remember(value) {
        val trimmed = value.trim()
        when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains('.') -> "https://$trimmed"
            else -> ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义网址") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("教务系统网址") },
                placeholder = { Text("https://jw.example.edu.cn") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onOpen(normalized) },
                enabled = normalized.isNotBlank(),
            ) { Text("打开") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
