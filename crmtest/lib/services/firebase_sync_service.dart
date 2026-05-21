import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_database/firebase_database.dart';
import 'call_db_service.dart';

// ─────────────────────────────────────────────────────────────────────────────
//  FirebaseSyncService — Flutter equivalent of batchSyncNow() in CallViewModel
//
//  Syncs all local SQLite data (leads + call records) to Firebase RTDB.
//  Called:
//    • Every 50 calls (batch threshold)     — same as Native Android
//    • Before logout                         — data-loss prevention fix
// ─────────────────────────────────────────────────────────────────────────────
class FirebaseSyncService {
  static const int batchThreshold = 50;

  static String? get _uid => FirebaseAuth.instance.currentUser?.uid;

  // ── Sync all pending data (called before logout) ───────────────────────────
  static Future<void> syncAllPendingData() async {
    final uid = _uid;
    if (uid == null) return;

    final db = AppDbService();
    final leads   = await db.getAllLeads();
    final records = await db.getAllRecords();

    if (leads.isNotEmpty)   await _pushLeads(uid, leads);
    if (records.isNotEmpty) await _pushCallRecords(uid, records);
  }

  // ── Batch sync — call after every 50th call record inserted ───────────────
  static Future<void> batchSyncIfNeeded() async {
    final uid = _uid;
    if (uid == null) return;

    final db = AppDbService();
    final todayRecords = await db.getTodaySortedAsc();

    final count = todayRecords.length;
    if (count > 0 && count % batchThreshold == 0) {
      final leads   = await db.getAllLeads();
      final records = await db.getAllRecords();

      if (leads.isNotEmpty)   await _pushLeads(uid, leads);
      if (records.isNotEmpty) await _pushCallRecords(uid, records);
    }
  }

  // ── Push leads to RTDB ────────────────────────────────────────────────────
  static Future<void> _pushLeads(String uid, List leads) async {
    final ref = FirebaseDatabase.instance.ref('employees/$uid/leads');
    final Map<String, dynamic> updates = {};
    for (final lead in leads) {
      final key = '${uid}_${lead.phone}';
      updates[key] = {
        'name':         lead.name,
        'phone':        lead.phone,
        'status':       lead.status,
        'isHotLead':    lead.isHotLead ? 1 : 0,
        'salesDone':    lead.salesDone ? 1 : 0,
        'collegeName':  lead.collegeName ?? '',
        'collegeCity':  lead.collegeCity ?? '',
        'notes':        lead.notes ?? '',
        'calledBy':     lead.calledBy ?? uid,
        'calledAt':     lead.calledAt ?? 0,
      };
    }
    await ref.update(updates);
  }

  // ── Push call records to RTDB ─────────────────────────────────────────────
  static Future<void> _pushCallRecords(String uid, List records) async {
    final ref = FirebaseDatabase.instance.ref('employees/$uid/callRecords');
    final Map<String, dynamic> updates = {};
    for (final r in records) {
      final key = '${uid}_${r.phone}_${r.calledAt}';
      updates[key] = {
        'name':     r.name,
        'phone':    r.phone,
        'status':   r.status,
        'duration': r.duration,
        'calledAt': r.calledAt,
        'calledBy': r.calledBy ?? uid,
      };
    }
    await ref.update(updates);
  }
}
