package com.hakan.notdefteri.ui

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Nottaki ses kayıtları: dinle / durdur / kaldır. Aynı anda tek kayıt çalar. */
@Composable
fun AudioList(paths: List<String>, onRemove: (String) -> Unit) {
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }

    fun stop() {
        player[0]?.run { try { stop() } catch (e: Exception) {}; release() }
        player[0] = null
        playing = null
    }

    fun play(path: String) {
        stop()
        try {
            player[0] = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playing = path
        } catch (e: Exception) {
            stop()
        }
    }

    DisposableEffect(Unit) { onDispose { stop() } }

    Column {
        paths.forEachIndexed { i, path ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Mic, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Ses kaydı ${i + 1}", Modifier.weight(1f))
                IconButton(onClick = { if (playing == path) stop() else play(path) }) {
                    Icon(if (playing == path) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (playing == path) "Durdur" else "Dinle")
                }
                IconButton(onClick = { if (playing == path) stop(); onRemove(path) }) {
                    Icon(Icons.Filled.Close, "Kaydı kaldır")
                }
            }
        }
    }
}
