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
                    Log.e(TAG, "Cannot open CSV stream")
                    return emptyList()
                }

            val lines = BufferedReader(InputStreamReader(stream)).readLines()
            stream.close()

            if (lines.isEmpty()) {
                Log.w(TAG, "CSV is empty")
                return emptyList()
            }

            Log.d(TAG, "CSV loaded: ${lines.size} lines")

            val delimiter = detectDelimiter(lines[0])
            val (nameCol, phoneCol, collegeNameCol, collegeCityCol) = detectColumnsSmartCsv(lines, delimiter)

            val hasRealHeader = isHeaderRowCsv(lines[0], delimiter, nameCol, phoneCol)
            val dataStartRow = if (hasRealHeader) 1 else 0

            Log.d(TAG, "━━━ Column Detection Results ━━━")
            Log.d(TAG, "Name col: $nameCol  Phone col: $phoneCol")
            Log.d(TAG, "CollegeName col: $collegeNameCol  City col: $collegeCityCol")
            Log.d(TAG, "Has Real Header: $hasRealHeader")
            Log.d(TAG, "Data start row: $dataStartRow")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            var rowsParsed  = 0
            var rowsSkipped = 0

            for (i in dataStartRow until lines.size) {
                try {
                    val line = lines[i]
                    if (line.isBlank()) {
                        rowsSkipped++; continue
                    }
                    val cols = line.split(delimiter).map { it.trim() }
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

            Log.d(TAG, "CSV done: parsed=$rowsParsed skipped=$rowsSkipped total=${leads.size}")

        } catch (e: Throwable) {
            Log.e(TAG, "parseCsv FATAL: ${e.javaClass.simpleName} — ${e.message}")
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
                    Log.e(TAG, "Cannot open Excel stream for uri=$uri")
                    return emptyList()
                }

            val workbook = try {
                WorkbookFactory.create(inputStream)
            } catch (e: Throwable) {
                Log.e(TAG, "WorkbookFactory.create failed: ${e.javaClass.simpleName} — ${e.message}")
                e.printStackTrace()
                inputStream.close()
                return emptyList()
            }

            val sheet = workbook.getSheetAt(0)
            Log.d(TAG, "Workbook opened, sheet: ${sheet.sheetName}, rows: ${sheet.lastRowNum}")

            // ── Step 1: Detect column positions using smart scoring ───────────
            val (nameCol, phoneCol, collegeNameCol, collegeCityCol) = detectColumnsSmartExcel(sheet)

            val headerRow = sheet.getRow(0)
            val hasRealHeader = isHeaderRowExcel(headerRow, nameCol, phoneCol)
            val dataStartRow = if (hasRealHeader) 1 else 0

            Log.d(TAG, "━━━ Column Detection Results ━━━")
            Log.d(TAG, "Name col: $nameCol  Phone col: $phoneCol")
            Log.d(TAG, "CollegeName col: $collegeNameCol  City col: $collegeCityCol")
            Log.d(TAG, "Has Real Header: $hasRealHeader")
            Log.d(TAG, "Data start row: $dataStartRow")
            Log.d(TAG, "Total data rows: ${sheet.lastRowNum}")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // ── Step 2: Parse data rows ───────────────────────────────────────
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
                    Log.d(TAG, "Row $i added: name='$name' phone='$cleanPhone'")

                } catch (e: Throwable) {
                    Log.e(TAG, "Row $i parse error: ${e.javaClass.simpleName} — ${e.message}")
                    rowsSkipped++
                }
            }

            workbook.close()
            inputStream.close()

            Log.d(TAG, "Excel parsing complete:")
            Log.d(TAG, "   Rows parsed: $rowsParsed")
            Log.d(TAG, "   Rows skipped: $rowsSkipped")
            Log.d(TAG, "   Total leads: ${leads.size}")

        } catch (e: Throwable) {
            Log.e(TAG, "parseExcel FATAL: ${e.javaClass.simpleName} — ${e.message}")
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

    private fun detectColumnsSmartExcel(sheet: org.apache.poi.ss.usermodel.Sheet): IntArray {
        val maxScanRows = minOf(sheet.lastRowNum + 1, 6) // Scan up to 6 rows (0 to 5)
        val numCols = 20
        val phoneScores = IntArray(numCols)
        val nameScores = IntArray(numCols)
        val collegeNameScores = IntArray(numCols)
        val collegeCityScores = IntArray(numCols)

        for (r in 0 until maxScanRows) {
            val row = sheet.getRow(r) ?: continue
            for (c in 0 until numCols) {
                val cell = row.getCell(c) ?: continue
                val rawVal = cellToString(cell).trim()
                if (rawVal.isEmpty()) continue

                val cleanPhone = rawVal
                    .replace(".0", "").replace(" ", "").replace("-", "")
                    .replace("(", "").replace(")", "").replace("+", "")
                    .filter { it.isDigit() }

                if (cleanPhone.length in 8..15) {
                    phoneScores[c] += 5
                } else if (rawVal.any { it.isLetter() }) {
                    val lowerVal = rawVal.lowercase()
                    if (lowerVal.contains("college") || lowerVal.contains("institute") || lowerVal.contains("university") || lowerVal.contains("school")) {
                        collegeNameScores[c] += 3
                    } else if (lowerVal.contains("city") || lowerVal.contains("district") || lowerVal.contains("state") || lowerVal.contains("pincode")) {
                        collegeCityScores[c] += 3
                    } else {
                        val isHeaderKeyword = lowerVal == "name" || lowerVal == "phone" || lowerVal == "mobile" || lowerVal == "contact" || lowerVal == "number" || lowerVal == "duration" || lowerVal == "status" || lowerVal == "date"
                        if (!isHeaderKeyword) {
                            nameScores[c] += 2
                        }
                    }
                }
            }
        }

        // Boost first row keywords
        val firstRow = sheet.getRow(0)
        if (firstRow != null) {
            for (c in 0 until numCols) {
                val cell = firstRow.getCell(c) ?: continue
                val lowerVal = cellToString(cell).trim().lowercase()
                when {
                    lowerVal.contains("phone") || lowerVal.contains("mobile") || lowerVal.contains("contact") || lowerVal.contains("number") || lowerVal.contains("mob") || lowerVal.contains("whatsapp") -> {
                        phoneScores[c] += 20
                    }
                    lowerVal.contains("college") || lowerVal.contains("institute") || lowerVal.contains("university") || lowerVal.contains("school") -> {
                        collegeNameScores[c] += 20
                    }
                    lowerVal.contains("city") || lowerVal.contains("district") || lowerVal.contains("state") || lowerVal.contains("pincode") || lowerVal.contains("location") -> {
                        collegeCityScores[c] += 20
                    }
                    (lowerVal.contains("name") || lowerVal.contains("student") || lowerVal.contains("candidate")) && !lowerVal.contains("college") -> {
                        nameScores[c] += 20
                    }
                }
            }
        }

        var phoneCol = -1
        var maxPhoneScore = -1
        for (c in 0 until numCols) {
            if (phoneScores[c] > maxPhoneScore) {
                maxPhoneScore = phoneScores[c]
                phoneCol = c
            }
        }

        var nameCol = -1
        var maxNameScore = -1
        for (c in 0 until numCols) {
            if (c == phoneCol) continue
            if (nameScores[c] > maxNameScore) {
                maxNameScore = nameScores[c]
                nameCol = c
            }
        }

        var collegeNameCol = -1
        var maxCollegeScore = -1
        for (c in 0 until numCols) {
            if (c == phoneCol || c == nameCol) continue
            if (collegeNameScores[c] > maxCollegeScore) {
                maxCollegeScore = collegeNameScores[c]
                collegeNameCol = c
            }
        }

        var collegeCityCol = -1
        var maxCityScore = -1
        for (c in 0 until numCols) {
            if (c == phoneCol || c == nameCol || c == collegeNameCol) continue
            if (collegeCityScores[c] > maxCityScore) {
                maxCityScore = collegeCityScores[c]
                collegeCityCol = c
            }
        }

        if (phoneCol == -1 || maxPhoneScore <= 0) {
            phoneCol = 1
        }
        if (nameCol == -1 || maxNameScore <= 0) {
            nameCol = if (phoneCol == 0) 1 else 0
        }
        if (collegeNameScores.maxOrNull() ?: 0 <= 0) {
            collegeNameCol = -1
        }
        if (collegeCityScores.maxOrNull() ?: 0 <= 0) {
            collegeCityCol = -1
        }

        if (nameCol == phoneCol) {
            nameCol = 0
            phoneCol = 1
        }

        Log.d(TAG, "Smart Excel detection results: name=$nameCol (score=$maxNameScore), phone=$phoneCol (score=$maxPhoneScore), college=$collegeNameCol, city=$collegeCityCol")
        return intArrayOf(nameCol, phoneCol, collegeNameCol, collegeCityCol)
    }

    private fun detectColumnsSmartCsv(lines: List<String>, delimiter: String): IntArray {
        val maxScanRows = minOf(lines.size, 6)
        val numCols = 20
        val phoneScores = IntArray(numCols)
        val nameScores = IntArray(numCols)
        val collegeNameScores = IntArray(numCols)
        val collegeCityScores = IntArray(numCols)

        for (r in 0 until maxScanRows) {
            val line = lines.getOrNull(r) ?: continue
            val cols = line.split(delimiter).map { it.trim() }
            for (c in cols.indices) {
                if (c >= numCols) continue
                val rawVal = cols[c]
                if (rawVal.isEmpty()) continue

                val cleanPhone = rawVal
                    .replace(".0", "").replace(" ", "").replace("-", "")
                    .replace("(", "").replace(")", "").replace("+", "")
                    .filter { it.isDigit() }

                if (cleanPhone.length in 8..15) {
                    phoneScores[c] += 5
                } else if (rawVal.any { it.isLetter() }) {
                    val lowerVal = rawVal.lowercase()
                    if (lowerVal.contains("college") || lowerVal.contains("institute") || lowerVal.contains("university") || lowerVal.contains("school")) {
                        collegeNameScores[c] += 3
                    } else if (lowerVal.contains("city") || lowerVal.contains("district") || lowerVal.contains("state") || lowerVal.contains("pincode")) {
                        collegeCityScores[c] += 3
                    } else {
                        val isHeaderKeyword = lowerVal == "name" || lowerVal == "phone" || lowerVal == "mobile" || lowerVal == "contact" || lowerVal == "number" || lowerVal == "duration" || lowerVal == "status" || lowerVal == "date"
                        if (!isHeaderKeyword) {
                            nameScores[c] += 2
                        }
                    }
                }
            }
        }

        // Boost first row keywords
        val firstLine = lines.getOrNull(0)
        if (firstLine != null) {
            val cols = firstLine.split(delimiter).map { it.trim().lowercase() }
            for (c in cols.indices) {
                if (c >= numCols) continue
                val lowerVal = cols[c]
                when {
                    lowerVal.contains("phone") || lowerVal.contains("mobile") || lowerVal.contains("contact") || lowerVal.contains("number") || lowerVal.contains("mob") || lowerVal.contains("whatsapp") -> {
                        phoneScores[c] += 20
                    }
                    lowerVal.contains("college") || lowerVal.contains("institute") || lowerVal.contains("university") || lowerVal.contains("school") -> {
                        collegeNameScores[c] += 20
                    }
                    lowerVal.contains("city") || lowerVal.contains("district") || lowerVal.contains("state") || lowerVal.contains("pincode") || lowerVal.contains("location") -> {
                        collegeCityScores[c] += 20
                    }
                    (lowerVal.contains("name") || lowerVal.contains("student") || lowerVal.contains("candidate")) && !lowerVal.contains("college") -> {
                        nameScores[c] += 20
                    }
                }
            }
        }

        var phoneCol = -1
        var maxPhoneScore = -1
        for (c in 0 until numCols) {
            if (phoneScores[c] > maxPhoneScore) {
                maxPhoneScore = phoneScores[c]
                phoneCol = c
            }
        }

        var nameCol = -1
        var maxNameScore = -1
        for (c in 0 until numCols) {
            if (c == phoneCol) continue
            if (nameScores[c] > maxNameScore) {
                maxNameScore = nameScores[c]
                nameCol = c
            }
        }

        var collegeNameCol = -1
        var maxCollegeScore = -1
        for (c in 0 until numCols) {
            if (c == phoneCol || c == nameCol) continue
            if (collegeNameScores[c] > maxCollegeScore) {
                maxCollegeScore = collegeNameScores[c]
                collegeNameCol = c
            }
        }

        var collegeCityCol = -1
        var maxCityScore = -1
        for (c in 0 until numCols) {
            if (c == phoneCol || c == nameCol || c == collegeNameCol) continue
            if (collegeCityScores[c] > maxCityScore) {
                maxCityScore = collegeCityScores[c]
                collegeCityCol = c
            }
        }

        if (phoneCol == -1 || maxPhoneScore <= 0) {
            phoneCol = 1
        }
        if (nameCol == -1 || maxNameScore <= 0) {
            nameCol = if (phoneCol == 0) 1 else 0
        }
        if (collegeNameScores.maxOrNull() ?: 0 <= 0) {
            collegeNameCol = -1
        }
        if (collegeCityScores.maxOrNull() ?: 0 <= 0) {
            collegeCityCol = -1
        }

        if (nameCol == phoneCol) {
            nameCol = 0
            phoneCol = 1
        }

        Log.d(TAG, "Smart CSV detection results: name=$nameCol (score=$maxNameScore), phone=$phoneCol (score=$maxPhoneScore), college=$collegeNameCol, city=$collegeCityCol")
        return intArrayOf(nameCol, phoneCol, collegeNameCol, collegeCityCol)
    }

    private fun isHeaderRowExcel(row: org.apache.poi.ss.usermodel.Row?, nameCol: Int, phoneCol: Int): Boolean {
        row ?: return false
        
        // 1. If any cell in this row contains a valid phone number, then it is NOT a header row!
        for (c in 0 until row.lastCellNum) {
            val cell = row.getCell(c) ?: continue
            val rawVal = cellToString(cell).trim()
            val cleanPhone = rawVal
                .replace(".0", "").replace(" ", "").replace("-", "")
                .replace("(", "").replace(")", "").replace("+", "")
                .filter { it.isDigit() }
            if (cleanPhone.length in 8..15) {
                Log.d(TAG, "Row 0 has a valid phone number '$rawVal' -> Not a header row!")
                return false
            }
        }

        // 2. Check if the cell at nameCol or phoneCol contains common header keywords
        val nameCellVal = row.getCell(nameCol)?.let { cellToString(it).lowercase().trim() } ?: ""
        val phoneCellVal = row.getCell(phoneCol)?.let { cellToString(it).lowercase().trim() } ?: ""

        val isNameHeader = nameCellVal.contains("name") || nameCellVal.contains("student") || 
                           nameCellVal.contains("candidate") || nameCellVal.contains("naam") || nameCellVal.contains("नाम")
                           
        val isPhoneHeader = phoneCellVal.contains("phone") || phoneCellVal.contains("mobile") || 
                            phoneCellVal.contains("contact") || phoneCellVal.contains("number") || 
                            phoneCellVal.contains("ph") || phoneCellVal.contains("mob") || 
                            phoneCellVal.contains("whatsapp") || phoneCellVal.contains("मोबाइल")

        if (isNameHeader || isPhoneHeader) {
            Log.d(TAG, "Row 0 matches header keywords -> Treated as header row!")
            return true
        }

        // 3. Fallback: if any cell in the row matches header keywords
        for (c in 0 until row.lastCellNum) {
            val cell = row.getCell(c) ?: continue
            val lower = cellToString(cell).lowercase().trim()
            if (lower == "name" || lower == "phone" || lower == "mobile" || lower == "contact" || 
                lower == "number" || lower == "sno" || lower == "s.no" || lower == "id" || lower == "date") {
                Log.d(TAG, "Row 0 contains generic header keyword '$lower' -> Treated as header row!")
                return true
            }
        }

        Log.d(TAG, "Row 0 does not look like a header -> Treated as data row!")
        return false
    }

    private fun isHeaderRowCsv(line: String, delimiter: String, nameCol: Int, phoneCol: Int): Boolean {
        val cols = line.split(delimiter).map { it.trim() }
        
        // 1. If any cell in this row contains a valid phone number, then it is NOT a header row!
        for (cellVal in cols) {
            val cleanPhone = cellVal
                .replace(".0", "").replace(" ", "").replace("-", "")
                .replace("(", "").replace(")", "").replace("+", "")
                .filter { it.isDigit() }
            if (cleanPhone.length in 8..15) {
                Log.d(TAG, "CSV line 0 has a valid phone number '$cellVal' -> Not a header row!")
                return false
            }
        }

        // 2. Check if the cell at nameCol or phoneCol contains common header keywords
        val nameCellVal = cols.getOrNull(nameCol)?.lowercase() ?: ""
        val phoneCellVal = cols.getOrNull(phoneCol)?.lowercase() ?: ""

        val isNameHeader = nameCellVal.contains("name") || nameCellVal.contains("student") || 
                           nameCellVal.contains("candidate") || nameCellVal.contains("naam") || nameCellVal.contains("नाम")
                           
        val isPhoneHeader = phoneCellVal.contains("phone") || phoneCellVal.contains("mobile") || 
                            phoneCellVal.contains("contact") || phoneCellVal.contains("number") || 
                            phoneCellVal.contains("ph") || phoneCellVal.contains("mob") || 
                            phoneCellVal.contains("whatsapp") || phoneCellVal.contains("मोबाइल")

        if (isNameHeader || isPhoneHeader) {
            Log.d(TAG, "CSV line 0 matches header keywords -> Treated as header row!")
            return true
        }

        // 3. Fallback: if any cell in the row matches header keywords
        for (lower in cols.map { it.lowercase() }) {
            if (lower == "name" || lower == "phone" || lower == "mobile" || lower == "contact" || 
                lower == "number" || lower == "sno" || lower == "s.no" || lower == "id" || lower == "date") {
                Log.d(TAG, "CSV line 0 contains generic header keyword '$lower' -> Treated as header row!")
                return true
            }
        }

        Log.d(TAG, "CSV line 0 does not look like a header -> Treated as data row!")
        return false
    }
}