package com.hakan.notdefteri.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hakan.notdefteri.R
import com.hakan.notdefteri.data.ItemType
import com.hakan.notdefteri.data.Repository

/** Hangi ekranın açık olduğunu tutar. editorId = -1 ise yeni öğe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(needsFullScreenPermission: Boolean, onOpenFullScreenSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorId by rememberSaveable { mutableStateOf(-1) }
    var editorType by rememberSaveable { mutableStateOf(ItemType.NOTE.name) }
    var editorPresetDue by rememberSaveable { mutableStateOf<Long?>(null) }
    val items by Repository.items.collectAsState()

    fun openEditor(id: Int, type: ItemType, presetDue: Long? = null) {
        editorId = id
        editorType = type.name
        editorPresetDue = presetDue
        editorOpen = true
    }

    if (editorOpen) {
        EditorScreen(
            itemId = editorId,
            newType = ItemType.valueOf(editorType),
            presetDue = editorPresetDue,
            onClose = { editorOpen = false }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painterResource(R.drawable.logo), null,
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Not Defteri", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Navy,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.StickyNote2, null) }, label = { Text("Notlar") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.CheckCircle, null) }, label = { Text("Görevler") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.CalendarMonth, null) }, label = { Text("Takvim") }
                )
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                FloatingActionButton(
                    onClick = { openEditor(-1, if (tab == 0) ItemType.NOTE else ItemType.TASK) },
                    containerColor = Gold
                ) { Icon(Icons.Filled.Add, "Yeni") }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (needsFullScreenPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Alarmın kilit ekranında tam ekran açılması için izin verin.")
                        TextButton(onClick = onOpenFullScreenSettings) { Text("İzin ver") }
                    }
                }
            }
            when (tab) {
                0 -> NotesScreen(items.filter { it.type == ItemType.NOTE }) { openEditor(it, ItemType.NOTE) }
                1 -> TasksScreen(items.filter { it.type == ItemType.TASK }) { openEditor(it, ItemType.TASK) }
                else -> CalendarScreen(
                    items = items,
                    onOpen = { openEditor(it.id, it.type) },
                    onNew = { type, due -> openEditor(-1, type, due) }
                )
            }
        }
    }
}
