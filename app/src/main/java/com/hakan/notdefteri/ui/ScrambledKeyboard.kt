package com.hakan.notdefteri.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.SecureRandom
import java.util.Locale

private val TR = Locale("tr")
private const val LETTERS = "abcçdefgğhıijklmnoöpqrsştuüvwxyz"
private const val DIGITS = "0123456789"
private const val SYMBOLS = ".,-_!?@#\$%&*+=/()[]:;'\"<>~^|\\{}`€£"

/**
 * Parola alanı: telefonun klavyesini açmaz. Dokununca bu alan seçilir ve yazı karışık klavyeden gelir.
 */
@Composable
fun SecretField(value: String, label: String, active: Boolean, onSelect: () -> Unit, isError: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = color, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(if (active) 2.dp else 1.dp, color, RoundedCornerShape(8.dp))
                .clickable(onClick = onSelect)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (visible) value else "•".repeat(value.length),
                fontSize = 20.sp, letterSpacing = if (visible) 0.sp else 3.sp,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Gizle" else "Göster")
            }
        }
    }
}

/**
 * Uygulamanın kendi parola klavyesi. Her açılışta tuşların yeri rastgele değişir; böylece ekranı izleyen biri
 * parmak hareketlerinden parolayı çıkaramaz, telefonun klavye uygulaması da parolayı hiç görmez.
 * Tuşa basınca renk değişmez (basılan tuş belli olmasın).
 */
@Composable
fun ScrambledKeyboard(onChar: (Char) -> Unit, onBackspace: () -> Unit) {
    val random = remember { SecureRandom() }
    val letterKeys = remember { (DIGITS + LETTERS).toList().shuffled(random) }
    val symbolKeys = remember { SYMBOLS.toList().shuffled(random) }
    var upper by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }

    val keys = if (symbols) symbolKeys else letterKeys.map { if (upper) it.toString().uppercase(TR).first() else it }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { c -> Key(c.toString(), Modifier.weight(1f)) { onChar(c) } }
                    repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Key(if (upper) "abc" else "ABC", Modifier.weight(1.4f), bold = upper, enabled = !symbols) { upper = !upper }
                Key(if (symbols) "abc" else "#+=", Modifier.weight(1.4f), bold = symbols) { symbols = !symbols }
                Key("boşluk", Modifier.weight(2.4f)) { onChar(' ') }
                Box(
                    Modifier
                        .weight(1.4f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBackspace),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.Backspace, "Sil", Modifier.size(22.dp)) }
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, bold: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = if (label.length > 1) 14.sp else 20.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
    }
}
