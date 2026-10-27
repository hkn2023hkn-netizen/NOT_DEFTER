package com.hakan.notdefteri

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hakan.notdefteri.data.Settings
import com.hakan.notdefteri.security.Vault
import com.hakan.notdefteri.ui.AppRoot
import com.hakan.notdefteri.ui.LockScreen
import com.hakan.notdefteri.ui.NotDefteriTheme

class MainActivity : ComponentActivity() {
    /** Uygulama kilidi açık mı (parola ekranı gösterilsin mi). */
    private var appLocked by mutableStateOf(false)
    private var stoppedAt = 0L

    private fun lockEnabled() = Settings.appLock && Vault.hasPassword

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appLocked = lockEnabled() && !Vault.isUnlocked
        setContent {
            NotDefteriTheme {
                val notifPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (appLocked) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .safeDrawingPadding()
                    ) {
                        LockScreen(onUnlocked = { appLocked = false })
                    }
                } else {
                    AppRoot(
                        needsFullScreenPermission = needsFullScreenPermission(),
                        onOpenFullScreenSettings = { openFullScreenSettings() }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Kamera / galeriye gidip dönmek kilitlemesin diye 60 saniye tolerans
        if (lockEnabled() && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 60_000) {
            Vault.lock()
            appLocked = true
        }
    }

    private fun needsFullScreenPermission(): Boolean =
        Build.VERSION.SDK_INT >= 34 &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
            )
        }
    }
}
