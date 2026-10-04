package de.vnarinski.ocrhub.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.vnarinski.ocrhub.data.DocumentStore
import de.vnarinski.ocrhub.model.DocumentRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

data class OcrResult(val text: String, val pageCount: Int)

class OcrEngine(private val context: Context, private val store: DocumentStore) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(rec: DocumentRecord): OcrResult = withContext(Dispatchers.IO) {
        if (rec.mimeType == "application/pdf") recognizePdf(rec) else recognizeImage(rec)
    }

    private suspend fun recognizeImage(rec: DocumentRecord): OcrResult {
        val bitmap = BitmapFactory.decodeByteArray(store.bytes(rec), 0, store.bytes(rec).size)
            ?: error("Bild konnte nicht dekodiert werden")
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        bitmap.recycle()
        return OcrResult(text, 1)
    }

    private suspend fun recognizePdf(rec: DocumentRecord): OcrResult {
        val temp = File.createTempFile("ocrhub-", ".pdf", context.cacheDir)
        temp.writeBytes(store.bytes(rec))
        val fd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val out = StringBuilder()
        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = 2
                val bmp = android.graphics.Bitmap.createBitmap(page.width * scale, page.height * scale, android.graphics.Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val text = recognizer.process(InputImage.fromBitmap(bmp, 0)).await().text
                if (i > 0) out.append("\n\n--- Seite ").append(i + 1).append(" ---\n")
                out.append(text)
                bmp.recycle()
                page.close()
            }
            return OcrResult(out.toString(), renderer.pageCount)
        } finally {
            renderer.close()
            fd.close()
            temp.delete()
        }
    }
}
