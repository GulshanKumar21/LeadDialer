// ════════════════════════════════════════════════════════════════════
//  Sheet Helpers
// ════════════════════════════════════════════════════════════════════
function getOrCreateSheet(name) {
  const ss  = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(name);
  if (!sheet) sheet = ss.insertSheet(name);
  return sheet;
}

function setupLeadHeaders(sheet) {
  // Added "Sales Done" as column 10
  const headers = ["Name","Phone","Status","Duration","Called At","Called By","College Name","College City","Notes","Sales Done"];
  sheet.getRange(1,1,1,10).setValues([headers])
       .setBackground("#2D3748").setFontColor("#FFFFFF").setFontWeight("bold");
  sheet.setFrozenRows(1);
  [180,140,130,100,160,150,180,150,200,100].forEach((w,i) => sheet.setColumnWidth(i+1,w));
}

function setupCallHeaders(sheet) {
  const headers = ["Name","Phone","Status","Duration","Called At","Gap (Idle)","_ms"]; // col G = hidden ms key
  sheet.getRange(1,1,1,7).setValues([headers])
       .setBackground("#1A365D").setFontColor("#FFFFFF").setFontWeight("bold");
  sheet.setFrozenRows(1);
  // Force columns E, F, G to TEXT — prevent date auto-conversion
  sheet.getRange("E:G").setNumberFormat("@STRING@");
  // Hide col G (raw ms — used only for dedup)
  sheet.hideColumns(7);
  [180,140,140,100,170,110,1].forEach((w,i) => sheet.setColumnWidth(i+1,w));
}

function setupAttendanceHeaders(sheet) {
  const headers = ["Employee","Date","Punch-In Time","Status","Late Reason","Total Calls"];
  sheet.getRange(1,1,1,6).setValues([headers])
       .setBackground("#744210").setFontColor("#FFFFFF").setFontWeight("bold");
  sheet.setFrozenRows(1);
  [160,120,130,100,250,110].forEach((w,i) => sheet.setColumnWidth(i+1,w));
}

// ════════════════════════════════════════════════════════════════════
//  KEY FIX: Normalize a cell value that may be a Date object or string.
//  Google Sheets auto-converts "2026-05-09 18:26" → Date object.
//  Reading it back with String() gives "Mon May 09 2026..." → dedup breaks.
//  This function always returns "yyyy-MM-dd HH:mm" string.
// ════════════════════════════════════════════════════════════════════
function normalizeCalledAt(val) {
  if (!val && val !== 0) return "";
  if (val instanceof Date) {
    return Utilities.formatDate(val, Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm");
  }
  const s = String(val).trim();
  if (!s || s === "Not called" || s === "—") return s;
  // Already in sortable format yyyy-MM-dd
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.substring(0, 16); // trim seconds if any
  return s;
}

// Convert Android "dd/MM/yyyy HH:mm" → sortable "yyyy-MM-dd HH:mm"
function toSortable(str) {
  if (!str || str === "Not called") return "Not called";
  try {
    const s = String(str).trim();
    if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.substring(0, 16); // already sortable
    const parts     = s.split(" ");
    const dateParts = parts[0].split("/"); // [dd, MM, yyyy]
    if (dateParts.length !== 3) return s;
    const time = parts[1] || "00:00";
    return dateParts[2] + "-" + dateParts[1] + "-" + dateParts[0] + " " + time;
  } catch(e) { return String(str); }
}

// ════════════════════════════════════════════════════════════════════
//  doGet
// ════════════════════════════════════════════════════════════════════
function doGet(e) {
  try {
    if (e.parameter.phones) {
      const phones = e.parameter.phones.split(",");
      const result = {};
      phones.forEach(phone => {
        const clean = phone.trim().replace(/\D/g,"");
        if (clean) result[clean] = checkPhoneInternal(clean);
      });
      return ContentService.createTextOutput(JSON.stringify({data:result}))
                           .setMimeType(ContentService.MimeType.JSON);
    }
    if (e.parameter.phone) {
      return ContentService.createTextOutput(JSON.stringify(checkPhoneInternal(e.parameter.phone)))
                           .setMimeType(ContentService.MimeType.JSON);
    }
    return ContentService.createTextOutput(JSON.stringify({called:false}))
                         .setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({called:false,error:err.message}))
                         .setMimeType(ContentService.MimeType.JSON);
  }
}

function checkPhoneInternal(phone) {
  const ss     = SpreadsheetApp.getActiveSpreadsheet();
  const sheets = ss.getSheets().filter(s =>
    s.getName() !== "📊 Summary" &&
    s.getName() !== "📅 Attendance" &&
    !s.getName().endsWith("_calls")
  );
  const checkPhone = String(phone).replace(/\D/g,"");
  for (const sheet of sheets) {
    const lastRow = sheet.getLastRow();
    if (lastRow < 2) continue;
    const data = sheet.getRange(2,1,lastRow-1,10).getValues();
    for (const row of data) {
      const rowPhone = String(row[1]).replace(/\D/g,"");
      if (rowPhone === checkPhone) {
        const status = String(row[2]);
        if (status && status !== "Pending" && status !== "") {
          return { called:true, calledBy:String(row[5]).trim()||sheet.getName(),
                   status:status, collegeName:String(row[6]).trim(), collegeCity:String(row[7]).trim() };
        }
      }
    }
  }
  return { called:false, calledBy:"", status:"", collegeName:"", collegeCity:"" };
}

// ════════════════════════════════════════════════════════════════════
//  doPost
// ════════════════════════════════════════════════════════════════════
function doPost(e) {
  try {
    const data         = JSON.parse(e.postData.contents);
    const employeeName = data.employeeName || "Unknown";
    const type         = data.type || "leads";
    if (type === "leads" && data.leads) {
      syncLeads(employeeName, data.leads);
      updateSummarySheet();
    }
    if (type === "callRecords" && data.records) {
      syncCallRecords(employeeName, data.records);
    }
    if (type === "attendance" && data.attendance) {
      syncAttendance(data.attendance);
    }
    if (type === "leaveEmail") {
      return ContentService.createTextOutput(JSON.stringify(handleLeaveEmail(data)))
                           .setMimeType(ContentService.MimeType.JSON);
    }
    if (type === "leaveStatusEmail") {
      return ContentService.createTextOutput(JSON.stringify(handleLeaveStatusEmail(data)))
                           .setMimeType(ContentService.MimeType.JSON);
    }
    return ContentService.createTextOutput(JSON.stringify({success:true}))
                         .setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({error:err.message}))
                         .setMimeType(ContentService.MimeType.JSON);
  }
}

// ════════════════════════════════════════════════════════════════════
//  syncLeads — now includes salesDone column
// ════════════════════════════════════════════════════════════════════
function syncLeads(employeeName, leads) {
  const sheet   = getOrCreateSheet(employeeName);
  const lastRow = sheet.getLastRow();
  if (lastRow === 0) setupLeadHeaders(sheet);
  else if (lastRow > 1) sheet.getRange(2,1,lastRow-1,10).clearContent().setBackground("#FFFFFF");
  if (leads.length > 0) {
    const rows = leads.map(lead => [
      lead.name        || "",
      lead.phone       || "",
      lead.status      || "Pending",
      formatDuration(lead.duration || 0),
      lead.calledAt    || "Not called",
      lead.calledBy    || employeeName,
      lead.collegeName || "",
      lead.collegeCity || "",
      lead.notes       || "",
      lead.salesDone   ? "✅ Yes" : ""     // NEW: salesDone column
    ]);
    sheet.getRange(2,1,rows.length,10).setValues(rows);
    leads.forEach((_,i) => {
      const bg = leads[i].salesDone ? "#D4EDDA" : statusColor(leads[i].status);
      sheet.getRange(i+2,1,1,10).setBackground(bg);
    });
  }
}

// ════════════════════════════════════════════════════════════════════
//  syncCallRecords — FIXED dedup (normalizes Date objects back to string)
//                   FIXED orphan rows (rows where phone is empty)
// ════════════════════════════════════════════════════════════════════
function syncCallRecords(employeeName, records) {
  const sheetName = employeeName + "_calls";
  const sheet     = getOrCreateSheet(sheetName);
  if (sheet.getLastRow() === 0) {
    setupCallHeaders(sheet);
  } else {
    sheet.getRange("E:G").setNumberFormat("@STRING@");
    // Ensure col G is hidden (ms key column)
    try { sheet.hideColumns(7); } catch(e) {}
  }

  // ── Step 1: Clean orphan rows (Phone column is empty) ──────────────
  const currentLast = sheet.getLastRow();
  if (currentLast >= 2) {
    const allData = sheet.getRange(2, 1, currentLast - 1, 7).getValues();
    for (let i = allData.length - 1; i >= 0; i--) {
      const phone = String(allData[i][1]).replace(/\D/g, "");
      if (!phone) sheet.deleteRow(i + 2);
    }
  }

  // ── Step 2: Build existing key set ─────────────────────────────────
  const existingLastRow = sheet.getLastRow();
  const existingKeys    = new Set();
  if (existingLastRow >= 2) {
    // Read cols B-G: phone(0), status(1), duration(2), calledAt(3), gap(4), ms(5)
    const existingData = sheet.getRange(2, 2, existingLastRow - 1, 6).getValues();
    existingData.forEach(row => {
      const phone = String(row[0]).replace(/\D/g, "");
      const ms    = String(row[5]).trim(); // col G = raw ms
      const time  = normalizeCalledAt(row[3]); // fallback
      if (phone) existingKeys.add(phone + "|" + (ms && ms !== "" ? ms : time));
    });
  }

  // ── Step 3: Append only NEW records ────────────────────────────────
  const newRows = [];
  records.forEach(r => {
    const phone    = String(r.phone || "").replace(/\D/g, "");
    const ms       = String(r.calledAtMs || "").trim();
    const sortable = toSortable(r.calledAt);
    const key = phone + "|" + (ms && ms !== "0" ? ms : sortable);
    if (phone && !existingKeys.has(key)) {
      // Format gap: if gapSeconds provided, convert to readable string
      const gapSec = r.gapSeconds || 0;
      const gapStr = gapSec > 0 ? formatDuration(gapSec) : "—";
      newRows.push([
        r.name   || "",
        r.phone  || "",
        r.status || "—",
        formatDuration(r.duration || 0),
        sortable,    // col E: display "yyyy-MM-dd HH:mm"
        gapStr,      // col F: gap since last call ended
        ms           // col G: hidden raw ms for dedup
      ]);
      existingKeys.add(key);
    }
  });

  if (newRows.length > 0) {
    const insertRow = sheet.getLastRow() + 1;
    const range     = sheet.getRange(insertRow, 1, newRows.length, 7);
    range.setNumberFormat("@STRING@");
    range.setValues(newRows);
    newRows.forEach((row, i) => {
      sheet.getRange(insertRow + i, 1, 1, 6).setBackground(statusColor(row[2]));
    });
  }

  // ── Step 4: Re-sort all rows by calledAt (col E) newest first ──────
  const totalRows = sheet.getLastRow();
  if (totalRows >= 3) {
    sheet.getRange(2, 1, totalRows - 1, 7).sort({ column: 5, ascending: false });
  }

  sheet.setFrozenRows(1);
  [180,140,140,100,170,110].forEach((w,i) => sheet.setColumnWidth(i+1,w));
  try { sheet.hideColumns(7); } catch(e) {}
}

// ════════════════════════════════════════════════════════════════════
//  syncAttendance
// ════════════════════════════════════════════════════════════════════
function syncAttendance(records) {
  const sheet = getOrCreateSheet("📅 Attendance");
  if (sheet.getLastRow() === 0) setupAttendanceHeaders(sheet);
  records.forEach(rec => {
    const employee     = rec.employeeName || "Unknown";
    const date         = rec.date         || "";
    const existingLast = sheet.getLastRow();
    let   foundRow     = -1;
    if (existingLast >= 2) {
      const existingData = sheet.getRange(2,1,existingLast-1,2).getValues();
      for (let i = 0; i < existingData.length; i++) {
        if (existingData[i][0] === employee && existingData[i][1] === date) {
          foundRow = i + 2; break;
        }
      }
    }
    const statusText = rec.isLate ? "🔴 Late" : "🟢 On Time";
    const rowData    = [employee, date, rec.punchInTime||"—", statusText, rec.lateReason||"—", rec.totalCalls||0];
    const bgColor    = rec.isLate ? "#FFF3CD" : "#D4EDDA";
    if (foundRow !== -1) sheet.getRange(foundRow,1,1,6).setValues([rowData]).setBackground(bgColor);
    else sheet.getRange(sheet.getLastRow()+1,1,1,6).setValues([rowData]).setBackground(bgColor);
  });
  if (sheet.getLastRow() > 2) sheet.getRange(2,1,sheet.getLastRow()-1,6).sort({column:2,ascending:false});
}

// ════════════════════════════════════════════════════════════════════
//  updateSummarySheet — updated for 10-column leads
// ════════════════════════════════════════════════════════════════════
function updateSummarySheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let summary = ss.getSheetByName("📊 Summary");
  if (!summary) summary = ss.insertSheet("📊 Summary", 0);
  summary.clearContents().clearFormats();
  const headers = ["Employee","Total","Connected","Interested","Busy","Not Connected","Not Interested","Pending","Sales Done"];
  summary.getRange(1,1,1,9).setValues([headers])
         .setBackground("#2D3748").setFontColor("#FFFFFF").setFontWeight("bold");
  const sheets = ss.getSheets().filter(s =>
    s.getName() !== "📊 Summary" &&
    s.getName() !== "📅 Attendance" &&
    !s.getName().endsWith("_calls")
  );
  const summaryRows = [];
  sheets.forEach(sheet => {
    const lastRow = sheet.getLastRow();
    if (lastRow < 2) return;
    const data  = sheet.getRange(2,1,lastRow-1,10).getValues();
    const count = (status) => data.filter(r => r[2] === status).length;
    summaryRows.push([
      sheet.getName(), data.length,
      count("Connected"), count("Interested"),
      count("Busy"),      count("Not Connected"),
      count("Not Interested"),
      data.filter(r => !r[2] || r[2] === "Pending").length,
      data.filter(r => r[9] === "✅ Yes").length   // Sales Done count
    ]);
  });
  if (summaryRows.length > 0) {
    summary.getRange(2,1,summaryRows.length,9).setValues(summaryRows);
    summaryRows.forEach((_,i) => {
      summary.getRange(i+2,3).setBackground("#C6EFCE");
      summary.getRange(i+2,4).setBackground("#BDD7EE");
      summary.getRange(i+2,5).setBackground("#FFE699");
      summary.getRange(i+2,7).setBackground("#FFC7CE");
      summary.getRange(i+2,9).setBackground("#D4EDDA"); // Sales Done = green
    });
    const totalRow = summaryRows.length + 2;
    summary.getRange(totalRow,1).setValue("✅ TOTAL").setFontWeight("bold");
    for (let col = 2; col <= 9; col++) {
      summary.getRange(totalRow,col)
        .setFormula("=SUM(" + col2letter(col) + "2:" + col2letter(col) + (totalRow-1) + ")")
        .setFontWeight("bold").setBackground("#F0F4F8");
    }
  }
  summary.setFrozenRows(1);
  summary.setColumnWidth(1,180);
  for (let i = 2; i <= 9; i++) summary.setColumnWidth(i,120);
}

// ════════════════════════════════════════════════════════════════════
//  Leave Email Handlers
// ════════════════════════════════════════════════════════════════════
function handleLeaveEmail(data) {
  try {
    const adminEmail = data.adminEmail || "";
    const empName    = data.empName    || "Employee";
    const empEmail   = data.empEmail   || "";
    const leaveType  = data.leaveType  || "Leave";
    const fromDate   = data.fromDate   || "—";
    const toDate     = data.toDate     || "—";
    const reason     = data.reason     || "No reason provided";
    if (!adminEmail) return { success:false, error:"No admin email" };
    const subject = "Leave Request — " + empName + " (" + leaveType + ")";
    const htmlBody =
      "<div style='font-family:Arial,sans-serif;max-width:600px'>" +
      "<h2 style='color:#3B82F6'>📋 Leave Request</h2>" +
      "<table style='width:100%;border-collapse:collapse;font-size:14px'>" +
      row_("Employee",  empName)  + row_("Email",     empEmail) +
      row_("Leave Type",leaveType)+ row_("From",      fromDate) +
      row_("To",        toDate)   + row_("Reason",    reason)   +
      "</table>" +
      "<p style='color:#6B7280;font-size:12px;margin-top:16px'>— Adyapan CRM</p></div>";
    const textBody =
      "Employee  : " + empName   + "\nEmail     : " + empEmail  +
      "\nLeave Type: " + leaveType + "\nFrom      : " + fromDate +
      "\nTo        : " + toDate    + "\nReason    : " + reason;
    GmailApp.sendEmail(adminEmail, subject, textBody, { htmlBody:htmlBody, replyTo:empEmail });
    return { success:true };
  } catch(err) {
    Logger.log("handleLeaveEmail error: " + err.toString());
    return { success:false, error:err.toString() };
  }
}

function handleLeaveStatusEmail(data) {
  try {
    const empEmail  = data.empEmail  || "";
    const empName   = data.empName   || "Employee";
    const leaveType = data.leaveType || "Leave";
    const fromDate  = data.fromDate  || "—";
    const toDate    = data.toDate    || "—";
    const status    = data.status    || "Updated";
    if (!empEmail) return { success:false, error:"No employee email" };
    const isApproved = status === "Approved";
    const emoji      = isApproved ? "✅" : "❌";
    const color      = isApproved ? "#16A34A" : "#DC2626";
    const message    = isApproved
      ? "Your leave has been <b>approved</b>. Enjoy your time off! 🎉"
      : "Your leave has been <b>rejected</b>. Please contact your manager for more details.";
    const subject = emoji + " Leave " + status + " — " + leaveType;
    const htmlBody =
      "<div style='font-family:Arial,sans-serif;max-width:600px'>" +
      "<h2 style='color:" + color + "'>" + emoji + " Leave " + status + "</h2>" +
      "<p>Dear <b>" + empName + "</b>,</p><p>" + message + "</p>" +
      "<table style='width:100%;border-collapse:collapse;font-size:14px;margin-top:12px'>" +
      row_("Leave Type", leaveType) + row_("From", fromDate) +
      row_("To",         toDate)    + row_("Status", emoji + " " + status) +
      "</table>" +
      "<p style='color:#6B7280;font-size:12px;margin-top:16px'>— Adyapan CRM</p></div>";
    const textBody =
      "Dear " + empName + ",\n\nYour leave request has been " + status.toLowerCase() + ".\n\n" +
      "Leave Type : " + leaveType + "\nFrom       : " + fromDate +
      "\nTo         : " + toDate   + "\nStatus     : " + status + "\n\n" +
      (isApproved ? "Enjoy your time off!" : "Please contact your manager for details.") +
      "\n\n— Adyapan CRM";
    GmailApp.sendEmail(empEmail, subject, textBody, { htmlBody:htmlBody });
    return { success:true };
  } catch(err) {
    Logger.log("handleLeaveStatusEmail error: " + err.toString());
    return { success:false, error:err.toString() };
  }
}

// ════════════════════════════════════════════════════════════════════
//  ONE-TIME CLEANUP: Run manually from editor to fix existing sheets
//  Removes duplicate rows and orphan date-only rows from all _calls sheets
// ════════════════════════════════════════════════════════════════════
function fixAllCallSheets() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ss.getSheets()
    .filter(s => s.getName().endsWith("_calls"))
    .forEach(sheet => {
      const lastRow = sheet.getLastRow();
      if (lastRow < 2) return;

      // Force column E to text
      sheet.getRange("E:E").setNumberFormat("@STRING@");

      const data = sheet.getRange(2, 1, lastRow - 1, 5).getValues();
      const seen = new Set();
      // Collect duplicate/orphan rows (process bottom-up for safe deletion)
      const toDelete = [];
      for (let i = data.length - 1; i >= 0; i--) {
        const phone    = String(data[i][1]).replace(/\D/g, "");
        const calledAt = normalizeCalledAt(data[i][4]);
        const key      = phone + "|" + calledAt;
        if (!phone || seen.has(key)) {
          toDelete.push(i + 2); // sheet row index
        } else {
          seen.add(key);
        }
      }
      toDelete.forEach(rowIdx => sheet.deleteRow(rowIdx));
      Logger.log(sheet.getName() + ": removed " + toDelete.length + " rows");

      // Re-sort after cleanup
      const newLast = sheet.getLastRow();
      if (newLast >= 3) {
        sheet.getRange(2, 1, newLast - 1, 5).sort({ column: 5, ascending: false });
      }
    });
  Logger.log("fixAllCallSheets complete");
}

// ════════════════════════════════════════════════════════════════════
//  Helpers
// ════════════════════════════════════════════════════════════════════
function row_(label, value) {
  return "<tr>" +
    "<td style='padding:8px;border:1px solid #E2E8F0;background:#F8FAFC;width:130px'><b>" + label + "</b></td>" +
    "<td style='padding:8px;border:1px solid #E2E8F0'>" + value + "</td></tr>";
}
function formatDuration(seconds) {
  if (!seconds || seconds <= 0) return "—";
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m > 0 ? m + "m " + s + "s" : s + "s";
}
function col2letter(col) { return String.fromCharCode(64 + col); }
function statusColor(status) {
  switch(status) {
    case "Connected":      return "#C6EFCE";
    case "Interested":     return "#BDD7EE";
    case "Busy":           return "#FFE699";
    case "Not Connected":  return "#EDEDED";
    case "Not Interested": return "#FFC7CE";
    default:               return "#FFFFFF";
  }
}

// ════════════════════════════════════════════════════════════════════
//  Test / Auth helpers
// ════════════════════════════════════════════════════════════════════
function testLeaveEmail() {
  handleLeaveEmail({
    adminEmail:"gulshan12216935@gmail.com",
    empName:"Test User", empEmail:"test@gmail.com",
    leaveType:"Sick Leave", fromDate:"10/01/2025", toDate:"11/01/2025", reason:"Testing"
  });
}
function testLeaveStatusEmail() {
  handleLeaveStatusEmail({
    empEmail:"employee@gmail.com", empName:"Test Employee",
    leaveType:"Casual Leave", fromDate:"10/01/2025", toDate:"11/01/2025",
    status:"Approved"
  });
}
function forceAuth() {
  GmailApp.getInboxThreads(0,1);
  Logger.log("Auth OK");
}
