package com.adyapan.leaddialer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallExcelWriter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /**
     * Exports call records.
     * Tries to write a high-fidelity Excel (.xls) file using Apache POI.
     * If POI classes fail to load or throw any error, it gracefully falls back to
     * a pure Kotlin/Java CSV exporter (.csv), which is 100% reliable and compatible with Excel/Sheets.
     *
     * @return Status message to be displayed directly to the user as a Toast.
     */
    suspend fun export(context: Context, records: List<CallRecord>): String {
        // Use a high-precision timestamp with seconds to guarantee filename uniqueness
        val timestamp = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())
        val baseFileName = "CallRecords_$timestamp"

        // 1. Try Excel (.xls) via POI in a separate method to isolate classloading
        try {
            val fileName = "$baseFileName.xls"
            val success = exportXls(context, records, fileName)
            if (success) {
                return "Excel file saved to Downloads! ($fileName)"
            } else {
                throw Exception("Write to storage failed")
            }
        } catch (poiError: Throwable) {
            // Excel failed or POI library crashed — log it and fall back to CSV!
            android.util.Log.e("CallExcelWriter", "Excel export failed, falling back to CSV. Error: ${poiError.message}", poiError)

            // 2. Fall back to pure Kotlin CSV export (100% reliable)
            val csvFileName = "$baseFileName.csv"
            val csvSuccess = exportCsv(context, records, csvFileName)
            if (csvSuccess) {
                return "Excel error, saved as CSV to Downloads: $csvFileName"
            } else {
                return "Download failed: ${poiError.localizedMessage ?:"Storage write failed"}"
            }
        }
    }

    private fun exportXls(context: Context, records: List<CallRecord>, fileName: String): Boolean {
        val workbook = HSSFWorkbook()
        val sheet    = workbook.createSheet("Call Records")

        sheet.setColumnWidth(0, 4000)
        sheet.setColumnWidth(1, 3500)
        sheet.setColumnWidth(2, 2500)
        sheet.setColumnWidth(3, 3000)
        sheet.setColumnWidth(4, 4000)

        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_TEAL.index
            fillPattern         = FillPatternType.SOLID_FOREGROUND
            alignment           = HorizontalAlignment.CENTER
        }
        val headerFont = workbook.createFont().apply {
            bold               = true
            color              = IndexedColors.WHITE.index
            fontHeightInPoints = 12
        }
        headerStyle.setFont(headerFont)

        val headers = listOf("Name", "Phone", "Duration", "Status", "Date")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, title ->
            headerRow.createCell(i).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        // Pre-defined styles to avoid POI style limit (max 4000)
        val connectedStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern         = FillPatternType.SOLID_FOREGROUND
        }
        val interestedStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_BLUE.index
            fillPattern         = FillPatternType.SOLID_FOREGROUND
        }
        val busyStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_ORANGE.index
            fillPattern         = FillPatternType.SOLID_FOREGROUND
        }
        val notInterestedStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.ROSE.index
            fillPattern         = FillPatternType.SOLID_FOREGROUND
        }
        val defaultStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.WHITE.index
            fillPattern         = FillPatternType.SOLID_FOREGROUND
        }

        records.forEachIndexed { index, record ->
            val rowStyle = when (record.status) {
                "Connected"      -> connectedStyle
                "Interested"     -> interestedStyle
                "Busy"           -> busyStyle
                "Not Interested" -> notInterestedStyle
                else             -> defaultStyle
            }

            val row = sheet.createRow(index + 1)
            row.createCell(0).apply { setCellValue(record.name);    cellStyle = rowStyle }
            row.createCell(1).apply { setCellValue(record.phone);   cellStyle = rowStyle }
            row.createCell(2).apply {
                setCellValue(CallManager.formatDuration(record.duration))
                cellStyle = rowStyle
            }
            row.createCell(3).apply { setCellValue(record.status);  cellStyle = rowStyle }
            row.createCell(4).apply {
                setCellValue(
                    if (record.calledAt > 0)
                        dateFormat.format(Date(record.calledAt))
                    else "—"
                )
                cellStyle = rowStyle
            }
        }

        val success = writeExcelToStorage(context, workbook, fileName)
        workbook.close()
        return success
    }

    private fun writeExcelToStorage(context: Context, workbook: HSSFWorkbook, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri      = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os -> workbook.write(os) }
                    cv.clear()
                    cv.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, cv, null, null)
                    true
                } else {
                    false
                }
            } else {
                val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { workbook.write(it) }
                true
            }
        } catch (e: Throwable) {
            android.util.Log.e("CallExcelWriter", "writeExcelToStorage failed: ${e.message}", e)
            false
        }
    }

    private fun exportCsv(context: Context, records: List<CallRecord>, fileName: String): Boolean {
        return try {
            val csvBuilder = StringBuilder()
            // Header
            csvBuilder.append("Name,Phone,Duration,Status,Date\n")
            // Rows
            for (record in records) {
                val name = escapeCsv(record.name)
                val phone = escapeCsv(record.phone)
                val duration = escapeCsv(CallManager.formatDuration(record.duration))
                val status = escapeCsv(record.status)
                val date = escapeCsv(
                    if (record.calledAt > 0)
                        dateFormat.format(Date(record.calledAt))
                    else "—"
                )
                csvBuilder.append("$name,$phone,$duration,$status,$date\n")
            }

            val csvContent = csvBuilder.toString()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri      = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(csvContent.toByteArray(Charsets.UTF_8))
                    }
                    cv.clear()
                    cv.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, cv, null, null)
                    true
                } else {
                    false
                }
            } else {
                val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { os ->
                    os.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                true
            }
        } catch (e: Throwable) {
            android.util.Log.e("CallExcelWriter", "exportCsv failed: ${e.message}", e)
            false
        }
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return "\"$clean\""
    }
}