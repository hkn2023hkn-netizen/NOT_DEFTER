package com.hakan.notdefteri.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val PadH = 16.dp
private val PadV = 8.dp

/**
 * Çizgili kağıt üzerinde yazı alanı. Çizgiler metnin gerçek satır yerleşiminden hesaplanır,
 * böylece her satırın yazısı tam çizginin üstüne oturur; boş alanda çizgiler aynı aralıkla devam eder.
 */
@Composable
fun LinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    paper: PaperColors,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minHeight: Dp = 0.dp,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle.copy(color = paper.ink),
        singleLine = singleLine,
        cursorBrush = SolidColor(paper.ink),
        onTextLayout = { layout = it },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .drawBehind {
                val l = layout ?: return@drawBehind
                val top = PadV.toPx()
                val gap = 5.dp.toPx() // yazının altı ile çizgi arasındaki boşluk
                val stroke = 1.dp.toPx()
                val lineCount = l.lineCount
                val step = if (lineCount > 0) l.getLineBottom(0) - l.getLineTop(0) else textStyle.fontSize.toPx() * 1.6f
                var y = 0f
                for (i in 0 until lineCount) {
                    y = top + l.getLineBaseline(i) + gap
                    drawLine(paper.line, Offset(0f, y), Offset(size.width, y), stroke)
                }
                if (!singleLine) {
                    y += step
                    while (y < size.height) {
                        drawLine(paper.line, Offset(0f, y), Offset(size.width, y), stroke)
                        y += step
                    }
                }
            },
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = PadH, vertical = PadV)) {
                if (value.text.isEmpty()) Text(placeholder, style = textStyle.copy(color = paper.inkSoft.copy(alpha = 0.6f)))
                inner()
            }
        }
    )
}

@Composable
fun LinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    paper: PaperColors,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    // Seçim/imleç bilgisini korumak için içerde TextFieldValue tutulur
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    if (tfv.text != value) tfv = tfv.copy(text = value)
    LinedTextField(
        value = tfv,
        onValueChange = { tfv = it; if (it.text != value) onValueChange(it.text) },
        placeholder = placeholder, textStyle = textStyle, paper = paper, modifier = modifier, singleLine = singleLine,
    )
}
