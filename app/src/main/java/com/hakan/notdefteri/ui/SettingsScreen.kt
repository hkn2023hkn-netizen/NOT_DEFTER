package com.hakan.notdefteri.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakan.notdefteri.R
import com.hakan.notdefteri.data.ItemType
import com.hakan.notdefteri.data.Markdown
import com.hakan.notdefteri.data.Repository
import com.hakan.notdefteri.data.Settings
import com.hakan.notdefteri.data.ThemeMode
import com.hakan.notdefteri.io.Exporter
import com.hakan.notdefteri.io.Importer
import com.hakan.notdefteri.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barColor(), titleContentColor = Color.White, navigationIconContentColor = Color.White
                )
            )
        },
        content = content
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenTrash: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by Settings.theme.collectAsState()
    val items by Repository.items.collectAsState()
    var appLock by remember { mutableStateOf(Settings.appLock) }
    var hasPassword by remember { mutableStateOf(Vault.hasPassword) }
    var newPasswordFor by remember { mutableStateOf<String?>(null) } // "lock" ya da "set"
    var changePassword by remember { mutableStateOf(false) }
    var oldPasswordOk by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = "İçe aktarılıyor..."
        scope.launch {
            val r = withContext(Dispatchers.IO) { Importer.import(context, uris) }
            busy = null
            val msg = if (r.imported > 0) "${r.imported} not içe aktarıldı" + (if (r.skipped > 0) ", ${r.skipped} dosya okunamadı" else "")
            else "İçe aktarılacak not bulunamadı"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    SubScreen("Ayarlar", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("Görünüm")
            ThemeMode.values().forEach { mode ->
                SettingRow(mode.label, onClick = { Settings.setTheme(mode) }) {
                    RadioButton(selected = theme == mode, onClick = { Settings.setTheme(mode) })
                }
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle("Gizlilik ve güvenlik")
            SettingRow(
                "Uygulama kilidi",
                "Açılışta ve uygulamaya geri dönüldüğünde parola sorulur",
                onClick = {
                    if (appLock) { Settings.appLock = false; appLock = false }
                    else if (!hasPassword) newPasswordFor = "lock"
                    else { Settings.appLock = true; appLock = true }
                }
            ) {
                Switch(checked = appLock, onCheckedChange = {
                    if (!it) { Settings.appLock = false; appLock = false }
                    else if (!hasPassword) newPasswordFor = "lock"
                    else { Settings.appLock = true; appLock = true }
                })
            }
            if (hasPassword) {
                SettingRow("Parolayı değiştir", "Kilitli notlar yeni parolayla yeniden şifrelenir", onClick = { changePassword = true })
            } else {
                SettingRow("Parola belirle", "Notları kilitlemek için gerekli", onClick = { newPasswordFor = "set" })
            }
            Text(
                "Kilitli notlar AES-256 ile şifrelenir. Not içinde ⋮ menüsünden \"Parola ile kilitle\"yi seçin. " +
                    "Kilitli notların fotoğrafları listede gizlenir ama şifrelenmez.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            HorizontalDivider(Modifier.padding(top = 12.dp))
            SectionTitle("Veriler")
            val trashCount = items.count { it.deletedAt != null }
            SettingRow("Çöp kutusu", if (trashCount == 0) "Boş" else "$trashCount öğe, 30 gün sonra kalıcı olarak silinir", onClick = onOpenTrash)
            SettingRow(
                "İçe aktar",
                "Google Keep (Takeout ZIP ya da .json), Evernote (.enex), Markdown (.md), metin (.txt) ya da bu uygulamanın yedeği",
                onClick = { importLauncher.launch(arrayOf("*/*")) }
            )
            SettingRow(
                "Tümünü dışa aktar (yedek)",
                "Tüm notlar ve görevler fotoğraflarıyla birlikte ZIP dosyası olarak (kilitli notlar hariç)",
                onClick = {
                    busy = "Hazırlanıyor..."
                    scope.launch {
                        try {
                            val (file, count) = withContext(Dispatchers.IO) { Exporter.createAllZip(context, items) }
                            Exporter.share(context, file, "application/zip")
                            Toast.makeText(context, "$count öğe dışa aktarıldı", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Dışa aktarılamadı", Toast.LENGTH_SHORT).show()
                        }
                        busy = null
                    }
                }
            )
            Text(
                "Not içinden tek bir notu PDF, TXT, Markdown ya da HTML olarak dışa aktarabilirsiniz (⋮ > Dışa aktar). " +
                    "Her yazdığınız harf anında telefondaki veritabanına kaydedilir.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            HorizontalDivider(Modifier.padding(top = 12.dp))
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(40.dp).clip(CircleShape))
                Spacer(Modifier.width(12.dp))
                Text("Not Defteri  ·  sürüm 2.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    newPasswordFor?.let { purpose ->
        NewPasswordDialog(
            onDismiss = { newPasswordFor = null },
            onSet = { pw ->
                Vault.setPassword(null, pw)
                hasPassword = true
                if (purpose == "lock") { Settings.appLock = true; appLock = true }
                newPasswordFor = null
                Toast.makeText(context, "Parola belirlendi", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (changePassword) {
        PasswordDialog(
            title = "Mevcut parola",
            onDismiss = { changePassword = false },
            onSubmit = { pw -> Vault.unlock(pw).also { if (it) { oldPasswordOk = pw; changePassword = false } } }
        )
    }

    oldPasswordOk?.let { old ->
        NewPasswordDialog(
            title = "Yeni parola",
            onDismiss = { oldPasswordOk = null },
            onSet = { pw ->
                val ok = Vault.setPassword(old, pw)
                oldPasswordOk = null
                Toast.makeText(context, if (ok) "Parola değiştirildi" else "Parola değiştirilemedi", Toast.LENGTH_SHORT).show()
            }
        )
    }

    busy?.let {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Text(it) })
    }
}

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val all by Repository.items.collectAsState()
    val trash = all.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    var confirmEmpty by remember { mutableStateOf(false) }
    SubScreen("Çöp kutusu", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (trash.isEmpty()) {
                EmptyHint("Çöp kutusu boş")
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Silinen öğeler 30 gün sonra kalıcı olarak silinir.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { confirmEmpty = true }) { Text("Boşalt") }
                }
                LazyColumn(
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(trash, key = { it.id }) { item ->
                        val paper = LocalPaper.current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .yellowPaper(paper)
                                .padding(start = 12.dp, top = 10.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        item.locked -> "Kilitli not"
                                        else -> item.title.ifBlank { Markdown.toPlain(item.content).trim().ifBlank { "(Başlıksız)" } }
                                    },
                                    fontWeight = FontWeight.SemiBold, color = paper.ink, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    (if (item.type == ItemType.TASK) "Görev" else "Not") + " · silinme: " + formatDateTime(item.deletedAt!!),
                                    fontSize = 12.sp, color = paper.inkSoft
                                )
                            }
                            IconButton(onClick = { Repository.restore(item.id) }) {
                                Icon(Icons.Filled.Restore, "Geri getir", tint = paper.ink)
                            }
                            IconButton(onClick = { Repository.deleteForever(item.id) }) {
                                Icon(Icons.Filled.DeleteForever, "Kalıcı sil", tint = Color(0xFFC62828))
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Çöp kutusu boşaltılsın mı?") },
            text = { Text("${trash.size} öğe kalıcı olarak silinecek. Bu işlem geri alınamaz.") },
            confirmButton = { Button(onClick = { Repository.emptyTrash(); confirmEmpty = false }) { Text("Kalıcı sil") } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Vazgeç") } }
        )
    }
}

/** Uygulama kilidi ekranı. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(110.dp).clip(CircleShape))
        Spacer(Modifier.height(20.dp))
        Text("Not Defteri kilitli", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        PasswordField(pw, { pw = it; error = false }, "Parola", error)
        if (error) Text("Parola yanlış", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (Vault.unlock(pw)) onUnlocked() else error = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Kilidi aç", fontSize = 17.sp) }
    }
}
