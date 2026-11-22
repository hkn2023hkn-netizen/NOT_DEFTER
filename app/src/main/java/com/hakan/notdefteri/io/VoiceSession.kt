package com.hakan.notdefteri.io

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.hakan.notdefteri.data.Repository
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Sesli not oturumu: mikrofonu uygulama kendisi dinler, sesi WAV dosyasına kaydeder ve aynı sesi
 * konuşma tanıma servisine verir. Böylece hem ses kaydı saklanır hem de yazıya çevrilir.
 *
 * Android 13 ve üstünde tanıma servisine ses doğrudan verilebiliyor. Servis bunu desteklemezse
 * ya da telefon daha eskiyse kayıt yine yapılır; yazıya çevirme yapılamadığı bildirilir.
 */
class VoiceSession(
    private val context: Context,
    private val onText: (final: String, partial: String) -> Unit,
    private val onStatus: (listening: Boolean, message: String?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val rate = 16000
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var pipe: OutputStream? = null
    private var pipeRead: ParcelFileDescriptor? = null
    // Kayıt hiç beklemesin diye tanıyıcıya giden ses ayrı bir kuyruktan, ayrı bir iş parçacığıyla gönderilir
    private val queue = java.util.concurrent.ArrayBlockingQueue<ByteArray>(256)
    private var pipeThread: Thread? = null
    private var recognizer: SpeechRecognizer? = null
    private var triedOnDevice = false
    private var transcribing = true
    private var gotAnyResult = false
    private val wav = File(Repository.audioDir, "SES_${UUID.randomUUID()}.wav")
    private var finalText = ""
    private var errorsInRow = 0

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8192) * 2)
        } catch (e: Exception) {
            null
        }
        if (r == null || r.state != AudioRecord.STATE_INITIALIZED) {
            r?.release()
            onStatus(false, "Mikrofon açılamadı")
            return false
        }
        record = r
        running = true
        val out = FileOutputStream(wav)
        out.write(ByteArray(44)) // WAV başlığı sonra yazılır
        r.startRecording()
        thread = Thread {
            val buf = ByteArray(4096)
            try {
                while (running) {
                    val n = r.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    out.write(buf, 0, n)
                    if (pipe != null) queue.offer(buf.copyOf(n))
                }
            } finally {
                try { out.close() } catch (e: Exception) {}
            }
        }.apply { start() }
        pipeThread = Thread {
            while (running) {
                val chunk = try { queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { null } ?: continue
                val p = pipe ?: continue
                try { p.write(chunk) } catch (e: Exception) { if (pipe === p) pipe = null }
            }
        }.apply { start() }

        if (Build.VERSION.SDK_INT >= 33) {
            startRecognizer(onDevice = false)
        } else {
            transcribing = false
            onStatus(true, "Ses kaydediliyor. Bu Android sürümünde aynı anda yazıya çevrilemiyor.")
        }
        return true
    }

    private fun createRecognizer(onDevice: Boolean): SpeechRecognizer? = try {
        when {
            onDevice && Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            !onDevice && SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun startRecognizer(onDevice: Boolean) {
        recognizer?.destroy()
        val rec = createRecognizer(onDevice)
        if (rec == null) {
            if (!onDevice && !triedOnDevice) { triedOnDevice = true; startRecognizer(true); return }
            stopTranscribing()
            return
        }
        recognizer = rec
        rec.setRecognitionListener(listener)
        listenSegment()
    }

    private fun listenSegment() {
        val rec = recognizer ?: return
        if (!running || Build.VERSION.SDK_INT < 33) return
        closePipe()
        val (readSide, writeSide) = ParcelFileDescriptor.createPipe()
        pipeRead = readSide
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, rate)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        queue.clear()
        pipe = ParcelFileDescriptor.AutoCloseOutputStream(writeSide)
        try {
            rec.startListening(intent)
        } catch (e: Exception) {
            stopTranscribing()
        }
    }

    private fun closePipe() {
        val p = pipe
        pipe = null
        try { p?.close() } catch (e: Exception) {}
        try { pipeRead?.close() } catch (e: Exception) {}
        pipeRead = null
    }

    private fun stopTranscribing() {
        transcribing = false
        closePipe()
        recognizer?.destroy()
        recognizer = null
        onStatus(true, "Ses kaydediliyor. Telefon bu kaydı aynı anda yazıya çeviremedi.")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { errorsInRow = 0; onStatus(true, null) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            onText(finalText, p)
        }
        override fun onResults(results: Bundle?) {
            gotAnyResult = true
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (t.isNotBlank()) finalText = (finalText + " " + t).trim()
            onText(finalText, "")
            if (running && transcribing) main.post { listenSegment() }
        }
        override fun onError(code: Int) {
            if (++errorsInRow > 5) { main.post { if (running && transcribing) stopTranscribing() }; return }
            when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    gotAnyResult = true
                    if (running && transcribing) main.post { listenSegment() }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (running && transcribing) main.postDelayed({ listenSegment() }, 300)
                SpeechRecognizer.ERROR_CLIENT -> if (running && transcribing) main.postDelayed({ listenSegment() }, 300)
                else -> {
                    // Bu servis sesi bizden almayı desteklemiyor olabilir: telefondaki diğer tanıyıcıyı dene
                    if (!gotAnyResult && !triedOnDevice) {
                        triedOnDevice = true
                        main.post { if (running) startRecognizer(onDevice = true) }
                    } else {
                        main.post { if (running) stopTranscribing() }
                    }
                }
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Kaydı bitirir. Ses dosyasının yolunu döner (kayıt çok kısaysa null). */
    fun finish(): String? {
        running = false
        closePipe()
        try { recognizer?.cancel() } catch (e: Exception) {}
        recognizer?.destroy()
        recognizer = null
        try { record?.stop() } catch (e: Exception) {}
        thread?.join(1500)
        pipeThread?.join(500)
        record?.release()
        record = null
        val dataLen = wav.length() - 44
        if (dataLen < rate * 2 / 2) { // yarım saniyeden kısa
            wav.delete(); return null
        }
        writeHeader(dataLen)
        return wav.absolutePath
    }

    fun cancel() {
        finish()?.let { File(it).delete() }
    }

    private fun writeHeader(dataLen: Long) {
        RandomAccessFile(wav, "rw").use { f ->
            fun int(v: Int) { f.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())) }
            fun short(v: Int) { f.write(byteArrayOf(v.toByte(), (v shr 8).toByte())) }
            f.seek(0)
            f.writeBytes("RIFF"); int((36 + dataLen).toInt()); f.writeBytes("WAVE")
            f.writeBytes("fmt "); int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
            f.writeBytes("data"); int(dataLen.toInt())
        }
    }
}
