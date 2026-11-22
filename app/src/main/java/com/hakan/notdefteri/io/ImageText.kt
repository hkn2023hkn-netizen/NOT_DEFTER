package com.hakan.notdefteri.io

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Fotoğraftaki yazıyı telefonda (internetsiz) okur. Türkçe harfler Latin alfabesi modeliyle tanınır. */
object ImageText {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Okunan yazı; yazı yoksa ya da okunamazsa boş metin. */
    suspend fun read(context: Context, path: String): String {
        val image = try {
            InputImage.fromFilePath(context, Uri.fromFile(File(path)))
        } catch (e: Exception) {
            return ""
        }
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    // Blokları paragraf, satırları satır olarak koru
                    val text = result.textBlocks.joinToString("\n\n") { block -> block.lines.joinToString("\n") { it.text } }
                    cont.resume(text.trim())
                }
                .addOnFailureListener { cont.resume("") }
        }
    }
}
