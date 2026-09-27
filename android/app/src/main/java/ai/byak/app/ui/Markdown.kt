package ai.byak.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
}

/** Minimal block parser for the Markdown models usually return: headings, lists, fenced code and paragraphs. */
fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>(); val paragraph = StringBuilder()
    fun flush() { if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim()); paragraph.clear() }
    val lines = source.lines(); var i = 0
    while (i < lines.size) {
        val line = lines[i]; val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flush(); val language = trimmed.removePrefix("```").trim(); val code = StringBuilder(); i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.appendLine(lines[i]); i++ }
                blocks += MdBlock.Code(language, code.toString().trimEnd())
            }
            Regex("^#{1,6}\\s").containsMatchIn(trimmed) -> { flush(); val level = trimmed.takeWhile { it == '#' }.length; blocks += MdBlock.Heading(level, trimmed.drop(level).trim()) }
            Regex("^[-*+]\\s").containsMatchIn(trimmed) -> { flush(); blocks += MdBlock.Bullet("•", trimmed.drop(2).trim()) }
            Regex("^\\d+[.)]\\s").containsMatchIn(trimmed) -> { flush(); val marker = trimmed.substringBefore(' '); blocks += MdBlock.Bullet(marker, trimmed.substringAfter(' ').trim()) }
            trimmed.isBlank() -> flush()
            else -> { if (paragraph.isNotEmpty()) paragraph.append('\n'); paragraph.append(line) }
        }
        i++
    }
    flush()
    return blocks
}

private val inline = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\((https?://[^)\s]+)\)|(?<![*\w])\*([^*\n]+)\*(?!\w)""")

fun inlineMarkdown(text: String, codeBackground: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in inline.findAll(text)) {
        append(text.substring(last, match.range.first))
        val (bold, code, label, url, italic) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, fontSize = 14.sp)) { append(code) }
            label.isNotEmpty() -> withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) { append(label) }
            italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

@Composable fun MarkdownText(content: String, modifier: Modifier = Modifier) {
    val blocks = remember(content) { parseMarkdown(content) }
    val codeBg = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f); val link = MaterialTheme.colorScheme.primary
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Paragraph -> Text(inlineMarkdown(block.text, codeBg, link))
                    is MdBlock.Heading -> Text(inlineMarkdown(block.text, codeBg, link), fontWeight = FontWeight.Bold, fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp })
                    is MdBlock.Bullet -> Row { Text(block.marker, Modifier.width(24.dp)); Text(inlineMarkdown(block.text, codeBg, link)) }
                    is MdBlock.Code -> Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)) {
                        Column(Modifier.padding(10.dp)) {
                            if (block.language.isNotBlank()) Text(block.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(block.code, Modifier.horizontalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 13.sp, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
