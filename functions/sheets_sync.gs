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
  // Kept for legacy compatibility
  const headers = ["Name","Phone","Status","Duration","Called At","Called By","College Name","College City","Notes","Sales Done"];
  sheet.getRange(1,1,1,10).setValues([headers])
       .setBackground("#2D3748").setFontColor("#FFFFFF").setFontWeight("bold");
  sheet.setFrozenRows(1);
  [180,140,130,100,160,150,180,150,200,100].forEach((w,i) => sheet.setColumnWidth(i+1,w));
}

function setupCallHeaders(sheet) {
  // 🌟 New column layout:
  // 1:Name, 2:Phone Number, 3:Called At, 4:Called End Time, 5:Duration, 6:Gap (Idle), 7:Status, 8:Notes, 9:Sales Done, 10:_ms (hidden)
  const headers = ["Name","Phone Number","Called At","Called End Time","Duration","Gap (Idle)","Status","Notes","Sales Done","_ms"];
  sheet.getRange(1,1,1,10).setValues([headers])
       .setBackground("#1A365D").setFontColor("#FFFFFF").setFontWeight("bold");
  sheet.setFrozenRows(1);
  sheet.getRange("C:D").setNumberFormat("@STRING@");
  sheet.getRange("J:J").setNumberFormat("@STRING@");
  sheet.hideColumns(10);
  [180,140,170,170,100,110,140,250,100,1].forEach((w,i) => sheet.setColumnWidth(i+1,w));
}

function setupAttendanceHeaders(sheet) {
  const headers = ["Employee","Date","Punch-In Time","Status","Late Reason","Total Calls"];
  sheet.getRange(1,1,1,6).setValues([headers])
       .setBackground("#744210").setFontColor("#FFFFFF").setFontWeight("bold");
  sheet.setFrozenRows(1);
  [160,120,130,100,250,110].forEach((w,i) => sheet.setColumnWidth(i+1,w));
}

// ════════════════════════════════════════════════════════════════════
//  Migration Helper: Automatically renames and upgrades old sheets
// ════════════════════════════════════════════════════════════════════
function migrateEmployeeSheet(employeeName) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const leadsSheet = ss.getSheetByName(employeeName);
  const callsSheet = ss.getSheetByName(employeeName + "_calls");
  
  if (callsSheet) {
    // If the leads sheet exists, delete it first
    if (leadsSheet) {
      try {
        ss.deleteSheet(leadsSheet);
      } catch(e) {
        Logger.log("Failed to delete old leads sheet: " + e.message);
      }
    }
    // Rename calls sheet to employeeName
    callsSheet.setName(employeeName);
  }
  
  const sheet = ss.getSheetByName(employeeName);
  if (sheet) {
    const lastRow = sheet.getLastRow();
    const maxCols = sheet.getMaxColumns();
    
    // Check if this sheet is in old lead format and needs resetting
    if (lastRow > 0) {
      const headerVal = sheet.getRange(1, 6).getValue(); // F1 (Called By in leads layout)
      if (headerVal === "Called By" || headerVal === "College Name") {
        sheet.clearContents().clearFormats();
        setupCallHeaders(sheet);
        return;
      }
    }
    
    // Check if we need to migrate from old formats to the new 10-column layout
    if (lastRow > 0) {
      const headers = sheet.getRange(1, 1, 1, maxCols).getValues()[0].map(h => String(h).trim());
      
      const expected = ["Name", "Phone Number", "Called At", "Called End Time", "Duration", "Gap (Idle)", "Status", "Notes", "Sales Done", "_ms"];
      let isUpToDate = true;
      if (headers.length < expected.length) {
        isUpToDate = false;
      } else {
        for (let i = 0; i < expected.length; i++) {
          if (headers[i] !== expected[i]) {
            isUpToDate = false;
            break;
          }
        }
      }
      
      if (!isUpToDate) {
        if (lastRow > 1) {
          // Read existing rows
          const oldData = sheet.getRange(2, 1, lastRow - 1, maxCols).getValues();
          const newData = [];
          
          // Find column indices dynamically
          const nameIdx = headers.indexOf("Name");
          let phoneIdx = headers.indexOf("Phone Number");
          if (phoneIdx === -1) phoneIdx = headers.indexOf("Phone");
          
          const calledAtIdx = headers.indexOf("Called At");
          const calledEndIdx = headers.indexOf("Called End Time");
          const durationIdx = headers.indexOf("Duration");
          
          let gapIdx = headers.indexOf("Gap (Idle)");
          if (gapIdx === -1) gapIdx = headers.indexOf("Gap");
          
          const statusIdx = headers.indexOf("Status");
          const notesIdx = headers.indexOf("Notes");
          const salesIdx = headers.indexOf("Sales Done");
          const msIdx = headers.indexOf("_ms");
          
          oldData.forEach(row => {
            const name = nameIdx !== -1 ? String(row[nameIdx]) : "";
            const phone = phoneIdx !== -1 ? String(row[phoneIdx]) : "";
            const status = statusIdx !== -1 ? String(row[statusIdx]) : "—";
            const durationStr = durationIdx !== -1 ? String(row[durationIdx]) : "—";
            const calledAtStr = calledAtIdx !== -1 ? normalizeCalledAt(row[calledAtIdx]) : "—";
            const gapStr = gapIdx !== -1 ? String(row[gapIdx]) : "—";
            const notes = notesIdx !== -1 ? String(row[notesIdx]) : "";
            const salesDone = salesIdx !== -1 ? String(row[salesIdx]) : "";
            const msValStr = msIdx !== -1 ? String(row[msIdx]) : "";
            const msVal = parseInt(msValStr) || 0;
            
            // Calculate Called End Time if missing or invalid
            let endTimeStr = "—";
            if (calledEndIdx !== -1 && row[calledEndIdx] && String(row[calledEndIdx]).trim() !== "—") {
              endTimeStr = normalizeCalledAt(row[calledEndIdx]);
            } else if (msVal > 0) {
              const hMatch = durationStr.match(/(\d+)h/);
              const mMatch = durationStr.match(/(\d+)m/);
              const sMatch = durationStr.match(/([\d]+)s/);
              const durationSec = (hMatch ? parseInt(hMatch[1]) * 3600 : 0)
                                + (mMatch ? parseInt(mMatch[1]) * 60 : 0)
                                + (sMatch ? parseInt(sMatch[1]) : 0);
              const endMs = msVal + (durationSec * 1000);
              endTimeStr = Utilities.formatDate(new Date(endMs), Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm");
            } else if (calledAtStr && calledAtStr !== "—" && calledAtStr !== "Not called") {
              endTimeStr = calledAtStr;
            }
            
            newData.push([
              name,
              phone,
              calledAtStr, // Called At
              endTimeStr,  // Called End Time
              durationStr, // Duration
              gapStr,      // Gap (Idle)
              status,      // Status
              notes,       // Notes
              salesDone,   // Sales Done
              msValStr     // _ms
            ]);
          });
          
          sheet.clearContents().clearFormats();
          setupCallHeaders(sheet);
          if (newData.length > 0) {
            sheet.getRange(2, 1, newData.length, 10).setValues(newData);
            const bgColors = [];
            newData.forEach(row => {
              const status = row[6];
              const sales = row[8];
              const bg = (sales === "✅ Yes") ? "#D4EDDA" : statusColor(status);
              bgColors.push(Array(9).fill(bg));
            });
            sheet.getRange(2, 1, bgColors.length, 9).setBackgrounds(bgColors);
          }
        } else {
          sheet.clearContents().clearFormats();
          setupCallHeaders(sheet);
        }
      }
    }
  }
}

// ════════════════════════════════════════════════════════════════════
//  Office Working Hours Constants
// ════════════════════════════════════════════════════════════════════
const WORK_START_HOUR   = 11;   // 11 AM
const WORK_START_MIN    = 15;   // 11:15 AM
const WORK_END_HOUR     = 20;   // 8 PM
const WORK_END_MIN      = 0;
const LUNCH_BREAK_MIN   = 60;   // 1 hour lunch
const SHORT_BREAK_MIN   = 30;   // 30 min break
const NET_WORK_MIN      = (WORK_END_HOUR * 60 + WORK_END_MIN)
                        - (WORK_START_HOUR * 60 + WORK_START_MIN)
                        - LUNCH_BREAK_MIN - SHORT_BREAK_MIN;  // = 435 min

// ════════════════════════════════════════════════════════════════════
//  Normalize a cell value that may be a Date object or string.
// ════════════════════════════════════════════════════════════════════
function normalizeCalledAt(val) {
  if (!val && val !== 0) return "";
  if (val instanceof Date) {
    return Utilities.formatDate(val, Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm");
  }
  const s = String(val).trim();
  if (!s || s === "Not called" || s === "—") return s;
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.substring(0, 16);
  return s;
}

function toSortable(str) {
  if (!str || str === "Not called") return "Not called";
  try {
    const s = String(str).trim();
    if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.substring(0, 16);
    const parts     = s.split(" ");
    const dateParts = parts[0].split("/");
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
    s.getName() !== "📅 Attendance"
  );
  const checkPhone = String(phone).replace(/\D/g,"");
  for (const sheet of sheets) {
    const lastRow = sheet.getLastRow();
    if (lastRow < 2) continue;
    const data = sheet.getRange(2,1,lastRow-1,10).getValues();
    for (const row of data) {
      const rowPhone = String(row[1]).replace(/\D/g,"");
      if (rowPhone === checkPhone) {
        const status = String(row[6]); // Column G is Status (index 6)
        if (status && status !== "Pending" && status !== "") {
          return { called:true, calledBy:sheet.getName(),
                   status:status, collegeName:"", collegeCity:"" };
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
      syncAttendance(employeeName, data.attendance);
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
//  syncLeads — Updates notes and sales status on matching call rows
// ════════════════════════════════════════════════════════════════════
function syncLeads(employeeName, leads) {
  if (!leads || leads.length === 0) return;
  const sheet = getOrCreateSheet(employeeName);
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return;
  
  // Read cols A to I (9 columns): Name(0), Phone(1), CalledAt(2), CalledEnd(3), Duration(4), Gap(5), Status(6), Notes(7), SalesDone(8)
  const range = sheet.getRange(2, 1, lastRow - 1, 9);
  const data = range.getValues();
  
  const leadMap = new Map();
  leads.forEach(lead => {
    const cleanPhone = String(lead.phone || "").replace(/\D/g, "");
    if (cleanPhone) {
      leadMap.set(cleanPhone, {
        notes: lead.notes || "",
        salesDone: lead.salesDone ? "✅ Yes" : ""
      });
    }
  });
  
  let modified = false;
  for (let i = 0; i < data.length; i++) {
    const phone = String(data[i][1]).replace(/\D/g, "");
    const match = leadMap.get(phone);
    if (match) {
      const existingNotes = String(data[i][7]);
      const existingSales = String(data[i][8]);
      if (existingNotes !== match.notes || existingSales !== match.salesDone) {
        data[i][7] = match.notes;
        data[i][8] = match.salesDone;
        modified = true;
      }
    }
  }
  
  if (modified) {
    range.setValues(data);
    
    // Batch update background colors
    const bgColors = [];
    for (let i = 0; i < data.length; i++) {
      const status = data[i][6];
      const sales = data[i][8];
      const bg = (sales === "✅ Yes") ? "#D4EDDA" : statusColor(status);
      bgColors.push(Array(9).fill(bg));
    }
    sheet.getRange(2, 1, bgColors.length, 9).setBackgrounds(bgColors);
  }
}

// ════════════════════════════════════════════════════════════════════
//  syncCallRecords
// ════════════════════════════════════════════════════════════════════
function syncCallRecords(employeeName, records) {
  try {
    migrateEmployeeSheet(employeeName);
  } catch(e) {
    Logger.log("Migration failed for " + employeeName + ": " + e.message);
  }

  const sheetName = employeeName;
  const sheet     = getOrCreateSheet(sheetName);
  if (sheet.getLastRow() === 0) {
    setupCallHeaders(sheet);
  } else {
    sheet.getRange("C:D").setNumberFormat("@STRING@");
    sheet.getRange("J:J").setNumberFormat("@STRING@");
    try { sheet.hideColumns(10); } catch(e) {}
  }
  
  // ── Step 1: Clean orphan rows (Phone column is empty) ──────────────
  const currentLast = sheet.getLastRow();
  if (currentLast >= 2) {
    const allData = sheet.getRange(2, 1, currentLast - 1, 10).getValues();
    for (let i = allData.length - 1; i >= 0; i--) {
      const phone = String(allData[i][1]).replace(/\D/g, "");
      if (!phone) sheet.deleteRow(i + 2);
    }
  }
  
  // ── Step 2: Build existing key set & phone details map ─────────────
  const existingLastRow = sheet.getLastRow();
  const existingKeys    = new Set();
  const phoneDetailsMap = new Map();
  if (existingLastRow >= 2) {
    const existingData = sheet.getRange(2, 1, existingLastRow - 1, 10).getValues();
    existingData.forEach(row => {
      const phone   = String(row[1]).replace(/\D/g, "");
      const rawMs   = String(row[9]).trim(); // Col J (_ms)
      const time    = normalizeCalledAt(row[2]); // Col C (Called At)
      const msClean = (rawMs && rawMs !== "" && rawMs !== "0") ? rawMs : "";
      if (phone) {
        existingKeys.add(phone + "|" + (msClean || time));
        if (!phoneDetailsMap.has(phone)) {
          phoneDetailsMap.set(phone, {
            notes: String(row[7]).trim(),
            salesDone: String(row[8]).trim()
          });
        }
      }
    });
  }
  
  // ── Step 3: Parse and Append new rows ──────────────────────────────
  const sorted = records
    .filter(r => String(r.phone||"").replace(/\D/g,""))
    .map(r => ({
      ...r,
      _ms: parseInt(String(r.calledAtMs||"").trim()) || 0
    }))
    .sort((a,b) => a._ms - b._ms);
    
  const newRows = [];
  sorted.forEach((r, idx) => {
    const phone    = String(r.phone || "").replace(/\D/g, "");
    const rawMs    = String(r.calledAtMs || "").trim();
    const sortable = toSortable(r.calledAt);
    const msClean  = (rawMs && rawMs !== "" && rawMs !== "0") ? rawMs : "";
    const key      = phone + "|" + (msClean || sortable);
    
    if (!phone || existingKeys.has(key)) return;
    
    // ── Gap calculation ────────────────────────────────────────────────
    let gapSec = 0;
    const callMs = r._ms;
    
    if (callMs > 0) {
      const workStart = new Date(callMs);
      workStart.setHours(WORK_START_HOUR, WORK_START_MIN, 0, 0);
      const workEnd = new Date(callMs);
      workEnd.setHours(WORK_END_HOUR, WORK_END_MIN, 0, 0);
      
      const isWithinHours = (callMs >= workStart.getTime() && callMs <= workEnd.getTime());
      
      if (isWithinHours) {
        if (idx === 0) {
          // First ever call: gap from 11:15 AM
          const diffMs = callMs - workStart.getTime();
          gapSec = diffMs > 0 ? Math.round(diffMs / 1000) : 0;
        } else {
          const prev = sorted[idx - 1];
          const prevDateStr = new Date(prev._ms).toDateString();
          const currDateStr = new Date(callMs).toDateString();
          
          if (prevDateStr === currDateStr) {
            const prevEndMs = prev._ms + ((prev.duration || 0) * 1000);
            // Verify if previous call ended within working hours
            if (prevEndMs >= workStart.getTime() && prevEndMs <= workEnd.getTime()) {
              const diffMs = callMs - prevEndMs;
              gapSec = diffMs > 0 ? Math.round(diffMs / 1000) : 0;
            } else {
              // If prev call was before workStart, calculate from workStart
              const diffMs = callMs - workStart.getTime();
              gapSec = diffMs > 0 ? Math.round(diffMs / 1000) : 0;
            }
          } else {
            const diffMs = callMs - workStart.getTime();
            gapSec = diffMs > 0 ? Math.round(diffMs / 1000) : 0;
          }
        }
      }
    } else {
      gapSec = r.gapSeconds || 0;
    }
    
    const gapStr = gapSec > 0 ? formatDuration(gapSec) : "—";
    
    // ── Called End Time calculation ────────────────────────────────────
    let endTimeStr = "—";
    if (callMs > 0) {
      const durationSec = r.duration || 0;
      const endMs = callMs + (durationSec * 1000);
      endTimeStr = Utilities.formatDate(new Date(endMs), Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm");
    } else {
      endTimeStr = sortable;
    }
    
    // Get existing details if any
    const details = phoneDetailsMap.get(phone) || { notes: "", salesDone: "" };
    
    // Header order: Name(0), Phone(1), Called At(2), Called End Time(3), Duration(4), Gap(5), Status(6), Notes(7), Sales Done(8), _ms(9)
    newRows.push([
      r.name   || "",
      r.phone  || "",
      sortable,    // Called At
      endTimeStr,  // Called End Time
      formatDuration(r.duration || 0),
      gapStr,      // Gap (Idle)
      r.status || "—",
      r.notes || details.notes || "",
      details.salesDone,
      rawMs        // _ms (hidden)
    ]);
    existingKeys.add(key);
  });
  
  if (newRows.length > 0) {
    const insertRow = sheet.getLastRow() + 1;
    const range     = sheet.getRange(insertRow, 1, newRows.length, 10);
    range.setNumberFormat("@STRING@");
    range.setValues(newRows);
    
    // Batch color the new rows
    const bgColors = [];
    newRows.forEach(row => {
      const status = row[6];
      const sales = row[8];
      const bg = (sales === "✅ Yes") ? "#D4EDDA" : statusColor(status);
      bgColors.push(Array(9).fill(bg));
    });
    sheet.getRange(insertRow, 1, bgColors.length, 9).setBackgrounds(bgColors);
  }
  
  // ── Step 4: Re-sort all rows descending (newest at top) ──────
  const totalRows = sheet.getLastRow();
  if (totalRows >= 3) {
    sheet.getRange(2, 1, totalRows - 1, 10).sort({ column: 3, ascending: false }); // Sort descending by Called At (Col 3)
  }
  sheet.setFrozenRows(1);
  [180,140,170,170,100,110,140,250,100].forEach((w,i) => sheet.setColumnWidth(i+1,w));
  try { sheet.hideColumns(10); } catch(e) {}

  updateSummarySheet();
}

// ════════════════════════════════════════════════════════════════════
//  syncAttendance
// ════════════════════════════════════════════════════════════════════
function syncAttendance(employeeName, records) {
  if (!records || !Array.isArray(records)) {
    Logger.log("syncAttendance: records is empty or not an array");
    return;
  }
  
  const sheet = getOrCreateSheet("📅 Attendance");
  if (sheet.getLastRow() === 0) setupAttendanceHeaders(sheet);
  
  records.forEach(rec => {
    const employee     = rec.employeeName || employeeName || "Unknown";
    const date         = rec.date         || "";
    if (!date) return;

    const existingLast = sheet.getLastRow();
    let   foundRow     = -1;
    
    if (existingLast >= 2) {
      const existingData = sheet.getRange(2,1,existingLast-1,2).getValues();
      for (let i = 0; i < existingData.length; i++) {
        const sheetEmployee = String(existingData[i][0]).trim();
        
        let sheetDate = existingData[i][1];
        if (sheetDate instanceof Date) {
          sheetDate = Utilities.formatDate(sheetDate, Session.getScriptTimeZone(), "dd/MM/yyyy");
        } else {
          sheetDate = String(sheetDate).trim();
        }
        
        if (sheetEmployee === employee && sheetDate === date) {
          foundRow = i + 2; break;
        }
      }
    }
    
    const statusText = rec.isLate ? "🔴 Late" : "🟢 On Time";
    const rowData    = [employee, date, rec.punchInTime||"—", statusText, rec.lateReason||"—", rec.totalCalls||0];
    const bgColor    = rec.isLate ? "#FFF3CD" : "#D4EDDA";
    
    if (foundRow !== -1) {
      sheet.getRange(foundRow, 1, 1, 6).setValues([rowData]).setBackground(bgColor);
    } else {
      sheet.getRange(sheet.getLastRow() + 1, 1, 1, 6).setValues([rowData]).setBackground(bgColor);
    }
  });
  
  if (sheet.getLastRow() > 2) {
    sheet.getRange(2, 1, sheet.getLastRow() - 1, 6).sort({column: 2, ascending: false});
  }
}

// ════════════════════════════════════════════════════════════════════
//  updateSummarySheet
// ════════════════════════════════════════════════════════════════════
function updateSummarySheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let summary = ss.getSheetByName("📊 Summary");
  if (!summary) summary = ss.insertSheet("📊 Summary", 0);
  summary.clearContents().clearFormats();
  const headers = [
    "Employee","Total Calls","Wrong No.","Interested","Busy",
    "Not Connected","Not Interested","Pending/Other","Sales Done",
    "Call Time","Net Idle Time","Efficiency %"
  ];
  summary.getRange(1,1,1,12).setValues([headers])
         .setBackground("#2D3748").setFontColor("#FFFFFF").setFontWeight("bold");
  const sheets = ss.getSheets().filter(s =>
    s.getName() !== "📊 Summary" &&
    s.getName() !== "📅 Attendance"
  );
  const summaryRows = [];
  sheets.forEach(sheet => {
    const lastRow = sheet.getLastRow();
    if (lastRow < 2) return;
    const data = sheet.getRange(2,1,lastRow-1,10).getValues();
    const countExact  = (s)   => data.filter(r => r[6] === s).length;
    const countPrefix = (pfx) => data.filter(r => String(r[6]).startsWith(pfx)).length;
    const isKnown     = (s)   => {
      const ls = String(s).toLowerCase();
      return s === "Wrong Number" || s === "Interested" || s === "Busy" ||
             s === "Not Connected" || ls.startsWith("not interested") ||
             ls.includes("sale") || !s || s === "Pending";
    };
    
    // ── Call time directly from this sheet ──────────────────────────────
    let totalCallSec = 0;
    data.forEach(row => {
      const durStr = String(row[4] || ""); // Col 5 is Duration (index 4)
      const hMatch = durStr.match(/(\d+)h/);
      const mMatch = durStr.match(/(\d+)m/);
      const sMatch = durStr.match(/([\d]+)s/);
      
      totalCallSec += (hMatch ? parseInt(hMatch[1]) * 3600 : 0)
                    + (mMatch ? parseInt(mMatch[1]) * 60 : 0)
                    + (sMatch ? parseInt(sMatch[1]) : 0);
    });
    const totalCallMin = Math.round(totalCallSec / 60);
    const netIdleMin   = Math.max(0, NET_WORK_MIN - totalCallMin);
    const efficiency   = NET_WORK_MIN > 0
      ? Math.min(100, Math.round((totalCallMin / NET_WORK_MIN) * 100))
      : 0;
      
    // Count Sales Done if status contains "sale" or matches "✅ Yes" in col 9 (index 8)
    const salesCount = data.filter(r => {
      const status = String(r[6]).toLowerCase();
      const sales = String(r[8]).trim();
      return status.includes("sale") || sales === "✅ Yes";
    }).length;

    summaryRows.push([
      sheet.getName(),
      data.length, // Total calls
      countExact("Wrong Number"),
      countExact("Interested"),
      countExact("Busy"),
      countExact("Not Connected"),
      countPrefix("Not Interested"),
      data.filter(r => !isKnown(r[6])).length +
        data.filter(r => !r[6] || r[6] === "Pending").length,
      salesCount,                            // Sales Done count
      formatDuration(totalCallSec),          // Call Time
      netIdleMin + " min",                   // Net Idle Time
      efficiency + "%"                       // Efficiency %
    ]);
  });
  if (summaryRows.length > 0) {
    summary.getRange(2,1,summaryRows.length,12).setValues(summaryRows);
    summaryRows.forEach((_,i) => {
      summary.getRange(i+2,3).setBackground("#EDEDED");  
      summary.getRange(i+2,4).setBackground("#BDD7EE");  
      summary.getRange(i+2,5).setBackground("#FFE699");  
      summary.getRange(i+2,8).setBackground("#FFC7CE");  
      summary.getRange(i+2,10).setBackground("#E8DAEF"); 
      summary.getRange(i+2,11).setBackground("#FFF3CD"); 
      summary.getRange(i+2,12).setBackground("#D4EDDA"); 
    });
    const totalRow = summaryRows.length + 2;
    summary.getRange(totalRow,1).setValue("✅ TOTAL").setFontWeight("bold");
    for (let col = 2; col <= 9; col++) {
      summary.getRange(totalRow,col)
        .setFormula("=SUM(" + col2letter(col) + "2:" + col2letter(col) + (totalRow-1) + ")")
        .setFontWeight("bold").setBackground("#F0F4F8");
    }
  }
  summary.getRange(summaryRows.length+4,1)
    .setValue("ℹ️ Net Idle = 435 min (7h 15m window) − Call Time | Window: 11:15 AM–8 PM minus 1h lunch & 30m break")
    .setFontColor("#6B7280").setFontStyle("italic");
  summary.setFrozenRows(1);
  summary.setColumnWidth(1,180);
  for (let i = 2; i <= 9; i++) summary.setColumnWidth(i,110);
  summary.setColumnWidth(10,120);
  summary.setColumnWidth(11,130);
  summary.setColumnWidth(12,110);
}

// ════════════════════════════════════════════════════════════════════
//  handleLeaveEmail
// ════════════════════════════════════════════════════════════════════
function handleLeaveEmail(data) {
  try {
    const adminEmail = data.adminEmail || "";
    const empName    = data.empName    || "Employee";
    const employeeId = data.employeeId || "—";
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
      row_("Employee Name", empName)  +
      row_("Employee ID",   employeeId) +
      row_("Email",         empEmail) +
      row_("Leave Type",    leaveType)+
      row_("From",          fromDate) +
      row_("To",            toDate)   +
      row_("Reason",        reason)   +
      "</table>" +
      "<p style='color:#6B7280;font-size:12px;margin-top:16px'>— Adyapan CRM</p></div>";
      
    const textBody =
      "Employee Name: " + empName   +
      "\nEmployee ID  : " + employeeId + 
      "\nEmail        : " + empEmail  +
      "\nLeave Type   : " + leaveType +
      "\nFrom         : " + fromDate +
      "\nTo           : " + toDate    +
      "\nReason       : " + reason;
      
    GmailApp.sendEmail(adminEmail, subject, textBody, { htmlBody:htmlBody, replyTo:empEmail });
    return { success:true };
  } catch(err) {
    Logger.log("handleLeaveEmail error: " + err.toString());
    return { success:false, error:err.toString() };
  }
}

// ════════════════════════════════════════════════════════════════════
//  handleLeaveStatusEmail
// ════════════════════════════════════════════════════════════════════
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
    const message = isApproved
      ? "Your leave has been <b>approved</b>. Enjoy your time off! 🎉"
      : "Your leave has been <b>rejected</b>. Contact your manager for more details.";
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
      (isApproved ? "Enjoy your time off!" : " Contact your manager for details.") +
      "\n\n— Adyapan CRM";
    GmailApp.sendEmail(empEmail, subject, textBody, { htmlBody:htmlBody });
    return { success:true };
  } catch(err) {
    Logger.log("handleLeaveStatusEmail error: " + err.toString());
    return { success:false, error:err.toString() };
  }
}

// ════════════════════════════════════════════════════════════════════
//  fixAllCallSheets
// ════════════════════════════════════════════════════════════════════
function fixAllCallSheets() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const exclude = ["📊 Summary", "📅 Attendance"];
  ss.getSheets()
    .filter(s => !exclude.includes(s.getName()))
    .forEach(sheet => {
      const lastRow = sheet.getLastRow();
      if (lastRow < 2) return;
      sheet.getRange("E:H").setNumberFormat("@STRING@");
      const data = sheet.getRange(2, 1, lastRow - 1, 10).getValues();
      const seen = new Set();
      const toDelete = [];
      for (let i = data.length - 1; i >= 0; i--) {
        const phone    = String(data[i][1]).replace(/\D/g, "");
        const rawMs    = String(data[i][9]).trim(); // col J is index 9
        const calledAt = normalizeCalledAt(data[i][2]); // col C is index 2
        const msClean  = (rawMs && rawMs !== "" && rawMs !== "0") ? rawMs : "";
        const key      = phone + "|" + (msClean || calledAt);
        if (!phone || seen.has(key)) {
          toDelete.push(i + 2);
        } else {
          seen.add(key);
        }
      }
      toDelete.forEach(rowIdx => sheet.deleteRow(rowIdx));
      Logger.log(sheet.getName() + ": removed " + toDelete.length + " rows");
      const newLast = sheet.getLastRow();
      if (newLast >= 3) {
        sheet.getRange(2, 1, newLast - 1, 10).sort({ column: 3, ascending: false });
      }
      try { sheet.hideColumns(10); } catch(e) {}
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
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  
  if (h > 0) return h + "h " + m + "m " + s + "s";
  if (m > 0) return m + "m " + s + "s";
  return s + "s";
}

function col2letter(col) { return String.fromCharCode(64 + col); }

function statusColor(status) {
  if (!status) return "#FFFFFF";
  if (status === "Wrong Number")                      return "#EDEDED";
  if (status === "Interested")                        return "#BDD7EE";
  if (status === "Busy")                              return "#FFE699";
  if (status === "Not Connected")                     return "#EDEDED";
  if (String(status).startsWith("Not Interested"))    return "#FFC7CE";
  if (status !== "Pending")                           return "#E8DAEF";
  return "#FFFFFF";
}

// Run this manually once from Google Apps Script editor to migrate existing sheets immediately
function runManualMigrationForAllSheets() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const exclude = ["📊 Summary", "📅 Attendance"];
  
  const sheets = ss.getSheets().map(s => s.getName());
  const employeeNames = new Set();
  
  sheets.forEach(name => {
    if (exclude.indexOf(name) !== -1) return;
    if (name.endsWith("_calls")) {
      employeeNames.add(name.replace("_calls", ""));
    } else {
      employeeNames.add(name);
    }
  });
  
  employeeNames.forEach(name => {
    Logger.log("Migrating sheet for: " + name);
    try {
      migrateEmployeeSheet(name);
    } catch(e) {
      Logger.log("Error migrating " + name + ": " + e.message);
    }
  });
  
  updateSummarySheet();
  Logger.log("All sheets migrated successfully!");
}

function testLeaveEmail() {
  handleLeaveEmail({
    adminEmail:"hr@adyapan.com,mounika@adyapan.com,gulshan12216935@gmail.com",
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
