package com.hakan.notdefteri.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private fun createRecognizer(context: Context): SpeechRecognizer? = when {
    SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
    Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    else -> null
}

/**
 * Uygulama içinde konuşmayı dinleyip yazıya çevirir. Siz "Bitti" diyene kadar dinlemeye devam eder,
 * söylenenler canlı olarak görünür. Telefonda konuşma tanıma servisi yoksa onUnavailable çağrılır.
 */
@Composable
fun VoiceDialog(onDone: (String) -> Unit, onCancel: () -> Unit, onUnavailable: () -> Unit) {
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
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf(true) } // "Bitti"ye basılana kadar yeniden dinle

    fun combined() = listOf(finalText, partial).filter { it.isNotBlank() }.joinToString(" ").trim()

    if (permitted) {
        DisposableEffect(Unit) {
            val recognizer = createRecognizer(context)
            if (recognizer == null) {
                onUnavailable()
                return@DisposableEffect onDispose { }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }
            fun start() {
                try {
                    recognizer.startListening(intent)
                } catch (e: Exception) {
                    error = "Dinleme başlatılamadı"
                }
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true; error = null }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onPartialResults(partialResults: Bundle?) {
                    partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (t.isNotBlank()) finalText = (finalText + " " + t).trim()
                    partial = ""
                    listening = false
                    if (active) start()
                }
                override fun onError(code: Int) {
                    listening = false
                    partial = ""
                    when (code) {
                        // Sessizlik / anlaşılamadı: dinlemeye devam et
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (active) start()
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { recognizer.cancel(); if (active) start() }
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                            error = "İnternet bağlantısı gerekiyor (hata $code)"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> error = "Mikrofon izni yok"
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            error = "Türkçe konuşma tanıma bu telefonda yok (hata $code)"
                        else -> error = "Konuşma tanınamadı (hata $code)"
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            start()
            onDispose {
                active = false
                try { recognizer.cancel() } catch (e: Exception) {}
                recognizer.destroy()
            }
        }
    }

    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                if (listening) Icons.Filled.Mic else Icons.Filled.MicOff, null, Modifier.size(40.dp),
                tint = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        title = { Text(if (denied) "Mikrofon izni gerekli" else if (listening) "Dinliyorum, konuşun..." else "Bekleyin...") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (denied) {
                    Text("Sesli not için mikrofon iznine izin verin. (Ayarlar > Uygulamalar > Not Defteri > İzinler)")
                } else {
                    val shown = combined()
                    Column(Modifier.heightIn(min = 60.dp, max = 260.dp).verticalScroll(rememberScrollState())) {
                        if (shown.isBlank()) {
                            Text("Söyledikleriniz burada yazıya dönüşecek.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(finalText, fontSize = 17.sp)
                            if (partial.isNotBlank()) Text(partial, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    error?.let {
                        Spacer(Modifier.width(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { active = false; onDone(combined()) }) { Text("Bitti") }
        },
        dismissButton = { TextButton(onClick = { active = false; onCancel() }) { Text("Vazgeç") } }
    )
}
