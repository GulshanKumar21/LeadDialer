package com.adyapan.leaddialer


import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallExcelWriter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    suspend fun export(context: Context, records: List<CallRecord>): Boolean {
        return try {
            val workbook = XSSFWorkbook()
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

            records.forEachIndexed { index, record ->
                val rowStyle = workbook.createCellStyle().apply {
                    fillForegroundColor = when (record.status) {
                        "Connected"      -> IndexedColors.LIGHT_GREEN.index
                        "Interested"     -> IndexedColors.LIGHT_BLUE.index
                        "Busy"           -> IndexedColors.LIGHT_ORANGE.index
                        "Not Interested" -> IndexedColors.ROSE.index
                        else             -> IndexedColors.WHITE.index
                    }
                    fillPattern = FillPatternType.SOLID_FOREGROUND
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

            val fileName = "CallRecords_${
                SimpleDateFormat("ddMMyyyy_HHmm", Locale.getDefault()).format(Date())
            }.xlsx"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri      = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os -> workbook.write(os) }
                    cv.clear()
                    cv.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(it, cv, null, null)
                }
            } else {
                val dir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, fileName)
                FileOutputStream(file).use { workbook.write(it) }
            }

            workbook.close()
            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}