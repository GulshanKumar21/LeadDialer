package com.adyapan.leaddialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a PDF from a Google Drive link and shares it via WhatsApp with an optional text message.
 *
 * Google Drive link format:
 *   https://drive.google.com/uc?export=download&id=XXXX
 *
 * Usage (inside a coroutine):
 *   val result = BrochureSharer.downloadAndShare(context, phone, courseTitle, driveLink, message)
 */
object BrochureSharer {

    /**
     * Downloads the PDF, then opens WhatsApp with the PDF + message.
     * @return null on success, or an error string on failure.
     */
    suspend fun downloadAndShare(
        context    : Context,
        phone      : String,
        courseTitle: String,
        driveLink  : String,
        message    : String
    ): String? = withContext(Dispatchers.IO) {
        try {
            // ── 1. Resolve the final download URL ────────────────────────────
            // Google Drive "uc?export=download" sometimes redirects — follow it
            val pdfFile = downloadPdf(context, driveLink, courseTitle)
                ?: return@withContext "PDF download nahi hua. Internet check karein."

            // ── 2. Build FileProvider URI ─────────────────────────────────────
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )

            // ── 3. Build WhatsApp intent ──────────────────────────────────────
            val clean     = phone.filter { it.isDigit() }
            val fullPhone = if (clean.startsWith("91") && clean.length == 12) clean else "91$clean"

            // ── Step 1: Share PDF with caption via WhatsApp ──────────────────
            val pdfIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("jid", "$fullPhone@s.whatsapp.net") // direct to contact
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(pdfIntent)
            null // success

        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // ── Download PDF with redirect handling ───────────────────────────────────
    private fun downloadPdf(context: Context, url: String, title: String): File? {
        return try {
            // Sanitize filename
            val safeName = title.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
            val cacheFile = File(context.cacheDir, "$safeName.pdf")

            // Skip re-download if already cached (same session)
            if (cacheFile.exists() && cacheFile.length() > 1000) return cacheFile

            var connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod          = "GET"
                connectTimeout         = 15_000
                readTimeout            = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            // Handle Google Drive confirmation page (large file warning)
            // Extract confirm token if present
            var finalUrl = url
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307) {
                finalUrl = connection.getHeaderField("Location") ?: url
                connection.disconnect()
                connection = URL(finalUrl).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod          = "GET"
                    connectTimeout         = 15_000
                    readTimeout            = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
            }

            // Write stream to cache file
            connection.inputStream.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()

            if (cacheFile.length() > 500) cacheFile else null

        } catch (e: Exception) {
            android.util.Log.e("BrochureSharer", "Download error: ${e.message}")
            null
        }
    }
}
