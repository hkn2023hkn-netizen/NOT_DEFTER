package com.hakan.notdefteri.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hakan.notdefteri.io.VoiceSession
import kotlinx.coroutines.delay

/**
 * Sesli not penceresi: konuşmayı kaydeder ve aynı anda yazıya çevirir.
 * "Bitti" denince yazı ve ses dosyası birlikte döner.
 */
@Composable
fun VoiceDialog(onDone: (text: String, audioPath: String?) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var permitted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permitted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!permitted) askPermission.launch(Manifest.permission.RECORD_AUDIO) }

    var finalText by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableLongStateOf(0L) }
    val session = remember { arrayOfNulls<VoiceSession>(1) }

    if (permitted) {
        DisposableEffect(Unit) {
            val s = VoiceSession(
                context,
                onText = { f, p -> finalText = f; partial = p },
                onStatus = { rec, msg -> recording = rec; if (msg != null || rec) message = msg }
            )
            session[0] = s
            recording = s.start()
            onDispose {
                // Pencere "Bitti"/"Vazgeç" dışında kapanırsa kaydı at
                session[0]?.cancel()
                session[0] = null
            }
        }
        LaunchedEffect(recording) {
            while (recording) { delay(1000); seconds++ }
        }
    }

    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                Icons.Filled.FiberManualRecord, null, Modifier.size(36.dp),
                tint = if (recording) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        title = {
            Text(
                when {
                    denied -> "Mikrofon izni gerekli"
                    recording -> "Kaydediliyor  %d:%02d".format(seconds / 60, seconds % 60)
                    else -> "Bekleyin..."
                }
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (denied) {
                    Text("Sesli not için mikrofon iznine izin verin. (Ayarlar > Uygulamalar > Not Defteri > İzinler)")
                } else {
                    Column(Modifier.heightIn(min = 60.dp, max = 260.dp).verticalScroll(rememberScrollState())) {
                        if (finalText.isBlank() && partial.isBlank()) {
                            Text("Konuşun. Ses kaydedilir, söyledikleriniz burada yazıya dönüşür.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            if (finalText.isNotBlank()) Text(finalText, fontSize = 17.sp)
                            if (partial.isNotBlank()) Text(partial, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = session[0]
                session[0] = null
                val audio = s?.finish()
                onDone(listOf(finalText, partial).filter { it.isNotBlank() }.joinToString(" ").trim(), audio)
            }) { Text("Bitti") }
        },
        dismissButton = {
            TextButton(onClick = {
                session[0]?.cancel()
                session[0] = null
                onCancel()
            }) { Text("Vazgeç") }
        }
    )
}
