package com.hakan.notdefteri.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.hakan.notdefteri.data.Item
import com.hakan.notdefteri.data.ItemType
import com.hakan.notdefteri.data.Repository
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

private fun newPhotoFile(): File = File(Repository.photoDir, "IMG_${UUID.randomUUID()}.jpg")

private fun copyToPhotos(context: Context, uri: Uri): String? = try {
    val target = newPhotoFile()
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { input.copyTo(it) }
    }
    target.absolutePath
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(itemId: Int, newType: ItemType, presetDue: Long?, onClose: () -> Unit) {
    val context = LocalContext.current
    val original = remember(itemId) { Repository.get(itemId) }
    val id = rememberSaveable { original?.id ?: Repository.newId() }
    val type = original?.type ?: newType

    var title by rememberSaveable { mutableStateOf(original?.title ?: "") }
    var content by rememberSaveable { mutableStateOf(original?.content ?: "") }
    var photosSaved by rememberSaveable { mutableStateOf(ArrayList(original?.photos ?: emptyList())) }
    val photos = remember { photosSaved.toMutableStateList() }
    var dueAt by rememberSaveable { mutableStateOf(original?.dueAt ?: presetDue) }
    var before by rememberSaveable { mutableStateOf(original?.remindBeforeMinutes ?: if (presetDue != null) 15 else 0) }
    var alarmAtDue by rememberSaveable { mutableStateOf(original?.alarmAtDue ?: true) }
    var done by rememberSaveable { mutableStateOf(original?.done ?: false) }

    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    fun syncPhotos() { photosSaved = ArrayList(photos) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCameraPath
        if (path != null) {
            if (ok && File(path).length() > 0) photos.add(path) else File(path).delete()
            syncPhotos()
        }
        pendingCameraPath = null
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.mapNotNull { copyToPhotos(context, it) }.forEach { photos.add(it) }
        syncPhotos()
    }

    fun launchCamera() {
        val file = newPhotoFile()
        pendingCameraPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        try {
            takePicture.launch(uri)
        } catch (e: Exception) {
            pendingCameraPath = null
            Toast.makeText(context, "Kamera uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveAndClose() {
        val isEmpty = title.isBlank() && content.isBlank() && photos.isEmpty() && dueAt == null
        if (isEmpty) {
            // Boş öğe kaydedilmez; eklenmiş fotoğraf kalmışsa temizlenir
            photos.filterNot { original?.photos?.contains(it) == true }.forEach { File(it).delete() }
            if (original != null) Repository.delete(original.id)
        } else {
            Repository.upsert(
                Item(
                    id = id, type = type, title = title.trim(), content = content.trimEnd(),
                    photos = photos.toList(), dueAt = dueAt, remindBeforeMinutes = before,
                    alarmAtDue = alarmAtDue, done = done,
                    createdAt = original?.createdAt ?: System.currentTimeMillis()
                )
            )
            if (dueAt != null && dueAt!! < System.currentTimeMillis() && !done) {
                Toast.makeText(context, "Uyarı: seçilen zaman geçmişte, alarm çalmayacak", Toast.LENGTH_LONG).show()
            }
        }
        onClose()
    }

    BackHandler { saveAndClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (type == ItemType.NOTE) "Not" else "Görev") },
                navigationIcon = {
                    IconButton(onClick = { saveAndClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                },
                actions = {
                    if (original != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Sil") }
                    }
                    IconButton(onClick = { saveAndClose() }) { Icon(Icons.Filled.Check, "Kaydet") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            // Sarı kağıt
            val lineHeight = 28.sp
            val linePx = with(LocalDensity.current) { lineHeight.toPx() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp)
                    .yellowPaper(lineSpacingPx = linePx)
                    .padding(top = 6.dp)
            ) {
                val fieldColors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (type == ItemType.TASK) {
                        Checkbox(checked = done, onCheckedChange = { done = it })
                    }
                    TextField(
                        value = title, onValueChange = { title = it },
                        placeholder = { Text("Başlık") },
                        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = lineHeight),
                        colors = fieldColors, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextField(
                    value = content, onValueChange = { content = it },
                    placeholder = { Text(if (type == ItemType.NOTE) "Notunuzu yazın..." else "Görev ayrıntıları...") },
                    textStyle = TextStyle(fontSize = 17.sp, lineHeight = lineHeight, color = Color(0xFF3E2723)),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                )
            }

            // Fotoğraflar
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Fotoğraf çek")
                }
                FilledTonalButton(
                    onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Galeriden")
                }
            }
            if (photos.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(photos.toList(), key = { _, p -> p }) { _, path ->
                        Box {
                            AsyncImage(
                                model = File(path), contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewPhoto = path }
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xAA000000))
                                    .clickable { photos.remove(path); syncPhotos() },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Close, "Fotoğrafı kaldır", tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }

            // Hatırlatıcı
            Spacer(Modifier.height(20.dp))
            ReminderSection(
                dueAt = dueAt, before = before, alarmAtDue = alarmAtDue,
                onAdd = {
                    dueAt = Calendar.getInstance().apply {
                        add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (before == 0) before = 15
                },
                onRemove = { dueAt = null },
                onPickDate = { showDate = true },
                onPickTime = { showTime = true },
                onBefore = { before = it },
                onAlarmAtDue = { alarmAtDue = it }
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showDate && dueAt != null) {
        val local = Calendar.getInstance().apply { timeInMillis = dueAt!! }
        // DatePicker UTC gece yarısı ile çalışır
        val utcInitial = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val state = rememberDatePickerState(initialSelectedDateMillis = utcInitial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { sel ->
                        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = sel }
                        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
                        dueAt = local.timeInMillis
                    }
                    showDate = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Vazgeç") } }
        ) { DatePicker(state = state) }
    }

    if (showTime && dueAt != null) {
        val local = Calendar.getInstance().apply { timeInMillis = dueAt!! }
        val state = rememberTimePickerState(
            initialHour = local.get(Calendar.HOUR_OF_DAY),
            initialMinute = local.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Saat seçin") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    local.set(Calendar.HOUR_OF_DAY, state.hour)
                    local.set(Calendar.MINUTE, state.minute)
                    local.set(Calendar.SECOND, 0); local.set(Calendar.MILLISECOND, 0)
                    dueAt = local.timeInMillis
                    showTime = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Vazgeç") } }
        )
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Silinsin mi?") },
            text = { Text("Bu ${if (type == ItemType.NOTE) "not" else "görev"} ve fotoğrafları silinecek.") },
            confirmButton = {
                Button(onClick = {
                    photos.filterNot { it in original.photos }.forEach { File(it).delete() }
                    Repository.delete(original.id)
                    confirmDelete = false
                    onClose()
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }

    viewPhoto?.let { path ->
        Dialog(onDismissRequest = { viewPhoto = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { viewPhoto = null }
            ) {
                AsyncImage(
                    model = File(path), contentDescription = null,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                )
                IconButton(onClick = { viewPhoto = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Icon(Icons.Filled.Close, "Kapat", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ReminderSection(
    dueAt: Long?, before: Int, alarmAtDue: Boolean,
    onAdd: () -> Unit, onRemove: () -> Unit,
    onPickDate: () -> Unit, onPickTime: () -> Unit,
    onBefore: (Int) -> Unit, onAlarmAtDue: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Alarm, null, tint = Navy)
            Spacer(Modifier.width(8.dp))
            Text("Hatırlatıcı ve alarm", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (dueAt != null) TextButton(onClick = onRemove) { Text("Kaldır") }
        }
        if (dueAt == null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAdd) { Text("Hatırlatıcı ekle") }
        } else ReminderDetails(dueAt, before, alarmAtDue, onPickDate, onPickTime, onBefore, onAlarmAtDue)
    }
}

@Composable
private fun ReminderDetails(
    dueAt: Long, before: Int, alarmAtDue: Boolean,
    onPickDate: () -> Unit, onPickTime: () -> Unit,
    onBefore: (Int) -> Unit, onAlarmAtDue: (Boolean) -> Unit,
) {
    Column {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickDate, modifier = Modifier.weight(1.4f)) {
                Text(java.text.SimpleDateFormat("d MMM yyyy", TR).format(dueAt))
            }
            OutlinedButton(onClick = onPickTime, modifier = Modifier.weight(1f)) {
                Text(formatTime(dueAt))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Zamanından önce alarm", fontSize = 14.sp, color = Color.Gray)
        var menu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(ReminderOptions.firstOrNull { it.first == before }?.second ?: "$before dakika önce")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                ReminderOptions.forEach { (minutes, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onBefore(minutes); menu = false })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tam zamanında alarm çalsın", modifier = Modifier.weight(1f))
            Switch(checked = alarmAtDue, onCheckedChange = onAlarmAtDue)
        }
    }
}
