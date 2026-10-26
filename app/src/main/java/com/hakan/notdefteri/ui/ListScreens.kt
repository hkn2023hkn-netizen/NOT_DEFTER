package com.hakan.notdefteri.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hakan.notdefteri.data.Item
import com.hakan.notdefteri.data.Repository
import java.io.File

/** Sarı not kağıdı görünümü: hafif gölge, çizgili zemin, üstte koyu sarı şerit. */
fun Modifier.yellowPaper(lineSpacingPx: Float = 0f): Modifier = this
    .shadow(4.dp, RoundedCornerShape(4.dp))
    .clip(RoundedCornerShape(4.dp))
    .background(NoteYellow)
    .drawBehind {
        drawRect(NoteYellowDark, size = size.copy(height = 6.dp.toPx()))
        if (lineSpacingPx > 0f) {
            var y = lineSpacingPx + 6.dp.toPx()
            while (y < size.height) {
                drawLine(NoteLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += lineSpacingPx
            }
        }
    }

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Ara") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

private fun Item.matches(q: String) =
    q.isBlank() || title.contains(q, ignoreCase = true) || content.contains(q, ignoreCase = true)

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray, fontSize = 16.sp)
    }
}

@Composable
fun NotesScreen(notes: List<Item>, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = notes.filter { it.matches(query) }.sortedByDescending { it.createdAt }
    Column {
        SearchField(query) { query = it }
        if (shown.isEmpty()) {
            EmptyHint(if (notes.isEmpty()) "Henüz not yok.\nYeni not için + düğmesine basın." else "Sonuç yok")
        } else LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            items(shown, key = { it.id }) { note -> NoteCard(note) { onOpen(note.id) } }
        }
    }
}

@Composable
fun NoteCard(note: Item, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .yellowPaper()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)
    ) {
        note.photos.firstOrNull()?.let {
            AsyncImage(
                model = File(it), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(8.dp))
        }
        if (note.title.isNotBlank()) {
            Text(note.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (note.content.isNotBlank()) {
            Text(note.content, fontSize = 14.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, color = Color(0xFF3E2723))
        }
        ItemBadges(note)
    }
}

@Composable
fun ItemBadges(item: Item) {
    val due = item.dueAt
    if (due == null && item.photos.size <= 1) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        if (due != null) {
            val late = due < System.currentTimeMillis() && !item.done
            val color = if (late) MaterialTheme.colorScheme.tertiary else Color(0xFF5D4037)
            Icon(Icons.Filled.Alarm, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(3.dp))
            Text(formatDateTime(due), fontSize = 12.sp, color = color)
            Spacer(Modifier.width(8.dp))
        }
        if (item.photos.size > 1) {
            Icon(Icons.Filled.PhotoCamera, null, Modifier.size(14.dp), tint = Color(0xFF5D4037))
            Spacer(Modifier.width(3.dp))
            Text("${item.photos.size}", fontSize = 12.sp, color = Color(0xFF5D4037))
        }
    }
}

@Composable
fun TasksScreen(tasks: List<Item>, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = tasks.filter { it.matches(query) }
    val active = filtered.filter { !it.done }.sortedWith(compareBy({ it.dueAt ?: Long.MAX_VALUE }, { -it.createdAt }))
    val done = filtered.filter { it.done }.sortedByDescending { it.createdAt }
    Column {
        SearchField(query) { query = it }
        if (filtered.isEmpty()) {
            EmptyHint(if (tasks.isEmpty()) "Henüz görev yok.\nYeni görev için + düğmesine basın." else "Sonuç yok")
        } else LazyColumn(
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(active, key = { it.id }) { TaskRow(it) { onOpen(it.id) } }
            if (done.isNotEmpty()) {
                item {
                    Text(
                        "Tamamlananlar (${done.size})",
                        fontWeight = FontWeight.Bold, color = Color.Gray,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(done, key = { it.id }) { TaskRow(it) { onOpen(it.id) } }
            }
        }
    }
}

@Composable
fun TaskRow(task: Item, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .yellowPaper()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.done, onCheckedChange = { Repository.setDone(task.id, it) })
        Column(Modifier.weight(1f)) {
            Text(
                task.title.ifBlank { task.content.lineSequence().firstOrNull()?.ifBlank { null } ?: "(Başlıksız görev)" },
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) Color.Gray else Color.Black,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (task.title.isNotBlank() && task.content.isNotBlank()) {
                Text(task.content, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFF4E342E))
            }
            ItemBadges(task)
        }
        task.photos.firstOrNull()?.let {
            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model = File(it), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}
