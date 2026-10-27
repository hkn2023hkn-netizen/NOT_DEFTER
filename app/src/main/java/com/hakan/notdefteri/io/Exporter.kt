package com.hakan.notdefteri.io

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.util.Base64
import androidx.core.content.FileProvider
import com.hakan.notdefteri.data.Item
import com.hakan.notdefteri.data.ItemType
import com.hakan.notdefteri.data.Markdown
import com.hakan.notdefteri.data.MdBlock
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    PDF("PDF", "pdf", "application/pdf"),
    TXT("Düz metin (TXT)", "txt", "text/plain"),
    MD("Markdown (.md)", "md", "text/markdown"),
    HTML("Web sayfası (HTML)", "html", "text/html"),
}

/** Notları dosyaya dönüştürür ve paylaşma penceresini açar. Başlık ve içerik çözülmüş olarak verilmelidir. */
object Exporter {
    private val TR = Locale.forLanguageTag("tr")
    private fun dateText(millis: Long) = SimpleDateFormat("d MMMM yyyy HH:mm", TR).format(Date(millis))

    private fun exportDir(context: Context) = File(context.cacheDir, "exports").apply {
        deleteRecursively(); mkdirs()
    }

    fun safeName(title: String, fallback: String): String =
        title.ifBlank { fallback }.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_").take(60).trim()

    private fun header(item: Item): String = buildString {
        if (item.type == ItemType.TASK) append(if (item.done) "Görev (tamamlandı)" else "Görev").append("  ·  ")
        append("Oluşturma: ").append(dateText(item.createdAt))
        item.dueAt?.let { append("  ·  Hatırlatma: ").append(dateText(it)) }
    }

    fun toMarkdown(item: Item, title: String, content: String): String = buildString {
        if (title.isNotBlank()) append("# ").append(title).append("\n\n")
        append("_").append(header(item)).append("_\n\n")
        append(content)
        append("\n")
    }

    fun toTxt(item: Item, title: String, content: String): String = buildString {
        if (title.isNotBlank()) append(title).append("\n").append("=".repeat(title.length.coerceAtMost(40))).append("\n\n")
        append(header(item)).append("\n\n")
        append(Markdown.toPlain(content)).append("\n")
    }

    private fun imageBase64(path: String, maxSide: Int = 1200): String? = try {
        val bmp = decodeScaled(path, maxSide)
        if (bmp == null) null else {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        null
    }

    fun toHtml(item: Item, title: String, content: String): String {
        val esc = { s: String -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") }
        val images = item.photos.mapNotNull { p -> imageBase64(p)?.let { "<img src=\"data:image/jpeg;base64,$it\">" } }
        return """<!doctype html>
<html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title.ifBlank { "Not" })}</title>
<style>
body{font-family:sans-serif;max-width:760px;margin:24px auto;padding:24px;background:#FFF9C4;color:#212121;line-height:1.6}
h1,h2,h3{line-height:1.25}.meta{color:#6D4C41;font-size:14px}
pre{background:#263238;color:#ECEFF1;padding:12px;border-radius:6px;overflow:auto}
code{font-family:monospace;background:rgba(0,0,0,.08);padding:1px 4px;border-radius:3px}pre code{background:none;padding:0}
blockquote{border-left:4px solid #FBC02D;margin:0;padding-left:12px;color:#5D4037}
li.check{list-style:none;margin-left:-20px}img{max-width:100%;border-radius:6px;margin:8px 0;display:block}
</style></head><body>
${if (title.isNotBlank()) "<h1>${esc(title)}</h1>" else ""}
<p class="meta">${esc(header(item))}</p>
${Markdown.toHtml(content)}
${images.joinToString("\n")}
</body></html>
"""
    }

    private fun decodeScaled(path: String, maxSide: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outWidth / sample > maxSide * 2 || opts.outHeight / sample > maxSide * 2) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Markdown'ı PDF'te kullanılacak biçimli metne çevirir. */
    private fun styled(content: String): CharSequence {
        val sb = SpannableStringBuilder()
        fun inline(text: String) {
            for (s in Markdown.spans(text)) {
                val start = sb.length
                sb.append(s.text)
                val end = sb.length
                if (s.bold && s.italic) sb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                else if (s.bold) sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                else if (s.italic) sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (s.strike) sb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (s.link != null) sb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (s.code) {
                    sb.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(BackgroundColorSpan(0x22000000), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        for (b in Markdown.parse(content)) {
            when (b) {
                is MdBlock.Heading -> {
                    val start = sb.length
                    inline(b.text)
                    val size = when (b.level) { 1 -> 20; 2 -> 17; else -> 15 }
                    sb.setSpan(AbsoluteSizeSpan(size), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is MdBlock.Paragraph -> inline(b.text)
                is MdBlock.Bullet -> { sb.append("    ".repeat(b.indent)).append("•  "); inline(b.text) }
                is MdBlock.Numbered -> { sb.append("    ".repeat(b.indent)).append(b.number).append(".  "); inline(b.text) }
                is MdBlock.Check -> { sb.append("    ".repeat(b.indent)).append(if (b.checked) "☑  " else "☐  "); inline(b.text) }
                is MdBlock.Quote -> {
                    val start = sb.length
                    sb.append("│ "); inline(b.text)
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is MdBlock.Code -> {
                    val start = sb.length
                    sb.append(b.code)
                    sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(BackgroundColorSpan(0x18000000), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                MdBlock.Rule -> sb.append("──────────────────────")
                MdBlock.Blank -> {}
            }
            sb.append("\n")
        }
        return sb
    }

    fun writePdf(item: Item, title: String, content: String, file: File) {
        val pageW = 595
        val pageH = 842
        val margin = 48
        val width = pageW - margin * 2
        val doc = PdfDocument()
        var pageNum = 0
        var page: PdfDocument.Page? = null
        var y = 0

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            page!!.canvas.drawColor(Color.rgb(255, 253, 231))
            y = margin
        }

        /** Metni sayfalara bölerek çizer. */
        fun drawText(text: CharSequence, paint: TextPaint) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
            var line = 0
            while (line < layout.lineCount) {
                if (y >= pageH - margin) newPage()
                val available = pageH - margin - y
                val startTop = layout.getLineTop(line)
                var last = line
                while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - startTop <= available) last++
                if (layout.getLineBottom(last) - startTop > available && y > margin) { newPage(); continue }
                val height = layout.getLineBottom(last) - startTop
                val canvas: Canvas = page!!.canvas
                canvas.save()
                canvas.translate(margin.toFloat(), (y - startTop).toFloat())
                canvas.clipRect(0, startTop, width, startTop + height)
                layout.draw(canvas)
                canvas.restore()
                y += height
                line = last + 1
            }
        }

        newPage()
        if (title.isNotBlank()) {
            drawText(title, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK })
            y += 4
        }
        drawText(header(item), TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.rgb(109, 76, 65) })
        y += 12
        drawText(styled(content), TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = Color.rgb(33, 33, 33) })

        for (path in item.photos) {
            val bmp = decodeScaled(path, 1200) ?: continue
            val scale = minOf(width.toFloat() / bmp.width, (pageH - margin * 2).toFloat() / bmp.height, 1f * width / bmp.width)
            val w = (bmp.width * scale).toInt()
            val h = (bmp.height * scale).toInt()
            y += 12
            if (y + h > pageH - margin) newPage()
            page!!.canvas.drawBitmap(bmp, null, android.graphics.Rect(margin, y, margin + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h
            bmp.recycle()
        }
        page?.let { doc.finishPage(it) }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    /** Tek bir notu seçilen biçimde dosyaya yazar (arka planda çağrılmalı). Paylaşmak için [share]. */
    fun createItemFile(context: Context, item: Item, title: String, content: String, format: ExportFormat): File {
        val name = safeName(title, if (item.type == ItemType.TASK) "Gorev" else "Not") + "." + format.ext
        val file = File(exportDir(context), name)
        when (format) {
            ExportFormat.PDF -> writePdf(item, title, content, file)
            ExportFormat.TXT -> file.writeText(toTxt(item, title, content))
            ExportFormat.MD -> file.writeText(toMarkdown(item, title, content))
            ExportFormat.HTML -> file.writeText(toHtml(item, title, content))
        }
        return file
    }

    /**
     * Tüm notları ve görevleri (kilitli olanlar hariç) Markdown dosyaları ve fotoğraflarıyla bir ZIP'e koyar.
     * Bu dosya yedek olarak saklanıp daha sonra İçe aktar ile geri yüklenebilir.
     */
    fun createAllZip(context: Context, items: List<Item>): Pair<File, Int> {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", TR).format(Date())
        val file = File(exportDir(context), "NotDefteri_$stamp.zip")
        val used = mutableSetOf<String>()
        var count = 0
        ZipOutputStream(file.outputStream()).use { zip ->
            for (item in items.filter { !it.locked && it.deletedAt == null }) {
                val folder = if (item.type == ItemType.TASK) "Gorevler" else "Notlar"
                var base = safeName(item.title, "${if (item.type == ItemType.TASK) "Gorev" else "Not"}_${item.id}")
                while (!used.add("$folder/$base")) base += "_${item.id}"
                val photoNames = item.photos.mapIndexedNotNull { i, p ->
                    val f = File(p)
                    if (!f.exists()) return@mapIndexedNotNull null
                    val n = "resimler/${base}_${i + 1}.${f.extension.ifBlank { "jpg" }}"
                    zip.putNextEntry(ZipEntry("$folder/$n"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    n
                }
                val md = toMarkdown(item, item.title, item.content) +
                    photoNames.joinToString("") { "\n![]($it)" }
                zip.putNextEntry(ZipEntry("$folder/$base.md"))
                zip.write(md.toByteArray())
                zip.closeEntry()
                count++
            }
        }
        return file to count
    }

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Dışa aktar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
