package com.openschedule.plugin.scores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ScoresHomeWidget(
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val viewModel = rememberScoresViewModel()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val result = remember(courses) { GpaCalculator.calculate(courses) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onOpen)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "GPA",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (result.courseCount > 0) {
                String.format("%.3f", result.gpa)
            } else {
                "—"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (result.courseCount > 0) {
                val credits = if (result.credits % 1.0 == 0.0) {
                    result.credits.toInt().toString()
                } else {
                    String.format("%.1f", result.credits)
                }
                "${result.courseCount} 门 · $credits 学分"
            } else {
                "点击去算分"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
