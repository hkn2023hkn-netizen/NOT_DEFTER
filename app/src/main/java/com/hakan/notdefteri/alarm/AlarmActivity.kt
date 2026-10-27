package com.hakan.notdefteri.alarm

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakan.notdefteri.R
import com.hakan.notdefteri.data.Repository
import com.hakan.notdefteri.ui.NoteYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kilit ekranında da açılan alarm ekranı. Kapat'a basınca ses durur. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ITEM_ID, -1)
        val early = intent.getBooleanExtra(AlarmScheduler.EXTRA_EARLY, false)
        val item = Repository.get(id)?.let { if (it.locked) it.copy(title = "Kilitli not", content = "") else it }
        val time = item?.dueAt?.let { SimpleDateFormat("d MMMM yyyy  HH:mm", Locale.forLanguageTag("tr")).format(Date(it)) } ?: ""

        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0E1E36))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(120.dp))
                Spacer(Modifier.height(24.dp))
                Text(
                    if (early) "Yaklaşan hatırlatma" else "Zamanı geldi!",
                    color = Color.White, fontSize = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(NoteYellow)
                        .padding(20.dp)
                ) {
                    Text(item?.title?.ifBlank { "(Başlıksız)" } ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (!item?.content.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(item!!.content.take(300), fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(time, fontSize = 14.sp, color = Color.DarkGray)
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { dismiss(id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) { Text("Kapat", fontSize = 22.sp, textAlign = TextAlign.Center) }
            }
        }
    }

    private fun dismiss(id: Int) {
        getSystemService(NotificationManager::class.java).cancel(id)
        finish()
    }
}
