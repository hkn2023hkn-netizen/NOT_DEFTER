package com.hakan.notdefteri.ui

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hakan.notdefteri.data.Item
import com.hakan.notdefteri.data.Markdown
import com.hakan.notdefteri.data.Repository
import java.io.File

private fun Item.matches(q: String) =
    q.isBlank() || (!locked && (title.contains(q, ignoreCase = true) || content.contains(q, ignoreCase = true)))

/** Sabitlenenler önce, sonra en son değiştirilen. */
private fun List<Item>.sortedForList() = sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.updatedAt })

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
    }
}

/** Arama + filtreler: favoriler, sesli notlar ve DEPO (uzun süre saklananlar). */
@Composable
private fun SearchAndFilter(
    query: String, onQuery: (String) -> Unit,
    favOnly: Boolean, onFav: (Boolean) -> Unit,
    depo: Boolean, onDepo: (Boolean) -> Unit, depoCount: Int,
    voiceOnly: Boolean? = null, onVoice: (Boolean) -> Unit = {},
) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp)) {
        CompactSearch(query, onQuery, Modifier.fillMaxWidth())
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = favOnly, onClick = { onFav(!favOnly) },
                label = { Text("Favoriler", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = if (favOnly) Gold else Color.Gray) }
            )
            if (voiceOnly != null) {
                FilterChip(
                    selected = voiceOnly, onClick = { onVoice(!voiceOnly) },
                    label = { Text("Sesli notlar", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Mic, null, Modifier.size(16.dp)) }
                )
            }
            FilterChip(
                selected = depo, onClick = { onDepo(!depo) },
                label = { Text("DEPO ($depoCount)", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Archive, null, Modifier.size(16.dp)) }
            )
        }
    }
}

private fun Item.hasVoice() = audios.isNotEmpty()

@Composable
fun NotesScreen(notes: List<Item>, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var voiceOnly by rememberSaveable { mutableStateOf(false) }
    var depo by rememberSaveable { mutableStateOf(false) }
    val depoCount = notes.count { it.archived }
    // DEPO'daki notlar ana listede görünmez; yalnızca DEPO seçilince listelenir
    val shown = notes.filter {
        it.archived == depo && it.matches(query) && (!favOnly || it.favorite) && (!voiceOnly || it.hasVoice())
    }.sortedForList()
    Column {
        SearchAndFilter(
            query, { query = it }, favOnly, { favOnly = it }, depo, { depo = it }, depoCount,
            voiceOnly, { voiceOnly = it }
        )
        if (shown.isEmpty()) {
            EmptyHint(
                when {
                    depo && depoCount == 0 -> "DEPO boş.\nBir notu saklamak için notu açın,\n⋮ menüsünden \"DEPO'ya taşı\"yı seçin."
                    notes.isEmpty() -> "Henüz not yok.\nYeni not için + düğmesine,\nsesli not için mikrofona basın."
                    else -> "Sonuç yok"
                }
            )
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
private fun LockedText() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = LocalPaper.current.inkSoft)
        Spacer(Modifier.width(4.dp))
        Text("Kilitli", fontWeight = FontWeight.Bold, color = LocalPaper.current.ink)
    }
}

@Composable
fun NoteCard(note: Item, onClick: () -> Unit) {
    val paper = LocalPaper.current
    Column(
        Modifier
            .fillMaxWidth()
            .yellowPaper(paper)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)
    ) {
        if (note.pinned || note.favorite || note.hasVoice()) {
            Row(Modifier.align(Alignment.End)) {
                if (note.hasVoice()) Icon(Icons.Filled.Mic, "Sesli not", Modifier.size(16.dp), tint = paper.inkSoft)
                if (note.pinned) Icon(Icons.Filled.PushPin, "Sabitlenmiş", Modifier.size(16.dp), tint = Color(0xFFC62828))
                if (note.favorite) Icon(Icons.Filled.Star, "Favori", Modifier.size(16.dp), tint = Color(0xFFE65100))
            }
        }
        if (note.locked) {
            LockedText()
        } else {
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
                Text(note.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = paper.ink)
            }
            if (note.content.isNotBlank()) {
                Text(Markdown.toPlain(note.content).trim(), fontSize = 14.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, color = paper.inkSoft)
            }
        }
        ItemBadges(note)
    }
}

@Composable
fun ItemBadges(item: Item) {
    val due = item.dueAt
    val photoCount = if (item.locked) 0 else item.photos.size
    if (due == null && photoCount <= 1) return
    val soft = LocalPaper.current.inkSoft
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        if (due != null) {
            val late = due < System.currentTimeMillis() && !item.done
            val color = if (late) Color(0xFFC62828) else soft
            Icon(Icons.Filled.Alarm, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(3.dp))
            Text(formatDateTime(due), fontSize = 12.sp, color = color)
            Spacer(Modifier.width(8.dp))
        }
        if (photoCount > 1) {
            Icon(Icons.Filled.PhotoCamera, null, Modifier.size(14.dp), tint = soft)
            Spacer(Modifier.width(3.dp))
            Text("$photoCount", fontSize = 12.sp, color = soft)
        }
    }
}

@Composable
fun TasksScreen(tasks: List<Item>, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var depo by rememberSaveable { mutableStateOf(false) }
    var showAllDone by rememberSaveable { mutableStateOf(false) }
    val depoCount = tasks.count { it.archived }
    val filtered = tasks.filter { it.archived == depo && it.matches(query) && (!favOnly || it.favorite) }
    val active = filtered.filter { !it.done }.sortedWith(
        compareByDescending<Item> { it.pinned }.thenBy { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.updatedAt }
    )
    val done = filtered.filter { it.done }.sortedByDescending { it.updatedAt }
    Column {
        SearchAndFilter(query, { query = it }, favOnly, { favOnly = it }, depo, { depo = it }, depoCount)
        if (filtered.isEmpty()) {
            EmptyHint(if (depo && depoCount == 0) "DEPO boş." else if (tasks.isEmpty()) "Henüz görev yok.\nYeni görev için + düğmesine basın." else "Sonuç yok")
        } else LazyColumn(
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(active, key = { it.id }) { TaskRow(it) { onOpen(it.id) } }
            if (done.isNotEmpty()) {
                // Son tamamlanan 3 görev görünür; gerisi "Tamamlananlar" düğmesiyle açılır
                val canExpand = done.size > 3
                item {
                    TextButton(
                        onClick = { if (canExpand) showAllDone = !showAllDone },
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text("Tamamlananlar (${done.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (canExpand) {
                            Spacer(Modifier.width(4.dp))
                            Icon(if (showAllDone) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                    }
                }
                val visibleDone = if (showAllDone) done else done.take(3)
                items(visibleDone, key = { it.id }) { TaskRow(it) { onOpen(it.id) } }
                if (canExpand && !showAllDone) {
                    item {
                        TextButton(onClick = { showAllDone = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("${done.size - 3} görev daha göster")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskRow(task: Item, onClick: () -> Unit) {
    val paper = LocalPaper.current
    Row(
        Modifier
            .fillMaxWidth()
            .yellowPaper(paper)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.done, onCheckedChange = { Repository.setDone(task.id, it) },
            colors = CheckboxDefaults.colors(uncheckedColor = paper.inkSoft, checkedColor = Navy)
        )
        Column(Modifier.weight(1f)) {
            if (task.locked) {
                LockedText()
            } else {
                Text(
                    task.title.ifBlank { Markdown.toPlain(task.content).lineSequence().firstOrNull()?.ifBlank { null } ?: "(Başlıksız görev)" },
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) Color.Gray else paper.ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (task.title.isNotBlank() && task.content.isNotBlank()) {
                    Text(Markdown.toPlain(task.content).trim(), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = paper.inkSoft)
                }
            }
            ItemBadges(task)
        }
        if (task.pinned) Icon(Icons.Filled.PushPin, "Sabitlenmiş", Modifier.size(16.dp), tint = Color(0xFFC62828))
        if (task.favorite) Icon(Icons.Filled.Star, "Favori", Modifier.size(16.dp), tint = Color(0xFFE65100))
        if (!task.locked) task.photos.firstOrNull()?.let {
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
