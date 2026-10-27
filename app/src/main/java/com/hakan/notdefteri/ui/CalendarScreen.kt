package com.hakan.notdefteri.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakan.notdefteri.data.Item
import com.hakan.notdefteri.data.ItemType
import com.hakan.notdefteri.data.Markdown
import java.text.SimpleDateFormat
import java.util.Calendar

private val WeekDays = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun CalendarScreen(items: List<Item>, onOpen: (Item) -> Unit, onNew: (ItemType, Long) -> Unit) {
    var selectedDay by rememberSaveable { mutableLongStateOf(startOfDay(System.currentTimeMillis())) }
    // Gösterilen ay: o ayın 1'i
    var monthStart by rememberSaveable {
        mutableLongStateOf(Calendar.getInstance().apply {
            timeInMillis = selectedDay; set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis)
    }
    var askType by rememberSaveable { mutableStateOf(false) }

    val withDue = items.filter { it.dueAt != null }
    val dayItems = withDue.filter { sameDay(it.dueAt!!, selectedDay) }.sortedBy { it.dueAt }

    fun shiftMonth(delta: Int) {
        monthStart = Calendar.getInstance().apply { timeInMillis = monthStart; add(Calendar.MONTH, delta) }.timeInMillis
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Önceki ay") }
                Text(
                    SimpleDateFormat("MMMM yyyy", TR).format(monthStart).replaceFirstChar { it.titlecase(TR) },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { shiftMonth(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sonraki ay") }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 8.dp)) {
                WeekDays.forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 13.sp)
                }
            }
        }
        item {
            MonthGrid(monthStart, selectedDay, withDue) { selectedDay = it }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatDate(selectedDay), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Button(onClick = { askType = true }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Hatırlatıcı")
                }
            }
        }
        if (dayItems.isEmpty()) {
            item {
                Text("Bu gün için hatırlatıcı yok.", color = Color.Gray, modifier = Modifier.padding(16.dp))
            }
        }
        items(dayItems, key = { it.id }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .yellowPaper(LocalPaper.current)
                    .clickable { onOpen(item) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatTime(item.dueAt!!), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LocalPaper.current.ink)
                Spacer(Modifier.width(12.dp))
                Icon(
                    if (item.type == ItemType.TASK) Icons.Filled.CheckCircle else Icons.Filled.StickyNote2,
                    null, Modifier.size(18.dp), tint = LocalPaper.current.inkSoft
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (item.locked) "Kilitli not" else item.title.ifBlank { Markdown.toPlain(item.content).trim().ifBlank { "(Başlıksız)" } },
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, color = LocalPaper.current.ink,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null
                    )
                    val extra = buildList {
                        if (item.remindBeforeMinutes > 0) add(ReminderOptions.firstOrNull { it.first == item.remindBeforeMinutes }?.second ?: "")
                        if (item.alarmAtDue) add("zamanında alarm")
                    }.joinToString(" + ")
                    if (extra.isNotBlank()) Text(extra, fontSize = 12.sp, color = LocalPaper.current.inkSoft)
                }
            }
        }
    }

    if (askType) {
        val defaultDue = defaultDueFor(selectedDay)
        AlertDialog(
            onDismissRequest = { askType = false },
            title = { Text("Ne eklemek istersiniz?") },
            text = { Text("${formatDate(selectedDay)} gününe hatırlatıcılı bir görev ya da not ekleyin.") },
            confirmButton = {
                Button(onClick = { askType = false; onNew(ItemType.TASK, defaultDue) }) { Text("Görev") }
            },
            dismissButton = {
                OutlinedButton(onClick = { askType = false; onNew(ItemType.NOTE, defaultDue) }) { Text("Not") }
            }
        )
    }
}

/** Seçilen gün bugünse bir sonraki tam saat, değilse 09:00. */
private fun defaultDueFor(day: Long): Long {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply { timeInMillis = day; set(Calendar.HOUR_OF_DAY, 9) }
    if (sameDay(day, now)) {
        cal.timeInMillis = now
        cal.add(Calendar.HOUR_OF_DAY, 1)
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

@Composable
private fun MonthGrid(monthStart: Long, selectedDay: Long, withDue: List<Item>, onSelect: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Pazartesi = 0
    val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val today = System.currentTimeMillis()
    val rows = (offset + daysInMonth + 6) / 7

    Column(Modifier.padding(horizontal = 8.dp)) {
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val dayMillis = Calendar.getInstance().apply {
                                timeInMillis = monthStart; set(Calendar.DAY_OF_MONTH, dayNum)
                            }.timeInMillis
                            val isSelected = sameDay(dayMillis, selectedDay)
                            val isToday = sameDay(dayMillis, today)
                            val dayEntries = withDue.filter { sameDay(it.dueAt!!, dayMillis) }
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .then(if (isToday && !isSelected) Modifier.border(2.dp, Gold, CircleShape) else Modifier)
                                    .clickable { onSelect(dayMillis) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "$dayNum",
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                                if (dayEntries.isNotEmpty()) {
                                    Box(
                                        Modifier
                                            .padding(top = 2.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (dayEntries.all { it.done }) Color.Gray else NoteYellowDark)
                                    )
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
