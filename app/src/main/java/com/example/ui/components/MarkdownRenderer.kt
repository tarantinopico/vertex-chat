package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledSurfaceLowest
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueLight
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

/**
 * Rychlý nativní Compose Markdown renderer optimalizovaný pro streamované AI odpovědi.
 * Podporuje bloky kódu (s kopírováním), inline kód, tučné písmo, kurzívu, nadpisy, odrážky a citace.
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = parseInlineMarkdown(block.text),
                            style = style,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    is MarkdownBlock.CodeBlock -> {
                        CodeBlockCard(language = block.language, code = block.code)
                    }

                    is MarkdownBlock.BulletItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                color = ElectricBlueLight,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp, start = 4.dp)
                            )
                            Text(
                                text = parseInlineMarkdown(block.text),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                    }

                    is MarkdownBlock.NumberedItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${block.number}.",
                                color = ElectricBlueLight,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(end = 8.dp, start = 2.dp, top = 2.dp)
                            )
                            Text(
                                text = parseInlineMarkdown(block.text),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                    }

                    is MarkdownBlock.Quote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF111827))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(ElectricBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parseInlineMarkdown(block.text),
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = TextSecondary
                            )
                        }
                    }

                    is MarkdownBlock.Divider -> {
                        HorizontalDivider(color = AmoledBorder, thickness = 1.dp)
                    }

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = parseInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    if (copied) {
        LaunchedEffect(Unit) {
            delay(1800)
            copied = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AmoledBorder, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = AmoledSurfaceLowest
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricBlueLight
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Kopírovat kód",
                        tint = if (copied) EmeraldSuccess else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            HorizontalDivider(color = AmoledBorder, thickness = 1.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

internal fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.lines()
    val result = mutableListOf<MarkdownBlock>()

    var inCodeBlock = false
    var codeLang = ""
    val codeBuffer = StringBuilder()
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            result.add(MarkdownBlock.Paragraph(paragraphBuffer.toString().trimEnd()))
            paragraphBuffer.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (!inCodeBlock) {
                flushParagraph()
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
                codeBuffer.clear()
            } else {
                result.add(MarkdownBlock.CodeBlock(codeLang, codeBuffer.toString().trimEnd()))
                inCodeBlock = false
                codeLang = ""
                codeBuffer.clear()
            }
            continue
        }

        if (inCodeBlock) {
            if (codeBuffer.isNotEmpty()) codeBuffer.append('\n')
            codeBuffer.append(line)
            continue
        }

        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                result.add(MarkdownBlock.Divider)
            }
            trimmed.startsWith("### ") -> {
                flushParagraph()
                result.add(MarkdownBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                result.add(MarkdownBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                result.add(MarkdownBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                result.add(MarkdownBlock.BulletItem(trimmed.substring(2).trim()))
            }
            trimmed.startsWith("> ") -> {
                flushParagraph()
                result.add(MarkdownBlock.Quote(trimmed.removePrefix("> ").trim()))
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                flushParagraph()
                val num = trimmed.substringBefore('.')
                val rest = trimmed.substringAfter('.').trim()
                result.add(MarkdownBlock.NumberedItem(num, rest))
            }
            else -> {
                if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append('\n')
                paragraphBuffer.append(line)
            }
        }
    }

    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        result.add(MarkdownBlock.CodeBlock(codeLang, codeBuffer.toString()))
    }
    flushParagraph()
    return result
}

@Composable
internal fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val n = text.length
        while (i < n) {
            when {
                // Inline code `...`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                background = Color(0xFF1E293B),
                                color = Color(0xFF93C5FD),
                                fontSize = 13.sp
                            )
                        ) {
                            append(" ")
                            append(text.substring(i + 1, end))
                            append(" ")
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Bold **...**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic *...*
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
