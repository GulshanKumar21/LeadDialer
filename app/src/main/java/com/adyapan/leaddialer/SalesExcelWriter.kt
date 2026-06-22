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

object SalesExcelWriter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /**
     * Exports completed sales.
     */
    suspend fun export(context: Context, sales: List<SaleRecord>): String {
        val timestamp = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())
        val baseFileName = "SalesReport_$timestamp"

        // Sort by employee name, then date descending
        val sortedSales = sales.sortedWith(
            compareBy<SaleRecord> { it.employeeName.lowercase() }
                .thenByDescending { it.calledAt }
        )

        try {
            val fileName = "$baseFileName.xls"
            val success = exportXls(context, sortedSales, fileName)
            if (success) {
                return "✅ Sales Excel saved to Downloads! ($fileName)"
            } else {
                throw Exception("Write to storage failed")
            }
        } catch (poiError: Throwable) {
            android.util.Log.e("SalesExcelWriter", "Excel export failed, falling back to CSV. Error: ${poiError.message}", poiError)

            val csvFileName = "$baseFileName.csv"
            val csvSuccess = exportCsv(context, sortedSales, csvFileName)
            if (csvSuccess) {
                return "⚠️ Excel error, saved as CSV to Downloads: $csvFileName"
            } else {
                return "❌ Download failed: ${poiError.localizedMessage ?: "Storage write failed"}"
            }
        }
    }

    private fun exportXls(context: Context, sales: List<SaleRecord>, fileName: String): Boolean {
        val workbook = HSSFWorkbook()
        val sheet    = workbook.createSheet("Sales Records")

        sheet.setColumnWidth(0, 4500) // Employee Name
        sheet.setColumnWidth(1, 4500) // Customer Name
        sheet.setColumnWidth(2, 3500) // Phone
        sheet.setColumnWidth(3, 4000) // College Name
        sheet.setColumnWidth(4, 4000) // College City
        sheet.setColumnWidth(5, 4500) // Date
        sheet.setColumnWidth(6, 6000) // Notes

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

        val headers = listOf("Employee Name", "Customer Name", "Phone", "College Name", "College City", "Date", "Notes")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, title ->
            headerRow.createCell(i).apply {
                setCellValue(title)
                cellStyle = headerStyle
            }
        }

        val saleStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern         = FillPatternType.SOLID_FOREGROUND
        }

        sales.forEachIndexed { index, sale ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).apply { setCellValue(sale.employeeName); cellStyle = saleStyle }
            row.createCell(1).apply { setCellValue(sale.name);         cellStyle = saleStyle }
            row.createCell(2).apply { setCellValue(sale.phone);        cellStyle = saleStyle }
            row.createCell(3).apply { setCellValue(sale.collegeName);  cellStyle = saleStyle }
            row.createCell(4).apply { setCellValue(sale.collegeCity);  cellStyle = saleStyle }
            row.createCell(5).apply {
                setCellValue(
                    if (sale.calledAt > 0) dateFormat.format(Date(sale.calledAt)) else "—"
                )
                cellStyle = saleStyle
            }
            row.createCell(6).apply { setCellValue(sale.notes);        cellStyle = saleStyle }
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
            android.util.Log.e("SalesExcelWriter", "writeExcelToStorage failed: ${e.message}", e)
            false
        }
    }

    private fun exportCsv(context: Context, sales: List<SaleRecord>, fileName: String): Boolean {
        return try {
            val csvBuilder = StringBuilder()
            csvBuilder.append("Employee Name,Customer Name,Phone,College Name,College City,Date,Notes\n")
            for (sale in sales) {
                val empName = escapeCsv(sale.employeeName)
                val custName = escapeCsv(sale.name)
                val phone = escapeCsv(sale.phone)
                val college = escapeCsv(sale.collegeName)
                val city = escapeCsv(sale.collegeCity)
                val date = escapeCsv(
                    if (sale.calledAt > 0) dateFormat.format(Date(sale.calledAt)) else "—"
                )
                val notes = escapeCsv(sale.notes)
                csvBuilder.append("$empName,$custName,$phone,$college,$city,$date,$notes\n")
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
            android.util.Log.e("SalesExcelWriter", "exportCsv failed: ${e.message}", e)
            false
        }
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return "\"$clean\""
    }
}
