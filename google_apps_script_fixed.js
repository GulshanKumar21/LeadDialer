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
  const headers = ["Name","Phone","Status","Duration","Called At","Called By","College Name","College City","Notes","Sales Done"];
  sheet.getRange(1,1,1,10).setValues([headers])
       .setBackground("#2D3748").setFontColor("#FFFFFF").setFontWeight("bold");
  sheet.setFrozenRows(1);
  [180,140,130,100,160,150,180,150,200,100].forEach((w,i) => sheet.setColumnWidth(i+1,w));
}
function setupCallHeaders(sheet) {
  const headers = ["NAME","PHONE NUMBER","CALLED AT","CALLED END TIME","DURATION","GAP (IDLE)","STATUS","NOTES","SALES DONE","_MS"];
  const hRange = sheet.getRange(1,1,1,10);
  hRange.setValues([headers])
        .setBackground("#1A365D")
        .setFontColor("#FFFFFF")
        .setFontWeight("bold")
        .setHorizontalAlignment("center");
  sheet.setFrozenRows(1);
  sheet.getRange("C:D").setNumberFormat("@STRING@");
  sheet.getRange("J:J").setNumberFormat("@STRING@");
  try { sheet.showColumns(8); } catch(e) {}
  try { sheet.hideColumns(10); } catch(e) {}
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
//  Migration Helper
//  BUG FIX: expected array was mixed-case ("Name","Phone Number") but
//  setupCallHeaders sets UPPERCASE ("NAME","PHONE NUMBER").
//  This mismatch caused isUpToDate=false on EVERY call → infinite
//  re-migration → column lookups return -1 → phone="" → orphan-row
//  cleanup deleted ALL data. Fixed by using UPPERCASE expected array
//  and case-insensitive findCol() helper.
// ════════════════════════════════════════════════════════════════════
function migrateEmployeeSheet(employeeName) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const leadsSheet = ss.getSheetByName(employeeName);
  const callsSheet = ss.getSheetByName(employeeName + "_calls");

  if (callsSheet) {
    if (leadsSheet) {
      try { ss.deleteSheet(leadsSheet); } catch(e) {
        Logger.log("Failed to delete old leads sheet: " + e.message);
      }
    }
    callsSheet.setName(employeeName);
  }

  const sheet = ss.getSheetByName(employeeName);
  if (!sheet) return;

  const lastRow = sheet.getLastRow();
  const maxCols = sheet.getMaxColumns();

  if (lastRow === 0) { setupCallHeaders(sheet); return; }

  // Read headers; build uppercase version for comparison
  const rawHeaders = sheet.getRange(1, 1, 1, maxCols).getValues()[0];
  const headers    = rawHeaders.map(h => String(h).trim());
  const headersUC  = headers.map(h => h.toUpperCase());

  // Check for OLD lead-format sheet (col F was "Called By")
  const h5uc = headersUC[5] || "";
  if (h5uc === "CALLED BY" || h5uc === "COLLEGE NAME") {
    sheet.clearContents().clearFormats();
    setupCallHeaders(sheet);
    return;
  }

  // ── FIX: expected in UPPERCASE to match setupCallHeaders output ───
  const expected = [
    "NAME","PHONE NUMBER","CALLED AT","CALLED END TIME",
    "DURATION","GAP (IDLE)","STATUS","NOTES","SALES DONE","_MS"
  ];

  let isUpToDate = headersUC.length >= expected.length;
  if (isUpToDate) {
    for (let i = 0; i < expected.length; i++) {
      if (headersUC[i] !== expected[i]) { isUpToDate = false; break; }
    }
  }

  if (!isUpToDate) {
    if (lastRow > 1) {
      const oldData = sheet.getRange(2, 1, lastRow - 1, maxCols).getValues();
      const newData = [];

      // ── FIX: case-insensitive column lookup ───────────────────────
      function findCol(names) {
        for (const n of names) {
          const idx = headersUC.indexOf(n.toUpperCase());
          if (idx !== -1) return idx;
        }
        return -1;
      }

      const nameIdx      = findCol(["Name","NAME"]);
      const phoneIdx     = findCol(["Phone Number","PHONE NUMBER","Phone","PHONE"]);
      const calledAtIdx  = findCol(["Called At","CALLED AT"]);
      const calledEndIdx = findCol(["Called End Time","CALLED END TIME"]);
      const durationIdx  = findCol(["Duration","DURATION"]);
      const gapIdx       = findCol(["Gap (Idle)","GAP (IDLE)","Gap","GAP"]);
      const statusIdx    = findCol(["Status","STATUS"]);
      const notesIdx     = findCol(["Notes","NOTES"]);
      const salesIdx     = findCol(["Sales Done","SALES DONE"]);
      const msIdx        = findCol(["_ms","_MS"]);

      oldData.forEach(row => {
        const name      = nameIdx     !== -1 ? String(row[nameIdx])     : "";
        const phone     = phoneIdx    !== -1 ? String(row[phoneIdx])    : "";
        const status    = statusIdx   !== -1 ? String(row[statusIdx])   : "—";
        const durStr    = durationIdx !== -1 ? String(row[durationIdx]) : "—";
        const calledAt  = calledAtIdx !== -1 ? normalizeCalledAt(row[calledAtIdx]) : "—";
        const gapStr    = gapIdx      !== -1 ? String(row[gapIdx])      : "—";
        const notes     = notesIdx    !== -1 ? String(row[notesIdx])    : "";
        const salesDone = salesIdx    !== -1 ? String(row[salesIdx])    : "";
        const msValStr  = msIdx       !== -1 ? String(row[msIdx])       : "";
        const msVal     = parseInt(msValStr) || 0;

        // ── FIX: skip rows with no phone (prevents orphan row creation)
        if (!phone.replace(/\D/g,"")) return;

        let endTimeStr = "—";
        if (calledEndIdx !== -1 && row[calledEndIdx] && String(row[calledEndIdx]).trim() !== "—") {
          endTimeStr = normalizeCalledAt(row[calledEndIdx]);
        } else if (msVal > 0) {
          const hM = durStr.match(/(\d+)h/), mM = durStr.match(/(\d+)m/), sM = durStr.match(/([\d]+)s/);
          const dSec = (hM?parseInt(hM[1])*3600:0)+(mM?parseInt(mM[1])*60:0)+(sM?parseInt(sM[1]):0);
          endTimeStr = Utilities.formatDate(new Date(msVal+dSec*1000), Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm");
        } else if (calledAt && calledAt !== "—" && calledAt !== "Not called") {
          endTimeStr = calledAt;
        }

        newData.push([name,phone,calledAt,endTimeStr,durStr,gapStr,status,notes,salesDone,msValStr]);
      });

      sheet.clearContents().clearFormats();
      setupCallHeaders(sheet);
      if (newData.length > 0) {
        sheet.getRange(2, 1, newData.length, 10).setValues(newData);
        const bgColors = newData.map(row => {
          const bg = (row[8] === "✅ Yes") ? "#D4EDDA" : statusColor(row[6]);
          return Array(9).fill(bg);
        });
        sheet.getRange(2, 1, bgColors.length, 9).setBackgrounds(bgColors);
      }
    } else {
      sheet.clearContents().clearFormats();
      setupCallHeaders(sheet);
    }
  }
}

// ════════════════════════════════════════════════════════════════════
//  Office Working Hours Constants
// ════════════════════════════════════════════════════════════════════
const WORK_START_HOUR = 11;
const WORK_START_MIN  = 15;
const WORK_END_HOUR   = 20;
const WORK_END_MIN    = 0;
const LUNCH_BREAK_MIN = 60;
const SHORT_BREAK_MIN = 30;
const NET_WORK_MIN    = (WORK_END_HOUR*60+WORK_END_MIN)-(WORK_START_HOUR*60+WORK_START_MIN)-LUNCH_BREAK_MIN-SHORT_BREAK_MIN; // 435

function normalizeCalledAt(val) {
  if (!val && val !== 0) return "";
  if (val instanceof Date) return Utilities.formatDate(val, Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm");
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
    const parts = s.split(" "), dateParts = parts[0].split("/");
    if (dateParts.length !== 3) return s;
    return dateParts[2]+"-"+dateParts[1]+"-"+dateParts[0]+" "+(parts[1]||"00:00");
  } catch(e) { return String(str); }
}

// ════════════════════════════════════════════════════════════════════
//  🔒 SECURITY: HMAC-SHA256 Authentication
//  Every request from the Android app must include X-Auth-Token header
//  containing HMAC-SHA256(body, SECRET_KEY).
//  Change SECRET_KEY to match BuildConfig.GAS_SECRET_TOKEN in the Android app.
// ════════════════════════════════════════════════════════════════════
const SECRET_KEY = "adyapan-crm-secret-2026-hmac-key"; // Must match Android BuildConfig.GAS_SECRET_TOKEN

function verifyHmac(body, token) {
  if (!token || token === "") return false;
  try {
    const mac = Utilities.computeHmacSha256Signature(body, SECRET_KEY, Utilities.Charset.UTF_8);
    const expected = mac.map(b => ('0' + (b < 0 ? b + 256 : b).toString(16)).slice(-2)).join('');
    return expected === token;
  } catch(e) {
    Logger.log("HMAC verify error: " + e.message);
    return false;
  }
}

function doGet(e) {
  try {
    // 🔒 SECURITY: Verify HMAC token for GET requests
    const token = (e.parameter && e.parameter["X-Auth-Token"]) || 
                  (e.headers && e.headers["X-Auth-Token"]) || "";
    // For GET, sign the phone query string as the body
    const queryBody = JSON.stringify(e.parameter || {});
    if (!verifyHmac(queryBody, token)) {
      return ContentService.createTextOutput(JSON.stringify({error: "Unauthorized", called: false}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    if (e.parameter.phones) {
      const phones = e.parameter.phones.split(","), result = {};
      phones.forEach(p => { const c=p.trim().replace(/\D/g,""); if(c) result[c]=checkPhoneInternal(c); });
      return ContentService.createTextOutput(JSON.stringify({data:result})).setMimeType(ContentService.MimeType.JSON);
    }
    if (e.parameter.phone) return ContentService.createTextOutput(JSON.stringify(checkPhoneInternal(e.parameter.phone))).setMimeType(ContentService.MimeType.JSON);
    return ContentService.createTextOutput(JSON.stringify({called:false})).setMimeType(ContentService.MimeType.JSON);
  } catch(err) { return ContentService.createTextOutput(JSON.stringify({called:false,error:err.message})).setMimeType(ContentService.MimeType.JSON); }
}
function checkPhoneInternal(phone) {
  const ss=SpreadsheetApp.getActiveSpreadsheet();
  const sheets=ss.getSheets().filter(s=>s.getName()!=="📊 Summary"&&s.getName()!=="📅 Attendance");
  const cp=String(phone).replace(/\D/g,"");
  for(const sheet of sheets){
    const lr=sheet.getLastRow(); if(lr<2) continue;
    const data=sheet.getRange(2,1,lr-1,10).getValues();
    for(const row of data){
      const rp=String(row[1]).replace(/\D/g,"");
      if(rp===cp){const st=String(row[6]);if(st&&st!=="Pending"&&st!=="")return{called:true,calledBy:sheet.getName(),status:st,collegeName:"",collegeCity:""};}
    }
  }
  return{called:false,calledBy:"",status:"",collegeName:"",collegeCity:""};
}
function doPost(e) {
  try {
    // 🔒 SECURITY: Verify HMAC token — reject unauthorized requests
    const token = (e.headers && e.headers["X-Auth-Token"]) || "";
    const rawBody = e.postData && e.postData.contents ? e.postData.contents : "";
    if (!verifyHmac(rawBody, token)) {
      Logger.log("🔒 REJECTED: Invalid or missing X-Auth-Token");
      return ContentService.createTextOutput(JSON.stringify({error: "Unauthorized"}))
        .setMimeType(ContentService.MimeType.JSON);
    }
    const data=JSON.parse(rawBody), employeeName=data.employeeName||"Unknown", type=data.type||"leads";
    if(type==="leads"&&data.leads){syncLeads(employeeName,data.leads);updateSummarySheet();}
    if(type==="callRecords"&&data.records) syncCallRecords(employeeName,data.records);
    if(type==="attendance"&&data.attendance) syncAttendance(employeeName,data.attendance);
    if(type==="leaveEmail") return ContentService.createTextOutput(JSON.stringify(handleLeaveEmail(data))).setMimeType(ContentService.MimeType.JSON);
    if(type==="leaveStatusEmail") return ContentService.createTextOutput(JSON.stringify(handleLeaveStatusEmail(data))).setMimeType(ContentService.MimeType.JSON);
    return ContentService.createTextOutput(JSON.stringify({success:true})).setMimeType(ContentService.MimeType.JSON);
  } catch(err) { return ContentService.createTextOutput(JSON.stringify({error:err.message})).setMimeType(ContentService.MimeType.JSON); }
}
function syncLeads(employeeName,leads){
  if(!leads||leads.length===0) return;
  const sheet=getOrCreateSheet(employeeName), lastRow=sheet.getLastRow();
  if(lastRow<2) return;
  const range=sheet.getRange(2,1,lastRow-1,9), data=range.getValues(), leadMap=new Map();
  leads.forEach(l=>{const cp=String(l.phone||"").replace(/\D/g,"");if(cp)leadMap.set(cp,{notes:l.notes||"",salesDone:l.salesDone?"✅ Yes":""});});
  let modified=false;
  for(let i=0;i<data.length;i++){const p=String(data[i][1]).replace(/\D/g,""),m=leadMap.get(p);if(m&&(String(data[i][7])!==m.notes||String(data[i][8])!==m.salesDone)){data[i][7]=m.notes;data[i][8]=m.salesDone;modified=true;}}
  if(modified){range.setValues(data);sheet.getRange(2,1,data.length,9).setBackgrounds(data.map(r=>Array(9).fill((r[8]==="✅ Yes")?"#D4EDDA":statusColor(r[6]))));}
}
function syncCallRecords(employeeName,records){
  try{migrateEmployeeSheet(employeeName);}catch(e){Logger.log("Migration failed for "+employeeName+": "+e.message);}
  const sheet=getOrCreateSheet(employeeName);
  if(sheet.getLastRow()===0){setupCallHeaders(sheet);}else{sheet.getRange("C:D").setNumberFormat("@STRING@");sheet.getRange("J:J").setNumberFormat("@STRING@");try{sheet.showColumns(8);}catch(e){}try{sheet.hideColumns(10);}catch(e){}}
  const cl=sheet.getLastRow();
  if(cl>=2){const ad=sheet.getRange(2,1,cl-1,10).getValues();for(let i=ad.length-1;i>=0;i--){if(!String(ad[i][1]).replace(/\D/g,""))sheet.deleteRow(i+2);}}
  const el=sheet.getLastRow(),ek=new Set(),pdm=new Map();
  if(el>=2){sheet.getRange(2,1,el-1,10).getValues().forEach(r=>{const p=String(r[1]).replace(/\D/g,""),rm=String(r[9]).trim(),t=normalizeCalledAt(r[2]),mc=(rm&&rm!==""&&rm!=="0")?rm:"";if(p){ek.add(p+"|"+(mc||t));if(!pdm.has(p))pdm.set(p,{notes:String(r[7]).trim(),salesDone:String(r[8]).trim()});}});}
  const sorted=records.filter(r=>String(r.phone||"").replace(/\D/g,"")).map(r=>({...r,_ms:parseInt(String(r.calledAtMs||"").trim())||0})).sort((a,b)=>a._ms-b._ms);
  const newRows=[];
  sorted.forEach((r,idx)=>{
    const phone=String(r.phone||"").replace(/\D/g,""),rawMs=String(r.calledAtMs||"").trim(),sortable=toSortable(r.calledAt),mc=(rawMs&&rawMs!==""&&rawMs!=="0")?rawMs:"",key=phone+"|"+(mc||sortable);
    if(!phone||ek.has(key)) return;
    let gapSec=0;
    if(r._ms>0){
      if(idx===0){const ws=new Date(r._ms);ws.setHours(WORK_START_HOUR,WORK_START_MIN,0,0);const d=r._ms-ws.getTime();gapSec=d>0?Math.round(d/1000):0;}
      else{const prev=sorted[idx-1];if(new Date(prev._ms).toDateString()===new Date(r._ms).toDateString()){const d=r._ms-(prev._ms+(prev.duration||0)*1000);gapSec=d>0?Math.round(d/1000):0;}else{const ws=new Date(r._ms);ws.setHours(WORK_START_HOUR,WORK_START_MIN,0,0);const d=r._ms-ws.getTime();gapSec=d>0?Math.round(d/1000):0;}}
    }else{gapSec=r.gapSeconds||0;}
    const gapStr=gapSec>0?formatDuration(gapSec):"—";
    const endTimeStr=r._ms>0?Utilities.formatDate(new Date(r._ms+(r.duration||0)*1000),Session.getScriptTimeZone(),"yyyy-MM-dd HH:mm"):sortable;
    const det=pdm.get(phone)||{notes:"",salesDone:""};
    const noteVal=(r.notes&&String(r.notes).trim())?String(r.notes).trim():det.notes;
    newRows.push([r.name||"",r.phone||"",sortable,endTimeStr,formatDuration(r.duration||0),gapStr,r.status||"—",noteVal,det.salesDone,rawMs]);
    ek.add(key);
  });
  if(newRows.length>0){
    const ir=sheet.getLastRow()+1,rng=sheet.getRange(ir,1,newRows.length,10);
    rng.setNumberFormat("@STRING@").setValues(newRows).setHorizontalAlignment("center");
    sheet.getRange(ir,1,newRows.length,9).setBackgrounds(newRows.map(row=>Array(9).fill((row[8]==="✅ Yes")?"#D4EDDA":statusColor(row[6]))));
  }
  const tr=sheet.getLastRow();if(tr>=3)sheet.getRange(2,1,tr-1,10).sort({column:3,ascending:false});
  sheet.setFrozenRows(1);
  [180,140,170,170,100,110,140,250,100].forEach((w,i)=>sheet.setColumnWidth(i+1,w));
  try{sheet.showColumns(8);}catch(e){}try{sheet.hideColumns(10);}catch(e){}
  updateSummarySheet();
}
function syncAttendance(employeeName,records){
  if(!records||!Array.isArray(records)) return;
  const sheet=getOrCreateSheet("📅 Attendance");
  if(sheet.getLastRow()===0) setupAttendanceHeaders(sheet);
  records.forEach(rec=>{
    const emp=rec.employeeName||employeeName||"Unknown",date=rec.date||"";if(!date) return;
    const el=sheet.getLastRow();let fr=-1;
    if(el>=2){const ed=sheet.getRange(2,1,el-1,2).getValues();for(let i=0;i<ed.length;i++){let sd=ed[i][1];if(sd instanceof Date)sd=Utilities.formatDate(sd,Session.getScriptTimeZone(),"dd/MM/yyyy");else sd=String(sd).trim();if(String(ed[i][0]).trim()===emp&&sd===date){fr=i+2;break;}}}
    const st=rec.isLate?"🔴 Late":"🟢 On Time",rd=[emp,date,rec.punchInTime||"—",st,rec.lateReason||"—",rec.totalCalls||0],bg=rec.isLate?"#FFF3CD":"#D4EDDA";
    if(fr!==-1){sheet.getRange(fr,1,1,6).setValues([rd]).setBackground(bg).setHorizontalAlignment("center");}
    else{sheet.getRange(sheet.getLastRow()+1,1,1,6).setValues([rd]).setBackground(bg).setHorizontalAlignment("center");}
  });
  if(sheet.getLastRow()>2) sheet.getRange(2,1,sheet.getLastRow()-1,6).sort({column:2,ascending:false});
}
function updateSummarySheet(){
  const ss=SpreadsheetApp.getActiveSpreadsheet();
  let sum=ss.getSheetByName("📊 Summary");if(!sum)sum=ss.insertSheet("📊 Summary",0);
  sum.clearContents().clearFormats();
  sum.getRange(1,1,1,12).setValues([["Employee","Total Calls","Wrong No.","Interested","Busy","Not Connected","Not Interested","Pending/Other","Sales Done","Call Time","Net Idle Time","Efficiency %"]]).setBackground("#2D3748").setFontColor("#FFFFFF").setFontWeight("bold");
  const sheets=ss.getSheets().filter(s=>s.getName()!=="📊 Summary"&&s.getName()!=="📅 Attendance"),rows=[];
  sheets.forEach(sheet=>{
    const lr=sheet.getLastRow();if(lr<2) return;
    const data=sheet.getRange(2,1,lr-1,10).getValues();
    const ce=s=>data.filter(r=>r[6]===s).length,cp=p=>data.filter(r=>String(r[6]).startsWith(p)).length;
    const ik=s=>{const ls=String(s).toLowerCase();return s==="Wrong Number"||s==="Interested"||s==="Busy"||s==="Not Connected"||ls.startsWith("not interested")||ls.includes("sale")||!s||s==="Pending";};
    let tcs=0;data.forEach(r=>{const d=String(r[4]||""),hM=d.match(/(\d+)h/),mM=d.match(/(\d+)m/),sM=d.match(/([\d]+)s/);tcs+=(hM?parseInt(hM[1])*3600:0)+(mM?parseInt(mM[1])*60:0)+(sM?parseInt(sM[1]):0);});
    const tcm=Math.round(tcs/60),ni=Math.max(0,NET_WORK_MIN-tcm),eff=NET_WORK_MIN>0?Math.min(100,Math.round((tcm/NET_WORK_MIN)*100)):0;
    const sc=data.filter(r=>String(r[6]).toLowerCase().includes("sale")||String(r[8]).trim()==="✅ Yes").length;
    rows.push([sheet.getName(),data.length,ce("Wrong Number"),ce("Interested"),ce("Busy"),ce("Not Connected"),cp("Not Interested"),data.filter(r=>!ik(r[6])).length+data.filter(r=>!r[6]||r[6]==="Pending").length,sc,formatDuration(tcs),ni+" min",eff+"%"]);
  });
  if(rows.length>0){
    sum.getRange(2,1,rows.length,12).setValues(rows);
    rows.forEach((_,i)=>{sum.getRange(i+2,3).setBackground("#EDEDED");sum.getRange(i+2,4).setBackground("#BDD7EE");sum.getRange(i+2,5).setBackground("#FFE699");sum.getRange(i+2,8).setBackground("#FFC7CE");sum.getRange(i+2,10).setBackground("#E8DAEF");sum.getRange(i+2,11).setBackground("#FFF3CD");sum.getRange(i+2,12).setBackground("#D4EDDA");});
    const tr=rows.length+2;sum.getRange(tr,1).setValue("✅ TOTAL").setFontWeight("bold");
    for(let c=2;c<=9;c++) sum.getRange(tr,c).setFormula("=SUM("+col2letter(c)+"2:"+col2letter(c)+(tr-1)+")").setFontWeight("bold").setBackground("#F0F4F8");
  }
  sum.getRange(rows.length+4,1).setValue("ℹ️ Net Idle = 435 min (7h 15m window) − Call Time | Window: 11:15 AM–8 PM minus 1h lunch & 30m break").setFontColor("#6B7280").setFontStyle("italic");
  sum.setFrozenRows(1);sum.setColumnWidth(1,180);for(let i=2;i<=9;i++)sum.setColumnWidth(i,110);sum.setColumnWidth(10,120);sum.setColumnWidth(11,130);sum.setColumnWidth(12,110);
  
  updateSalesDoneSheet();
}

function updateSalesDoneSheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let salesDoneSheet = ss.getSheetByName("💰 Sales Done");
  if (!salesDoneSheet) {
    salesDoneSheet = ss.insertSheet("💰 Sales Done", 1);
  }
  salesDoneSheet.clearContents().clearFormats();
  
  const headers = ["Employee Name", "Customer Name", "Phone Number", "Called At", "Called End Time", "Duration", "Status", "Notes", "Sales Done"];
  salesDoneSheet.getRange(1, 1, 1, 9).setValues([headers])
                .setBackground("#2D3748").setFontColor("#FFFFFF").setFontWeight("bold");
  salesDoneSheet.setFrozenRows(1);
  
  const exclude = ["📊 Summary", "📅 Attendance", "💰 Sales Done"];
  const sheets = ss.getSheets().filter(s => !exclude.includes(s.getName()));
  const allSalesRows = [];
  
  sheets.forEach(sheet => {
    const lastRow = sheet.getLastRow();
    if (lastRow < 2) return;
    
    const data = sheet.getRange(2, 1, lastRow - 1, 10).getValues();
    const empName = sheet.getName();
    
    data.forEach(row => {
      const status = String(row[6]).toLowerCase();
      const sales = String(row[8]).trim();
      
      if (status.includes("sale") || sales === "✅ Yes") {
        allSalesRows.push([
          empName,
          row[0],
          row[1],
          normalizeCalledAt(row[2]),
          normalizeCalledAt(row[3]),
          row[4],
          row[6],
          row[7],
          sales
        ]);
      }
    });
  });
  
  if (allSalesRows.length > 0) {
    allSalesRows.sort((a, b) => {
      const empA = a[0].toLowerCase();
      const empB = b[0].toLowerCase();
      if (empA < empB) return -1;
      if (empA > empB) return 1;
      
      const dateA = toSortable(a[3]);
      const dateB = toSortable(b[3]);
      if (dateA > dateB) return -1;
      if (dateA < dateB) return 1;
      return 0;
    });
    
    salesDoneSheet.getRange(2, 1, allSalesRows.length, 9).setValues(allSalesRows);
    
    const bgColors = allSalesRows.map(() => Array(9).fill("#D4EDDA"));
    salesDoneSheet.getRange(2, 1, bgColors.length, 9).setBackgrounds(bgColors);
  }
  
  [150, 180, 140, 170, 170, 100, 140, 250, 100].forEach((w, i) => salesDoneSheet.setColumnWidth(i + 1, w));
}
function handleLeaveEmail(data){
  try{
    const ae=data.adminEmail||"",en=data.empName||"Employee",eid=data.employeeId||"—",ee=data.empEmail||"",lt=data.leaveType||"Leave",fd=data.fromDate||"—",td=data.toDate||"—",re=data.reason||"No reason provided";
    if(!ae) return{success:false,error:"No admin email"};
    const subj="Leave Request — "+en+" ("+lt+")",hb="<div style='font-family:Arial,sans-serif;max-width:600px'><h2 style='color:#3B82F6'>📋 Leave Request</h2><table style='width:100%;border-collapse:collapse;font-size:14px'>"+row_("Employee Name",en)+row_("Employee ID",eid)+row_("Email",ee)+row_("Leave Type",lt)+row_("From",fd)+row_("To",td)+row_("Reason",re)+"</table><p style='color:#6B7280;font-size:12px;margin-top:16px'>— Adyapan CRM</p></div>";
    GmailApp.sendEmail(ae,subj,"Employee Name: "+en+"\nLeave Type: "+lt+"\nFrom: "+fd+"\nTo: "+td+"\nReason: "+re,{htmlBody:hb,replyTo:ee});
    return{success:true};
  }catch(err){Logger.log("handleLeaveEmail error: "+err);return{success:false,error:err.toString()};}
}
function handleLeaveStatusEmail(data){
  try{
    const ee=data.empEmail||"",en=data.empName||"Employee",lt=data.leaveType||"Leave",fd=data.fromDate||"—",td=data.toDate||"—",st=data.status||"Updated";
    if(!ee) return{success:false,error:"No employee email"};
    const ia=st==="Approved",em=ia?"✅":"❌",co=ia?"#16A34A":"#DC2626",msg=ia?"Your leave has been <b>approved</b>. Enjoy your time off! 🎉":"Your leave has been <b>rejected</b>. Contact your manager for more details.";
    const hb="<div style='font-family:Arial,sans-serif;max-width:600px'><h2 style='color:"+co+"'>"+em+" Leave "+st+"</h2><p>Dear <b>"+en+"</b>,</p><p>"+msg+"</p><table style='width:100%;border-collapse:collapse;font-size:14px;margin-top:12px'>"+row_("Leave Type",lt)+row_("From",fd)+row_("To",td)+row_("Status",em+" "+st)+"</table><p style='color:#6B7280;font-size:12px;margin-top:16px'>— Adyapan CRM</p></div>";
    GmailApp.sendEmail(ee,em+" Leave "+st+" — "+lt,"Dear "+en+",\n\nYour leave has been "+st.toLowerCase()+".\n\nLeave Type: "+lt+"\nFrom: "+fd+"\nTo: "+td+"\nStatus: "+st+"\n\n"+(ia?"Enjoy your time off!":"Contact your manager.")+"\n\n— Adyapan CRM",{htmlBody:hb});
    return{success:true};
  }catch(err){Logger.log("handleLeaveStatusEmail error: "+err);return{success:false,error:err.toString()};}
}
function fixAllCallSheets(){
  const ss=SpreadsheetApp.getActiveSpreadsheet();
  ss.getSheets().filter(s=>!["📊 Summary","📅 Attendance"].includes(s.getName())).forEach(sheet=>{
    const lr=sheet.getLastRow();if(lr<2) return;
    sheet.getRange("E:H").setNumberFormat("@STRING@");
    const data=sheet.getRange(2,1,lr-1,10).getValues(),seen=new Set(),td=[];
    for(let i=data.length-1;i>=0;i--){const p=String(data[i][1]).replace(/\D/g,""),rm=String(data[i][9]).trim(),ca=normalizeCalledAt(data[i][2]),mc=(rm&&rm!==""&&rm!=="0")?rm:"",k=p+"|"+(mc||ca);if(!p||seen.has(k)){td.push(i+2);}else{seen.add(k);}}
    td.forEach(r=>sheet.deleteRow(r));Logger.log(sheet.getName()+": removed "+td.length+" rows");
    const nl=sheet.getLastRow();if(nl>=3)sheet.getRange(2,1,nl-1,10).sort({column:3,ascending:false});
    try{sheet.hideColumns(10);}catch(e){}
  });Logger.log("fixAllCallSheets complete");
}
function row_(label,value){return "<tr><td style='padding:8px;border:1px solid #E2E8F0;background:#F8FAFC;width:130px'><b>"+label+"</b></td><td style='padding:8px;border:1px solid #E2E8F0'>"+value+"</td></tr>";}
function formatDuration(seconds){if(!seconds||seconds<=0)return"—";const h=Math.floor(seconds/3600),m=Math.floor((seconds%3600)/60),s=seconds%60;if(h>0)return h+"h "+m+"m "+s+"s";if(m>0)return m+"m "+s+"s";return s+"s";}
function col2letter(col){return String.fromCharCode(64+col);}
function statusColor(status){if(!status)return"#FFFFFF";if(status==="Wrong Number")return"#EDEDED";if(status==="Interested")return"#BDD7EE";if(status==="Busy")return"#FFE699";if(status==="Not Connected")return"#EDEDED";if(String(status).startsWith("Not Interested"))return"#FFC7CE";if(status!=="Pending")return"#E8DAEF";return"#FFFFFF";}
function runManualMigrationForAllSheets(){
  const ss=SpreadsheetApp.getActiveSpreadsheet(),exc=["📊 Summary","📅 Attendance"],names=new Set();
  ss.getSheets().forEach(s=>{const n=s.getName();if(exc.includes(n))return;names.add(n.endsWith("_calls")?n.replace("_calls",""):n);});
  names.forEach(n=>{Logger.log("Migrating: "+n);try{migrateEmployeeSheet(n);}catch(e){Logger.log("Error: "+e.message);}});
  updateSummarySheet();Logger.log("All sheets migrated!");
}
function upgradeAllHeadersNow(){
  const ss=SpreadsheetApp.getActiveSpreadsheet(),exc=["📊 Summary","📅 Attendance"];
  ss.getSheets().forEach(sheet=>{const n=sheet.getName();if(n==="📅 Attendance"){sheet.getRange(1,1,1,6).setValues([["EMPLOYEE","DATE","PUNCH-IN TIME","STATUS","LATE REASON","TOTAL CALLS"]]).setBackground("#744210").setFontColor("#FFFFFF").setFontWeight("bold").setHorizontalAlignment("center");}else if(!exc.includes(n)&&sheet.getMaxColumns()>=9){sheet.getRange(1,1,1,10).setValues([["NAME","PHONE NUMBER","CALLED AT","CALLED END TIME","DURATION","GAP (IDLE)","STATUS","NOTES","SALES DONE","_MS"]]).setBackground("#1A365D").setFontColor("#FFFFFF").setFontWeight("bold").setHorizontalAlignment("center");try{sheet.showColumns(8);}catch(e){}try{sheet.hideColumns(10);}catch(e){}}});Logger.log("All headers upgraded.");
}
function centerAllData(){const ss=SpreadsheetApp.getActiveSpreadsheet();ss.getSheets().filter(s=>s.getName()!=="📊 Summary").forEach(sheet=>{const lr=sheet.getLastRow(),lc=sheet.getLastColumn();if(lr>=2&&lc>=1)sheet.getRange(2,1,lr-1,lc).setHorizontalAlignment("center");});Logger.log("Done!");}
function testLeaveEmail(){handleLeaveEmail({adminEmail:"hr@adyapan.com,mounika@adyapan.com,gulshan12216935@gmail.com",empName:"Test User",empEmail:"test@gmail.com",leaveType:"Sick Leave",fromDate:"10/01/2025",toDate:"11/01/2025",reason:"Testing"});}
function testLeaveStatusEmail(){handleLeaveStatusEmail({empEmail:"employee@gmail.com",empName:"Test Employee",leaveType:"Casual Leave",fromDate:"10/01/2025",toDate:"11/01/2025",status:"Approved"});}
function forceAuth(){GmailApp.getInboxThreads(0,1);Logger.log("Auth OK");}
