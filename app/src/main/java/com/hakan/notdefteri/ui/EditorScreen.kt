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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.hakan.notdefteri.data.Item
import com.hakan.notdefteri.data.ItemType
import com.hakan.notdefteri.data.Markdown
import com.hakan.notdefteri.data.Repository
import com.hakan.notdefteri.io.ExportFormat
import com.hakan.notdefteri.io.Exporter
import com.hakan.notdefteri.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * Not / görev düzenleyici. Kilitli bir öğe açılıyorsa önce parola sorar.
 */
@Composable
fun EditorScreen(itemId: Int, newType: ItemType, presetDue: Long?, onClose: () -> Unit) {
    val original = remember(itemId) { Repository.get(itemId) }
    var unlocked by remember { mutableStateOf(original?.locked != true || Vault.isUnlocked) }
    if (!unlocked) {
        BackHandler { onClose() }
        FullScreenCenter {
            PasswordDialog(
                title = "Kilitli not",
                message = "Bu notu açmak için parolanızı girin.",
                onDismiss = onClose,
                onSubmit = { pw -> Vault.unlock(pw).also { if (it) unlocked = true } }
            )
        }
        return
    }
    val plain = remember(itemId) {
        if (original?.locked == true) Vault.decrypt(original) else (original?.title ?: "") to (original?.content ?: "")
    }
    if (plain == null) {
        // Şifre çözülemedi: içeriği bozmamak için düzenleyiciyi açma
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Not açılamadı (şifre çözülemedi)", Toast.LENGTH_LONG).show()
            onClose()
        }
        return
    }
    EditorContent(original, newType, presetDue, plain.first, plain.second, onClose)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditorContent(
    original: Item?,
    newType: ItemType,
    presetDue: Long?,
    initialTitle: String,
    initialContent: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paper = LocalPaper.current
    val id = rememberSaveable { original?.id ?: Repository.newId() }
    val type = original?.type ?: newType
    val createdAt = rememberSaveable { original?.createdAt ?: System.currentTimeMillis() }

    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var content by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialContent)) }
    var photosSaved by rememberSaveable { mutableStateOf(ArrayList(original?.photos ?: emptyList())) }
    val photos = remember { photosSaved.toMutableStateList() }
    var dueAt by rememberSaveable { mutableStateOf(original?.dueAt ?: presetDue) }
    var before by rememberSaveable { mutableStateOf(original?.remindBeforeMinutes ?: if (presetDue != null) 15 else 0) }
    var alarmAtDue by rememberSaveable { mutableStateOf(original?.alarmAtDue ?: true) }
    var done by rememberSaveable { mutableStateOf(original?.done ?: false) }
    var pinned by rememberSaveable { mutableStateOf(original?.pinned ?: false) }
    var favorite by rememberSaveable { mutableStateOf(original?.favorite ?: false) }
    var locked by rememberSaveable { mutableStateOf(original?.locked ?: false) }
    var preview by rememberSaveable { mutableStateOf(original != null && initialContent.isNotBlank()) }

    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var setPasswordDialog by remember { mutableStateOf(false) }
    var askPasswordDialog by remember { mutableStateOf(false) }

    fun syncPhotos() { photosSaved = ArrayList(photos) }
    fun isEmpty() = title.isBlank() && content.text.isBlank() && photos.isEmpty() && dueAt == null

    fun buildItem(): Item {
        val base = Item(
            id = id, type = type, title = title.trim(), content = content.text.trimEnd(),
            photos = photos.toList(), dueAt = dueAt, remindBeforeMinutes = before,
            alarmAtDue = alarmAtDue, done = done, createdAt = createdAt,
            updatedAt = System.currentTimeMillis(), pinned = pinned, favorite = favorite, locked = locked,
        )
        return if (locked) base.copy(title = "", content = Vault.encrypt(base.title, base.content)) else base
    }

    // Otomatik kayıt: her değişiklikte hemen veritabanına yazılır
    LaunchedEffect(title, content.text, photosSaved, dueAt, before, alarmAtDue, done, pinned, favorite, locked) {
        val exists = Repository.get(id) != null
        try {
            if (exists || !isEmpty()) Repository.upsert(buildItem())
        } catch (e: Exception) {
            // Kasa kilitlendiyse şifreli kayıt yapılamaz; son kayıt geçerli kalır
        }
    }

    fun close() {
        if (isEmpty()) {
            if (Repository.get(id) != null) Repository.deleteForever(id)
            photos.forEach { File(it).delete() }
        } else {
            try { Repository.upsert(buildItem()) } catch (e: Exception) {}
            original?.let { Repository.deleteUnusedPhotos(it.photos) }
            val due = dueAt
            if (due != null && due < System.currentTimeMillis() && !done) {
                Toast.makeText(context, "Uyarı: seçilen zaman geçmişte, alarm çalmayacak", Toast.LENGTH_LONG).show()
            }
        }
        onClose()
    }

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

    fun toggleLock() {
        when {
            locked -> locked = false
            !Vault.hasPassword -> setPasswordDialog = true
            !Vault.isUnlocked -> askPasswordDialog = true
            else -> locked = true
        }
    }

    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (type == ItemType.NOTE) "Not" else "Görev") },
                navigationIcon = {
                    IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                },
                actions = {
                    IconButton(onClick = { preview = !preview }) {
                        Icon(if (preview) Icons.Filled.Edit else Icons.Filled.Visibility, if (preview) "Düzenle" else "Önizle")
                    }
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (pinned) "Sabitlemeyi kaldır" else "Sabitle")
                    }
                    IconButton(onClick = { favorite = !favorite }) {
                        Icon(if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favori", tint = if (favorite) Gold else Color.White)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Diğer") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (locked) "Kilidi kaldır" else "Parola ile kilitle") },
                                leadingIcon = { Icon(if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock, null) },
                                onClick = { menu = false; toggleLock() }
                            )
                            DropdownMenuItem(
                                text = { Text("Dışa aktar / paylaş") },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = { menu = false; exportDialog = true }
                            )
                            if (original != null || Repository.get(id) != null) {
                                DropdownMenuItem(
                                    text = { Text("Çöp kutusuna taşı") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = { menu = false; confirmDelete = true }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barColor(), titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (!preview) {
                FormatToolbar(onApply = { content = it(content) })
            }
        }
    ) { padding ->
        // Düzenleme modunda klavye boşluğunu alttaki araç çubuğu zaten bırakıyor; iki kez eklenirse yazı alanı tek satıra iner
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .then(if (preview) Modifier.imePadding() else Modifier)
        ) {
        val viewport = maxHeight
        val keyboardOpen = WindowInsets.isImeVisible
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            if (locked) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Bu not AES-256 ile şifreli saklanıyor", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Sarı kağıt
            val lineHeight = 28.sp
            val linePx = with(LocalDensity.current) { lineHeight.toPx() }
            Column(
                Modifier
                    .fillMaxWidth()
                    // Klavye açıkken sarı kağıt görünen alanın tamamını kaplar
                    .heightIn(min = if (keyboardOpen) maxOf(320.dp, viewport - 24.dp) else 320.dp)
                    .yellowPaper(paper, lineSpacingPx = if (preview) 0f else linePx)
                    .padding(top = 6.dp)
            ) {
                val fieldColors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = paper.ink,
                    unfocusedTextColor = paper.ink,
                    focusedPlaceholderColor = paper.inkSoft.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = paper.inkSoft.copy(alpha = 0.6f),
                    cursorColor = paper.ink,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (type == ItemType.TASK) {
                        Checkbox(
                            checked = done, onCheckedChange = { done = it },
                            colors = CheckboxDefaults.colors(uncheckedColor = paper.inkSoft, checkedColor = Navy)
                        )
                    }
                    TextField(
                        value = title, onValueChange = { title = it },
                        placeholder = { Text("Başlık") },
                        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = lineHeight, color = paper.ink),
                        colors = fieldColors, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (preview) {
                    if (content.text.isBlank()) {
                        Text(
                            "Boş. Yazmak için dokunun.", color = paper.inkSoft,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { preview = false }
                                .padding(16.dp)
                        )
                    } else {
                        MarkdownView(
                            content.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { preview = false }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            onToggleCheck = { line -> content = content.copy(text = Markdown.toggleCheck(content.text, line)) }
                        )
                    }
                } else {
                    TextField(
                        value = content,
                        onValueChange = { content = TextFormat.continueList(content, it) },
                        placeholder = {
                            Text(
                                if (type == ItemType.NOTE) "Notunuzu yazın...\n**kalın**, *italik*, - liste, - [ ] yapılacak, ``` kod"
                                else "Görev ayrıntıları..."
                            )
                        },
                        textStyle = TextStyle(fontSize = 17.sp, lineHeight = lineHeight, color = paper.ink),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp)
                    )
                }
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
                                    .clickable {
                                        photos.remove(path); syncPhotos()
                                        if (original?.photos?.contains(path) != true) File(path).delete()
                                    },
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Çöp kutusuna taşınsın mı?") },
            text = { Text("Çöp kutusundan 30 gün içinde geri getirebilirsiniz. (Ayarlar > Çöp kutusu)") },
            confirmButton = {
                Button(onClick = {
                    Repository.upsert(buildItem())
                    Repository.moveToTrash(id)
                    confirmDelete = false
                    onClose()
                }) { Text("Taşı") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }

    if (exportDialog) {
        AlertDialog(
            onDismissRequest = { exportDialog = false },
            title = { Text("Dışa aktar") },
            text = {
                Column {
                    if (locked) Text("Dışa aktarılan dosya şifresiz olur.", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    ExportFormat.values().forEach { fmt ->
                        Text(
                            fmt.label,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    exportDialog = false
                                    val plainItem = buildItem().copy(title = title.trim(), content = content.text)
                                    scope.launch {
                                        try {
                                            val file = withContext(Dispatchers.IO) {
                                                Exporter.createItemFile(context, plainItem, title.trim(), content.text, fmt)
                                            }
                                            Exporter.share(context, file, fmt.mime)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Dışa aktarılamadı", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { exportDialog = false }) { Text("Vazgeç") } }
        )
    }

    if (setPasswordDialog) {
        NewPasswordDialog(
            onDismiss = { setPasswordDialog = false },
            onSet = { pw ->
                Vault.setPassword(null, pw)
                setPasswordDialog = false
                locked = true
                Toast.makeText(context, "Parola belirlendi. Not kilitlendi.", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (askPasswordDialog) {
        PasswordDialog(
            title = "Parola",
            message = "Notu kilitlemek için parolanızı girin.",
            onDismiss = { askPasswordDialog = false },
            onSubmit = { pw -> Vault.unlock(pw).also { if (it) { askPasswordDialog = false; locked = true } } }
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

/** Yeni parola belirleme penceresi (iki kez yazdırır). */
@Composable
fun NewPasswordDialog(onDismiss: () -> Unit, onSet: (String) -> Unit, title: String = "Parola belirleyin") {
    var pw1 by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bu parola kilitli notları ve uygulama kilidini açar. Unutursanız kilitli notlar kurtarılamaz.", fontSize = 13.sp)
                PasswordField(pw1, { pw1 = it; error = null }, "Yeni parola")
                PasswordField(pw2, { pw2 = it; error = null }, "Parolayı tekrar yazın", error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    pw1.length < 4 -> "Parola en az 4 karakter olmalı"
                    pw1 != pw2 -> "Parolalar aynı değil"
                    else -> null
                }
                if (error == null) onSet(pw1)
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

private class Tool(val icon: ImageVector, val label: String, val action: (TextFieldValue) -> TextFieldValue)

@Composable
private fun FormatToolbar(onApply: ((TextFieldValue) -> TextFieldValue) -> Unit) {
    val tools = listOf(
        Tool(Icons.Filled.FormatBold, "Kalın") { TextFormat.wrap(it, "**") },
        Tool(Icons.Filled.FormatItalic, "İtalik") { TextFormat.wrap(it, "*") },
        Tool(Icons.Filled.FormatStrikethrough, "Üstü çizili") { TextFormat.wrap(it, "~~") },
        Tool(Icons.Filled.Title, "Başlık") { TextFormat.linePrefix(it, "## ") },
        Tool(Icons.AutoMirrored.Filled.FormatListBulleted, "Madde listesi") { TextFormat.linePrefix(it, "- ") },
        Tool(Icons.Filled.FormatListNumbered, "Numaralı liste") { TextFormat.linePrefix(it, "1. ") },
        Tool(Icons.Filled.CheckBox, "Yapılacak") { TextFormat.linePrefix(it, "- [ ] ") },
        Tool(Icons.Filled.FormatQuote, "Alıntı") { TextFormat.linePrefix(it, "> ") },
        Tool(Icons.Filled.Code, "Satır içi kod") { TextFormat.wrap(it, "`") },
        Tool(Icons.Filled.DataObject, "Kod bloğu") { TextFormat.codeBlock(it) },
    )
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).imePadding()) {
        HorizontalDivider()
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            tools.forEach { t ->
                IconButton(onClick = { onApply(t.action) }) { Icon(t.icon, t.label) }
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.primary)
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
        Text("Zamanından önce alarm", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
