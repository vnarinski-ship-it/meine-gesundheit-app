package de.vnarinski.meinegesundheit.report

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

object DoctorPdfExporter {
    fun create(context: Context, fileTitle: String, content: String): android.net.Uri {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safe = fileTitle.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val file = File(dir, "$safe.pdf")
        val pdf = PdfDocument()
        val paint = Paint().apply { textSize = 12f; isAntiAlias = true }
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val width = 595; val height = 842; val margin = 42f; val lineHeight = 17f
        var pageNo = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
        var canvas = page.canvas
        var y = margin
        canvas.drawText("Meine Gesundheit – Arztbericht", margin, y, titlePaint); y += 28f
        fun newPage() {
            pdf.finishPage(page); pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
            canvas = page.canvas; y = margin
        }
        for (paragraph in content.lines()) {
            val words = paragraph.split(" ")
            var line = ""
            if (words.isEmpty()) { y += lineHeight; continue }
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > width - margin * 2 && line.isNotEmpty()) {
                    if (y > height - margin) newPage()
                    canvas.drawText(line, margin, y, paint); y += lineHeight
                    line = word
                } else line = candidate
            }
            if (y > height - margin) newPage()
            canvas.drawText(line, margin, y, paint); y += lineHeight
        }
        pdf.finishPage(page)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
