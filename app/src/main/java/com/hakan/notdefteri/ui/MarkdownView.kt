package com.hakan.notdefteri.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakan.notdefteri.data.Markdown
import com.hakan.notdefteri.data.MdBlock

private val CodeBg = Color(0xFF263238)
private val CodeInk = Color(0xFFECEFF1)

fun mdAnnotated(text: String, ink: Color): AnnotatedString = buildAnnotatedString {
    for (s in Markdown.spans(text)) {
        val style = SpanStyle(
            color = if (s.link != null) Color(0xFF1565C0) else ink,
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            fontFamily = if (s.code) FontFamily.Monospace else null,
            background = if (s.code) Color(0x22000000) else Color.Unspecified,
            textDecoration = when {
                s.strike && s.link != null -> TextDecoration.combine(listOf(TextDecoration.LineThrough, TextDecoration.Underline))
                s.strike -> TextDecoration.LineThrough
                s.link != null -> TextDecoration.Underline
                else -> null
            }
        )
        withStyle(style) { append(s.text) }
    }
}

/** Markdown içeriği biçimli gösterir. onToggleCheck verilirse onay kutuları tıklanabilir. */
@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 17,
    onToggleCheck: ((Int) -> Unit)? = null,
) {
    val paper = LocalPaper.current
    val ink = paper.ink
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (b in Markdown.parse(text)) {
            when (b) {
                is MdBlock.Heading -> Text(
                    mdAnnotated(b.text, ink),
                    fontSize = when (b.level) { 1 -> (fontSize + 9).sp; 2 -> (fontSize + 5).sp; else -> (fontSize + 2).sp },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
                is MdBlock.Paragraph -> Text(mdAnnotated(b.text, ink), fontSize = fontSize.sp, lineHeight = (fontSize + 9).sp)
                is MdBlock.Bullet -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text("•  ", fontSize = fontSize.sp, color = ink)
                    Text(mdAnnotated(b.text, ink), fontSize = fontSize.sp)
                }
                is MdBlock.Numbered -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text("${b.number}.  ", fontSize = fontSize.sp, color = ink)
                    Text(mdAnnotated(b.text, ink), fontSize = fontSize.sp)
                }
                is MdBlock.Check -> Row(
                    Modifier.padding(start = (b.indent * 16).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = b.checked,
                        onCheckedChange = if (onToggleCheck != null) { _ -> onToggleCheck(b.lineIndex) } else null,
                        colors = CheckboxDefaults.colors(uncheckedColor = paper.inkSoft, checkedColor = Navy),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        mdAnnotated(b.text, if (b.checked) Color.Gray else ink), fontSize = fontSize.sp,
                        textDecoration = if (b.checked) TextDecoration.LineThrough else null
                    )
                }
                is MdBlock.Quote -> Row(Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.width(3.dp).height((fontSize + 9).dp).background(paper.strip))
                    Spacer(Modifier.width(8.dp))
                    Text(mdAnnotated(b.text, paper.inkSoft), fontSize = fontSize.sp, fontStyle = FontStyle.Italic)
                }
                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CodeBg)
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(b.code, fontFamily = FontFamily.Monospace, fontSize = (fontSize - 3).sp, color = CodeInk, softWrap = false)
                }
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 6.dp), color = paper.inkSoft)
                MdBlock.Blank -> Spacer(Modifier.height(8.dp))
            }
        }
    }
}
