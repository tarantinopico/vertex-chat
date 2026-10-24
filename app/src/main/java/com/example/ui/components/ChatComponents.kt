package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.ToolCallRecord
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledSurfaceElevated
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.SoftVioletBorder
import com.example.ui.theme.SoftVioletContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Animovaný indikátor "Přemýšlím…" s pulzujícími tečkami, dokud nedorazí první token.
 */
@Composable
fun ThinkingPulseIndicator(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "thinking_pulse")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3.99f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots_step"
    )
    val dots = ".".repeat(step.toInt().coerceIn(1, 3))

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SoftVioletContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("thinking_indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            tint = SoftViolet,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "Přemýšlím$dots",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFDDD6FE)
        )
    }
}

/**
 * Rozbalovací panel nad odpovědí zobrazující Thought Summary modelu (ThinkingConfig includeThoughts = true).
 */
@Composable
fun ThoughtSummaryAccordion(
    thoughtSummary: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    if (thoughtSummary.isBlank()) return
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("thought_summary_panel"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SoftVioletContainer),
        border = BorderStroke(1.dp, SoftVioletBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = SoftViolet,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.thought_summary_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFDDD6FE)
                    )
                    if (isStreaming) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(SoftViolet)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Sbalit myšlenky" else "Rozbalit myšlenky",
                    tint = Color(0xFFC4B5FD)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    HorizontalDivider(color = SoftVioletBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = thoughtSummary.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDDD6FE),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * Karta vykonání VFS nástroje (functionCall) v chatu s ikonou, stavem a akčními tlačítky Zobrazit a Stáhnout.
 */
@Composable
fun ToolUseCard(
    record: ToolCallRecord,
    onViewFile: (String) -> Unit,
    onDownloadFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isFileTarget = record.toolName in listOf("writeFile", "readFile", "moveFile", "renameFile")
    val icon = when (record.toolName) {
        "createFolder" -> Icons.Default.CreateNewFolder
        "writeFile", "readFile" -> Icons.Default.Description
        "listFiles" -> Icons.Default.FolderOpen
        "deleteFile" -> Icons.Default.DeleteOutline
        "moveFile", "renameFile" -> Icons.AutoMirrored.Filled.DriveFileMove
        else -> Icons.Default.Description
    }

    val statusColor = when (record.status) {
        "SUCCESS" -> EmeraldSuccess
        "ERROR" -> CrimsonError
        else -> ElectricBlueLight
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tool_use_card_${record.toolName}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AmoledSurfaceElevated),
        border = BorderStroke(1.dp, AmoledBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF172554)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = record.toolName,
                        tint = ElectricBlueLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.actionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = record.targetPath,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.5.sp,
                        color = ElectricBlueLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (record.details.isNotBlank()) {
                        Text(
                            text = record.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                when (record.status) {
                    "RUNNING" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ElectricBlue
                        )
                    }
                    "SUCCESS" -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Dokončeno",
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Chyba",
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (record.status == "SUCCESS" && isFileTarget && record.targetPath != "/") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onViewFile(record.targetPath) },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("tool_card_view_btn"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_view),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = { onDownloadFile(record.targetPath) },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("tool_card_download_btn"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ElectricBlueLight
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_download),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
