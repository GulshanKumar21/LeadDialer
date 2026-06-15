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

object BrochureSharer {

    suspend fun downloadAndShare(
        context    : Context,
        phone      : String,
        courseTitle: String,
        driveLink  : String,
        message    : String
    ): String? = withContext(Dispatchers.IO) {
        try {

            val pdfFile = downloadPdf(context, driveLink, courseTitle)
                ?: return@withContext "PDF download nahi hua. Internet check karein."

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )

            val clean     = phone.filter { it.isDigit() }
            val fullPhone = if (clean.startsWith("91") && clean.length == 12) clean else "91$clean"

            val pdfIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("jid", "$fullPhone@s.whatsapp.net") // direct to contact
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(pdfIntent, "Select WhatsApp Application").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            null // success

        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun downloadPdf(context: Context, url: String, title: String): File? {
        return try {
            val safeName = title.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
            val cacheFile = File(context.cacheDir, "$safeName.pdf")

            if (cacheFile.exists() && cacheFile.length() > 1000) return cacheFile

            var connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod          = "GET"
                connectTimeout         = 15_000
                readTimeout            = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

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
