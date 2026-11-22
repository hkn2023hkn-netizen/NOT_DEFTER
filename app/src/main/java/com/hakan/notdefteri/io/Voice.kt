package com.hakan.notdefteri.io

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContract
import com.hakan.notdefteri.data.Repository
import java.io.File
import java.util.UUID

/**
 * Sesli not: telefonun konuşma tanıma ekranını açar, söyleneni yazıya çevirir.
 * Destekleyen telefonlarda (Google uygulaması) ses kaydının kendisi de alınıp nota eklenir.
 */
object Voice {
    data class Result(val text: String, val audioPath: String?)

    class Contract : ActivityResultContract<Unit, Result?>() {
        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Konuşun, söyledikleriniz yazıya dönecek")
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                // Ses kaydını da iste (Google konuşma tanıma destekler)
                putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR")
                putExtra("android.speech.extra.GET_AUDIO", true)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Result? {
            if (resultCode != Activity.RESULT_OK || intent == null) return null
            val text = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            val audio = intent.data?.let { uri ->
                try {
                    val target = File(Repository.audioDir, "SES_${UUID.randomUUID()}.amr")
                    Repository.appContext.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                    if (target.length() > 0) target.absolutePath else { target.delete(); null }
                } catch (e: Exception) {
                    null
                }
            }
            if (text.isBlank() && audio == null) return null
            return Result(text, audio)
        }
    }
}
