package com.adyapan.leaddialer

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader

object ExcelUtils {

    private const val TAG = "ExcelUtils"

    // ── Public entry-point ────────────────────────────────────────────────────
    fun parseLeads(context: Context, uri: Uri): List<Lead> {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val path     = uri.lastPathSegment?.lowercase() ?: ""

        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "parseLeads START")
        Log.d(TAG, "URI: $uri")
        Log.d(TAG, "MIME type: $mimeType")
        Log.d(TAG, "Path: $path")
        Log.d(TAG, "═══════════════════════════════════════")

        return when {
            mimeType.contains("csv", ignoreCase = true) ||
                    path.endsWith(".csv") -> parseCsv(context, uri)

            else -> parseExcel(context, uri)   // handles .xls and .xlsx via POI
        }
    }

    // ── Helper: Detect delimiter in CSV ───────────────────────────────────────
    private fun detectDelimiter(line: String): String {
        val commas     = line.count { it == ',' }
        val semicolons = line.count { it == ';' }
        val tabs       = line.count { it == '\t' }

        Log.d(TAG, "Delimiter detection: commas=$commas, semicolons=$semicolons, tabs=$tabs")

        return when {
            tabs > 0 && tabs > commas && tabs > semicolons -> {
                Log.d(TAG, "Using TAB delimiter"); "\t"
            }
            semicolons > commas -> {
                Log.d(TAG, "Using SEMICOLON delimiter"); ";"
            }
            else -> {
                Log.d(TAG, "Using COMMA delimiter"); ","
            }
        }
    }

    // ── CSV parser ────────────────────────────────────────────────────────────
    private fun parseCsv(context: Context, uri: Uri): List<Lead> {
        val leads = mutableListOf<Lead>()
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: run {
                    Log.e(TAG, "❌ Cannot open CSV stream")
                    return emptyList()
                }

            val lines = BufferedReader(InputStreamReader(stream)).readLines()
            stream.close()

            if (lines.isEmpty()) {
                Log.w(TAG, "❌ CSV is empty")
                return emptyList()
            }

            Log.d(TAG, "✅ CSV loaded: ${lines.size} lines")

            val delimiter    = detectDelimiter(lines[0])
            val headerValues = lines[0].split(delimiter).map { it.trim().lowercase() }
            val (nameCol, phoneCol, collegeNameCol, collegeCityCol) = detectColumns(headerValues)

            Log.d(TAG, "━━━ Column Detection Results ━━━")
            Log.d(TAG, "Name col: $nameCol  Phone col: $phoneCol")
            Log.d(TAG, "CollegeName col: $collegeNameCol  City col: $collegeCityCol")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            var rowsParsed  = 0
            var rowsSkipped = 0

            for (i in 1 until lines.size) {
                try {
                    val cols = lines[i].split(delimiter).map { it.trim() }
                    if (cols.size <= maxOf(nameCol, phoneCol)) {
                        rowsSkipped++; continue
                    }

                    val name = cols.getOrNull(nameCol)?.trim() ?: ""
                    if (name.isEmpty() || name.contains("TOTAL", true) ||
                        name.contains("SUM", true) || name.startsWith("=")) {
                        rowsSkipped++; continue
                    }

                    val rawPhone  = cols.getOrNull(phoneCol)?.trim() ?: ""
                    if (rawPhone.isEmpty()) { rowsSkipped++; continue }

                    var cleanPhone = rawPhone
                        .replace(".0", "").replace(" ", "").replace("-", "")
                        .replace("(", "").replace(")", "").replace("+", "")
                        .filter { it.isDigit() }

                    if (cleanPhone.startsWith("91") && cleanPhone.length > 10)
                        cleanPhone = cleanPhone.substring(2)

                    if (cleanPhone.length !in 8..15) { rowsSkipped++; continue }

                    leads.add(Lead(
                        name        = name,
                        phone       = cleanPhone,
                        collegeName = if (collegeNameCol >= 0) cols.getOrNull(collegeNameCol)?.trim() ?: "" else "",
                        collegeCity = if (collegeCityCol >= 0) cols.getOrNull(collegeCityCol)?.trim() ?: "" else ""
                    ))
                    rowsParsed++

                } catch (e: Throwable) {
                    Log.e(TAG, "Row $i error: ${e.message}"); rowsSkipped++
                }
            }

            Log.d(TAG, "✅ CSV done: parsed=$rowsParsed skipped=$rowsSkipped total=${leads.size}")

        } catch (e: Throwable) {
            Log.e(TAG, "❌ parseCsv FATAL: ${e.javaClass.simpleName} — ${e.message}")
            e.printStackTrace()
        }
        return leads
    }

    // ── Excel (.xls / .xlsx) parser ───────────────────────────────────────────
    private fun parseExcel(context: Context, uri: Uri): List<Lead> {
        val leads = mutableListOf<Lead>()
        try {
            Log.d(TAG, "Opening Excel workbook...")
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: run {
                    Log.e(TAG, "❌ Cannot open Excel stream for uri=$uri")
                    return emptyList()
                }

            val workbook = try {
                WorkbookFactory.create(inputStream)
            } catch (e: Throwable) {
                Log.e(TAG, "❌ WorkbookFactory.create failed: ${e.javaClass.simpleName} — ${e.message}")
                e.printStackTrace()
                inputStream.close()
                return emptyList()
            }

            val sheet = workbook.getSheetAt(0)
            Log.d(TAG, "✅ Workbook opened, sheet: ${sheet.sheetName}, rows: ${sheet.lastRowNum}")

            // ── Step 1: Detect column positions from header row ───────────────
            val headerRow    = sheet.getRow(0)
            val headerValues = if (headerRow != null) {
                (0..headerRow.lastCellNum).map { c ->
                    headerRow.getCell(c)?.toString()?.trim()?.lowercase() ?: ""
                }
            } else {
                Log.w(TAG, "⚠️ No header row found, using defaults")
                emptyList()
            }

            // ✅ FIX: Broader header detection
            // If ANY cell in row 0 contains letters → treat as header row
            val hasRealHeader = headerValues.isNotEmpty() &&
                headerValues.any { h -> h.isNotBlank() && h.any { c -> c.isLetter() } }

            Log.d(TAG, "Has real header row: $hasRealHeader  headerValues=$headerValues")

            val (nameCol, phoneCol, collegeNameCol, collegeCityCol) = detectColumns(
                if (hasRealHeader) headerValues else emptyList()
            )

            Log.d(TAG, "━━━ Column Detection Results ━━━")
            Log.d(TAG, "Name col: $nameCol  Phone col: $phoneCol")
            Log.d(TAG, "CollegeName col: $collegeNameCol  City col: $collegeCityCol")
            Log.d(TAG, "Total data rows: ${sheet.lastRowNum}")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // ── Step 2: Parse data rows ───────────────────────────────────────
            val dataStartRow = if (hasRealHeader && headerRow != null) 1 else 0
            var rowsParsed   = 0
            var rowsSkipped  = 0

            for (i in dataStartRow..sheet.lastRowNum) {
                try {
                    val row = sheet.getRow(i)
                    if (row == null) { rowsSkipped++; continue }

                    // ── Name ──────────────────────────────────────────────────
                    val nameCell = row.getCell(nameCol)
                    val name     = cellToString(nameCell).trim().ifEmpty { null }
                    if (name.isNullOrEmpty()) { rowsSkipped++; continue }
                    if (name.contains("TOTAL", true) || name.contains("SUM", true) || name.startsWith("=")) {
                        rowsSkipped++; continue
                    }

                    // ── Phone ─────────────────────────────────────────────────
                    val phoneCell = row.getCell(phoneCol)
                    if (phoneCell == null) { rowsSkipped++; continue }

                    val rawPhone: String = cellToString(phoneCell)

                    var cleanPhone = rawPhone
                        .replace(".0", "").replace(" ", "").replace("-", "")
                        .replace("(", "").replace(")", "").replace("+", "")
                        .trim()
                        .filter { it.isDigit() }

                    if (cleanPhone.startsWith("91") && cleanPhone.length > 10) {
                        cleanPhone = cleanPhone.substring(2)
                        Log.d(TAG, "Row $i: Removed country code 91, now: $cleanPhone")
                    }

                    if (cleanPhone.length !in 8..15) {
                        Log.d(TAG, "Row $i skipped — phone='$rawPhone' cleaned='$cleanPhone' (len=${cleanPhone.length}) invalid")
                        rowsSkipped++; continue
                    }

                    // ── Optional fields ───────────────────────────────────────
                    val collegeName = if (collegeNameCol >= 0)
                        cellToString(row.getCell(collegeNameCol)).trim() else ""
                    val collegeCity = if (collegeCityCol >= 0)
                        cellToString(row.getCell(collegeCityCol)).trim() else ""

                    leads.add(Lead(
                        name        = name,
                        phone       = cleanPhone,
                        collegeName = collegeName,
                        collegeCity = collegeCity
                    ))
                    rowsParsed++
                    Log.d(TAG, "Row $i ✅ added: name='$name' phone='$cleanPhone'")

                } catch (e: Throwable) {
                    Log.e(TAG, "Row $i parse error: ${e.javaClass.simpleName} — ${e.message}")
                    rowsSkipped++
                }
            }

            workbook.close()
            inputStream.close()

            Log.d(TAG, "✅ Excel parsing complete:")
            Log.d(TAG, "   Rows parsed: $rowsParsed")
            Log.d(TAG, "   Rows skipped: $rowsSkipped")
            Log.d(TAG, "   Total leads: ${leads.size}")

        } catch (e: Throwable) {
            Log.e(TAG, "❌ parseExcel FATAL: ${e.javaClass.simpleName} — ${e.message}")
            e.printStackTrace()
        }
        return leads
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Safely convert any Excel cell to String (handles scientific notation, formula, all types) */
    private fun cellToString(cell: org.apache.poi.ss.usermodel.Cell?): String {
        cell ?: return ""
        return try {
            val effectiveType = if (cell.cellType == CellType.FORMULA)
                cell.cachedFormulaResultType else cell.cellType

            when (effectiveType) {
                CellType.NUMERIC -> {
                    val numVal = cell.numericCellValue
                    if (numVal == Math.floor(numVal) && !numVal.isInfinite()) {
                        numVal.toLong().toString()
                    } else {
                        numVal.toBigDecimal().toPlainString()
                    }
                }
                CellType.STRING  -> cell.stringCellValue.trim()
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.BLANK   -> ""
                else             -> cell.toString().trim()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "cellToString error: ${e.message}")
            ""
        }
    }

    private fun resolveCellString(cell: org.apache.poi.ss.usermodel.Cell?): String? {
        cell ?: return null
        val effectiveType = if (cell.cellType == CellType.FORMULA)
            cell.cachedFormulaResultType else cell.cellType
        return when (effectiveType) {
            CellType.NUMERIC -> {
                val numVal = cell.numericCellValue
                if (numVal == Math.floor(numVal) && !numVal.isInfinite()) {
                    numVal.toLong().toString()
                } else {
                    numVal.toBigDecimal().toPlainString()
                }
            }
            CellType.STRING  -> cell.stringCellValue
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.BLANK   -> null
            else             -> cell.toString().takeIf { it.isNotBlank() }
        }
    }

    /**
     * Detects column indices for name, phone, college name, college city from header strings.
     * Returns [nameCol, phoneCol, collegeNameCol, collegeCityCol] with safe defaults.
     */
    private fun detectColumns(headers: List<String>): IntArray {
        var nameCol        = 0   // default: first column
        var phoneCol       = 1   // default: second column
        var collegeNameCol = -1
        var collegeCityCol = -1

        // Empty headers → return safe defaults immediately
        if (headers.isEmpty()) {
            Log.w(TAG, "⚠️ No headers — using defaults: name=0, phone=1")
            return intArrayOf(nameCol, phoneCol, collegeNameCol, collegeCityCol)
        }

        for (c in headers.indices) {
            val h = headers[c].trim().lowercase()
            when {
                // ── Skip serial/index columns ──────────────────────────────────
                h == "sno" || h == "s.no" || h == "sl.no" || h == "sr.no" ||
                h == "sr"  || h == "no"   || h == "id"    || h == "#" ||
                h == "serial" || h == "sl" -> { /* skip */ }

                // ── Phone — highest priority ───────────────────────────────────
                h.contains("phone")   || h.contains("mobile")   ||
                h.contains("contact") || h.contains("number")   ||
                h.contains("mob")     || h.contains("ph")       ||
                h.contains("cell")    || h.contains("whatsapp") -> phoneCol = c

                // ── College city (before generic city so it matches first) ──────
                h.contains("college") && (h.contains("city") || h.contains("location") ||
                        h.contains("place") || h.contains("where") || h.contains("address")) -> collegeCityCol = c

                h.contains("city")     || h.contains("district") ||
                h.contains("location") || h.contains("place")    ||
                h.contains("pincode")  || h.contains("state") -> {
                    if (collegeCityCol < 0) collegeCityCol = c
                }

                // ── College name ───────────────────────────────────────────────
                h.contains("college") || h.contains("institute") ||
                h.contains("school")  || h.contains("university") ||
                h.contains("institution") -> collegeNameCol = c

                // ── Person / student name ──────────────────────────────────────
                (h.contains("name") || h.contains("student") || h.contains("candidate") ||
                 h.contains("applicant") || h.contains("person")) &&
                !h.contains("college") && !h.contains("institute") &&
                !h.contains("school")  && !h.contains("university") -> nameCol = c
            }
        }

        // Edge-case: same column for name and phone → reset defaults
        if (nameCol == phoneCol) {
            Log.w(TAG, "⚠️ name and phone mapped to same column ($nameCol) — resetting defaults")
            nameCol  = 0
            phoneCol = 1
        }

        Log.d(TAG, "✅ detectColumns → name=$nameCol, phone=$phoneCol, college=$collegeNameCol, city=$collegeCityCol")
        return intArrayOf(nameCol, phoneCol, collegeNameCol, collegeCityCol)
    }
}