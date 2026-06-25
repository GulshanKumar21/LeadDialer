import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/foundation.dart';
import 'call_db_service.dart';
import '../models/lead.dart';

// ─────────────────────────────────────────────────────────────────────────────
//  FirebaseSyncService — Flutter equivalent of batchSyncNow() in CallViewModel
//
//  Syncs data to/from Firebase RTDB & Firestore.
//  Features:
//    • batchSyncIfNeeded(): Every 50 calls (batch threshold) -> push to RTDB
//    • syncAllPendingData(): Push before logout
//    • syncFromFirebase(): Pull leads from Firestore & RTDB and merge into local SQLite (crm_database)
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

  // ── Pull and sync from Firebase (Firestore + RTDB) ─────────────────────────
  static Future<void> syncFromFirebase() async {
    final uid = _uid;
    if (uid == null) return;

    final db = AppDbService();

    try {
      // 1. Fetch from Firestore (Hot/Interested leads / SalesDone)
      final firestoreSnap = await FirebaseFirestore.instance
          .collection('leads')
          .where('userId', isEqualTo: uid)
          .get();

      final List<Lead> firestoreLeads = [];
      for (final doc in firestoreSnap.docs) {
        final data = doc.data();
        firestoreLeads.add(Lead(
          name: data['name']?.toString() ?? '',
          phone: data['phone']?.toString() ?? '',
          status: data['status']?.toString() ?? 'Pending',
          notes: data['notes']?.toString() ?? '',
          calledAt: data['calledAt'] as int? ?? 0,
          duration: data['duration'] as int? ?? 0,
          firestoreId: doc.id,
          collegeName: data['collegeName']?.toString() ?? '',
          collegeCity: data['collegeCity']?.toString() ?? '',
          isHotLead: (data['isHotLead'] as int? ?? 0) == 1,
          calledBy: data['calledBy']?.toString() ?? '',
          salesDone: data['salesDone'] == true,
        ));
      }

      // 2. Fetch from RTDB (Pending/Raw leads)
      final rtdbRef = FirebaseDatabase.instance.ref('rtdb_leads/$uid');
      final rtdbSnap = await rtdbRef.get();

      final List<Lead> rtdbLeads = [];
      if (rtdbSnap.exists && rtdbSnap.value != null) {
        final data = rtdbSnap.value;
        if (data is Map) {
          data.forEach((docId, value) {
            if (value is Map) {
              rtdbLeads.add(Lead(
                name: value['name']?.toString() ?? '',
                phone: value['phone']?.toString() ?? '',
                status: value['status']?.toString() ?? 'Pending',
                notes: value['notes']?.toString() ?? '',
                calledAt: value['calledAt'] as int? ?? 0,
                duration: value['duration'] as int? ?? 0,
                firestoreId: docId.toString(),
                collegeName: value['collegeName']?.toString() ?? '',
                collegeCity: value['collegeCity']?.toString() ?? '',
                isHotLead: (value['isHotLead'] as int? ?? 0) == 1,
                calledBy: value['calledBy']?.toString() ?? '',
                salesDone: value['salesDone'] == true,
              ));
            }
          });
        }
      }

      final allRemote = [...firestoreLeads, ...rtdbLeads];

      // Get all local leads
      final localLeads = await db.getAllLeads();
      final Map<String, Lead> localMap = {
        for (final l in localLeads) l.phone: l
      };

      for (final remoteLead in allRemote) {
        final local = localMap[remoteLead.phone];
        if (local == null) {
          // Insert new lead
          await db.insertLead(remoteLead);
        } else if ((remoteLead.calledAt ?? 0) > (local.calledAt ?? 0)) {
          // Update existing lead keeping its local DB ID
          await db.updateLead(remoteLead.copyWith(id: local.id));
        }
      }
    } catch (e) {
      debugPrint('syncFromFirebase error: $e');
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
