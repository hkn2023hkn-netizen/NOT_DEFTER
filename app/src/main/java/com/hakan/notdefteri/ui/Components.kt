package com.hakan.notdefteri.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Sarı not kağıdı görünümü: hafif gölge, isteğe bağlı çizgili zemin, üstte koyu sarı şerit. */
fun Modifier.yellowPaper(paper: PaperColors, lineSpacingPx: Float = 0f): Modifier = this
    .shadow(3.dp, RoundedCornerShape(4.dp))
    .clip(RoundedCornerShape(4.dp))
    .background(paper.paper)
    .drawBehind {
        drawRect(paper.strip, size = size.copy(height = 6.dp.toPx()))
        if (lineSpacingPx > 0f) {
            var y = lineSpacingPx + 6.dp.toPx()
            while (y < size.height) {
                drawLine(paper.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += lineSpacingPx
            }
        }
    }

/** İnce, küçük arama çubuğu. */
@Composable
fun CompactSearch(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Ara", fontSize = 14.sp, color = colors.onSurfaceVariant)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, "Temizle",
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onChange("") },
                tint = colors.onSurfaceVariant
            )
        }
    }
}

/** Parola isteyen pencere. onSubmit false dönerse "Parola yanlış" gösterilir. */
@Composable
fun PasswordDialog(
    title: String,
    message: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Boolean,
) {
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) Text(message, Modifier.padding(bottom = 8.dp))
                PasswordField(pw, { pw = it; error = false }, "Parola", error)
                if (error) Text("Parola yanlış", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { if (!onSubmit(pw)) error = true }) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

/** Parola alanı: yalnızca rakam kabul eder, rakam klavyesi açılır. */
@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { c -> c in '0'..'9' }) }, label = { Text(label) }, singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun FullScreenCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
