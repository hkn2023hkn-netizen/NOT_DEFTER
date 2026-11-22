package com.hakan.notdefteri.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakan.notdefteri.security.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gizli bölüm. Ana ekranda "Not Defteri" yazısına uzun basınca açılır; hiçbir yerde düğmesi görünmez.
 * Açıkken ekran görüntüsü alınamaz ve son uygulamalar listesinde içerik görünmez.
 */
@Composable
fun SecretScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            SecretVault.close()
        }
    }
    val notes by SecretVault.notes.collectAsState()
    var exists by remember { mutableStateOf(SecretVault.exists) }
    var openId by remember { mutableStateOf<Long?>(null) }

    val current = notes
    when {
        !exists -> SecretSetup(onBack = onBack, onCreated = { exists = true })
        current == null -> SecretUnlock(onBack = onBack, onWiped = { exists = false })
        openId != null -> SecretEditor(
            note = current.firstOrNull { it.id == openId },
            newId = openId!!,
            onClose = { openId = null }
        )
        else -> SecretList(
            notes = current,
            onBack = onBack,
            onOpen = { openId = it },
            onNew = { openId = System.currentTimeMillis() },
            onDeleted = { exists = false; onBack() }
        )
    }
}

@Composable
private fun SecretFrame(onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler { onBack() }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hiçbir ad ya da simge yok: yalnızca parola alanı
        Spacer(Modifier.height(40.dp))
        content()
    }
}

@Composable
private fun SecretSetup(onBack: () -> Unit, onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(0) }
    val weak = SecretVault.weakness(pw)
    val mismatch = pw2.isNotEmpty() && pw != pw2
    SecretFrame(onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0E0))) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFC62828))
                    Spacer(Modifier.size(8.dp))
                    Text("Dikkat", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Şifre üst üste 3 kez yanlış girilirse buradaki her şey KALICI olarak silinir.\n" +
                        "• Parolayı unutursanız içerik hiçbir şekilde geri getirilemez. Parola hiçbir yerde saklanmaz.\n" +
                        "• Buradaki notlar telefonun güvenlik çipine bağlıdır, yalnızca bu telefonda açılır. Telefon sıfırlanır ya da değişirse taşınamaz.\n" +
                        "• Buradaki notlar yedeğe ve dışa aktarmaya girmez.",
                    fontSize = 14.sp, color = Color(0xFF3E2723)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SecretField(pw, "Güçlü parola", active == 0, { active = 0 })
        Text(
            weak ?: "Parola güçlü",
            fontSize = 13.sp,
            color = if (weak == null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
        )
        SecretField(pw2, "Parolayı tekrar yazın", active == 1, { active = 1 }, isError = mismatch)
        if (mismatch) Text("Parolalar aynı değil", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        ScrambledKeyboard(
            onChar = { c -> if (active == 0) pw += c else pw2 += c },
            onBackspace = { if (active == 0) pw = pw.dropLast(1) else pw2 = pw2.dropLast(1) }
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { agreed = !agreed }) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text("Anladım: 3 yanlış denemede her şey kalıcı olarak silinir.", fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = weak == null && pw == pw2 && agreed && !working,
            onClick = {
                working = true
                scope.launch {
                    withContext(Dispatchers.Default) { SecretVault.create(pw) }
                    working = false
                    onCreated()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (working) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Oluştur", fontSize = 17.sp)
        }
    }
}

@Composable
private fun SecretUnlock(onBack: () -> Unit, onWiped: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(SecretVault.MAX_TRIES - SecretVault.failedTries) }
    var wrong by remember { mutableStateOf(false) }
    var wiped by remember { mutableStateOf(false) }
    var otherDevice by remember { mutableStateOf(false) }

    if (wiped) {
        SecretFrame(onBack) {
            Text(
                "Çok fazla yanlış deneme.\nHer şey kalıcı olarak silindi.",
                fontSize = 18.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
            )
        }
        return
    }
    SecretFrame(onBack) {
        SecretField(pw, "Şifre", true, {}, isError = wrong)
        if (otherDevice) {
            Text(
                "Bu kayıt bu telefonda açılamıyor.",
                color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
        if (wrong || remaining < SecretVault.MAX_TRIES) {
            Text(
                (if (wrong) "Yanlış. " else "") + "Kalan hak: $remaining",
                color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = pw.isNotEmpty() && !working,
            onClick = {
                working = true
                scope.launch {
                    val r = withContext(Dispatchers.Default) { SecretVault.open(pw) }
                    working = false
                    when (r) {
                        is SecretVault.OpenResult.Ok -> pw = ""
                        is SecretVault.OpenResult.Wrong -> { wrong = true; remaining = r.remaining; pw = "" }
                        is SecretVault.OpenResult.Wiped -> { wiped = true; onWiped() }
                        is SecretVault.OpenResult.OtherDevice -> { otherDevice = true; pw = "" }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (working) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Aç", fontSize = 17.sp)
        }
        ScrambledKeyboard(
            onChar = { c -> pw += c; wrong = false },
            onBackspace = { pw = pw.dropLast(1) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecretList(
    notes: List<SecretVault.Note>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onDeleted: () -> Unit,
) {
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var changePw by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    if (changePw) {
        ChangePassword(onClose = { changePw = false })
        return
    }
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kapat") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Diğer") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Parolayı değiştir") }, onClick = { menu = false; changePw = true })
                            DropdownMenuItem(text = { Text("Hepsini kalıcı sil") }, onClick = { menu = false; confirmWipe = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF263238), titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, containerColor = Gold) { Icon(Icons.Filled.Add, "Yeni not") }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize()) { EmptyHint("Not yok.\nEklemek için + düğmesine basın.") }
        } else LazyColumn(
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            items(notes, key = { it.id }) { n ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .yellowPaper(paper)
                        .clickable { onOpen(n.id) }
                        .padding(12.dp)
                ) {
                    if (n.title.isNotBlank()) Text(n.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = paper.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (n.content.isNotBlank()) Text(n.content, fontSize = 14.sp, color = paper.inkSoft, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(formatDateTime(n.updatedAt), fontSize = 12.sp, color = paper.inkSoft, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Hepsi silinsin mi?") },
            text = { Text("Buradaki tüm notlar kalıcı olarak silinir. Geri alınamaz.") },
            confirmButton = {
                Button(
                    onClick = { confirmWipe = false; SecretVault.wipe(); onDeleted() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Vazgeç") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecretEditor(note: SecretVault.Note?, newId: Long, onClose: () -> Unit) {
    val paper = LocalPaper.current
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(TextFieldValue(note?.content ?: "")) }
    var confirmDelete by remember { mutableStateOf(false) }
    val id = note?.id ?: newId

    // Her değişiklik hemen şifrelenip kaydedilir
    LaunchedEffect(title, content.text) {
        val unchanged = note != null && note.title == title.trim() && note.content == content.text.trimEnd()
        if (!unchanged && (title.isNotBlank() || content.text.isNotBlank())) {
            withContext(Dispatchers.Default) {
                SecretVault.upsert(SecretVault.Note(id, title.trim(), content.text.trimEnd(), System.currentTimeMillis()))
            }
        }
    }

    fun close() {
        if (title.isBlank() && content.text.isBlank()) SecretVault.delete(id)
        onClose()
    }
    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Sil") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF263238), titleContentColor = Color.White,
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 400.dp)
                    .yellowPaper(paper)
                    .padding(top = 6.dp)
            ) {
                LinedTextField(
                    value = title, onValueChange = { title = it }, placeholder = "Başlık",
                    textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
                    paper = paper, singleLine = true, modifier = Modifier.padding(top = 4.dp)
                )
                LinedTextField(
                    value = content, onValueChange = { content = it }, placeholder = "Notunuzu yazın...",
                    textStyle = TextStyle(fontSize = 17.sp, lineHeight = 28.sp),
                    paper = paper, minHeight = 340.dp
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Not silinsin mi?") },
            text = { Text("Bu not kalıcı olarak silinir.") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; SecretVault.delete(id); onClose() }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } }
        )
    }
}

/** Parola değiştirme: üç alan, tek karışık klavye. Telefonun klavyesi açılmaz. */
@Composable
private fun ChangePassword(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val weak = SecretVault.weakness(new)
    val mismatch = new2.isNotEmpty() && new != new2
    fun edit(f: (String) -> String) {
        error = null
        when (active) {
            0 -> old = f(old)
            1 -> new = f(new)
            else -> new2 = f(new2)
        }
    }
    SecretFrame(onBack = { if (!working) onClose() }) {
        Text("Parolayı değiştir", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SecretField(old, "Şimdiki parola", active == 0, { active = 0 })
        SecretField(new, "Yeni parola", active == 1, { active = 1 })
        Text(
            weak ?: "Parola güçlü", fontSize = 12.sp,
            color = if (weak == null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
        )
        SecretField(new2, "Yeni parola (tekrar)", active == 2, { active = 2 }, isError = mismatch)
        if (mismatch) Text("Parolalar aynı değil", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !working, onClick = onClose, modifier = Modifier.weight(1f)) { Text("Vazgeç") }
            Button(
                enabled = !working && weak == null && new == new2 && old.isNotEmpty(),
                onClick = {
                    working = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { SecretVault.changePassword(old, new) }
                        working = false
                        if (ok) onClose() else { error = "Şimdiki parola yanlış"; old = ""; active = 0 }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Değiştir")
            }
        }
        ScrambledKeyboard(onChar = { c -> edit { it + c } }, onBackspace = { edit { it.dropLast(1) } })
    }
}
