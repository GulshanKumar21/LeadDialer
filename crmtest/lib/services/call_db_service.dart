import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'package:flutter/foundation.dart';
import '../models/call_record.dart';
import '../models/lead.dart';
import 'firebase_sync_service.dart';

// Local SQLite database — mirrors native Room database
class AppDbService {
  static final AppDbService _instance = AppDbService._();
  factory AppDbService() => _instance;
  AppDbService._();

  Database? _db;

  Future<Database> get db async {
    _db ??= await _openDb();
    return _db!;
  }

  Future<Database> _openDb() async {
    final path = join(await getDatabasesPath(), 'crm_database');
    return openDatabase(
      path,
      version: 12,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE IF NOT EXISTS leads (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            phone TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'Pending',
            notes TEXT NOT NULL DEFAULT '',
            calledAt INTEGER NOT NULL DEFAULT 0,
            duration INTEGER NOT NULL DEFAULT 0,
            firestoreId TEXT,
            collegeName TEXT NOT NULL DEFAULT '',
            collegeCity TEXT NOT NULL DEFAULT '',
            isHotLead INTEGER NOT NULL DEFAULT 0,
            calledBy TEXT NOT NULL DEFAULT '',
            salesDone INTEGER NOT NULL DEFAULT 0
          )
        ''');
        await db.execute(
          'CREATE UNIQUE INDEX IF NOT EXISTS index_leads_phone ON leads (phone)'
        );
        await db.execute('''
          CREATE TABLE IF NOT EXISTS call_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            phone TEXT NOT NULL,
            name TEXT NOT NULL,
            duration INTEGER NOT NULL,
            calledAt INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'Pending',
            note TEXT NOT NULL DEFAULT '',
            calledBy TEXT NOT NULL DEFAULT ''
          )
        ''');
        await db.execute(
          'CREATE UNIQUE INDEX IF NOT EXISTS index_call_records_phone_calledAt ON call_records (phone, calledAt)'
        );
        await db.execute('''
          CREATE TABLE IF NOT EXISTS attendance (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            date TEXT NOT NULL,
            punchInTime TEXT NOT NULL,
            punchInMs INTEGER NOT NULL DEFAULT 0,
            isLate INTEGER NOT NULL DEFAULT 0,
            lateReason TEXT NOT NULL DEFAULT '',
            totalCalls INTEGER NOT NULL DEFAULT 0,
            employeeName TEXT NOT NULL DEFAULT ''
          )
        ''');
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        // Room fallback to destructive migration logic if needed
      },
    );
  }

  // ── Lead CRUD ────────────────────────────────────────────────────────────
  Future<List<Lead>> getAllLeads() async {
    final d = await db;
    final maps = await d.query('leads');
    return maps.map(Lead.fromMap).toList();
  }

  Future<int> insertLead(Lead lead) async {
    final d = await db;
    return d.insert('leads', lead.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> updateLead(Lead lead) async {
    final d = await db;
    await d.update('leads', lead.toMap(), where: 'id = ?', whereArgs: [lead.id]);
  }

  Future<void> deleteLead(int id) async {
    final d = await db;
    await d.delete('leads', where: 'id = ?', whereArgs: [id]);
  }

  // ── Stats Counts Queries (Parity with Room Dao) ──────────────────────────
  Future<int> getTotalLeadsCount() async {
    final d = await db;
    final res = await d.rawQuery('SELECT COUNT(*) as count FROM leads');
    return Sqflite.firstIntValue(res) ?? 0;
  }

  Future<int> getCalledLeadsCount() async {
    final d = await db;
    final res = await d.rawQuery('SELECT COUNT(*) as count FROM leads WHERE calledAt > 0');
    return Sqflite.firstIntValue(res) ?? 0;
  }

  Future<int> getBusyLeadsCount() async {
    final d = await db;
    final res = await d.rawQuery("SELECT COUNT(*) as count FROM leads WHERE status = 'Busy'");
    return Sqflite.firstIntValue(res) ?? 0;
  }

  Future<int> getSalesLeadsCount() async {
    final d = await db;
    final res = await d.rawQuery('SELECT COUNT(*) as count FROM leads WHERE salesDone = 1');
    return Sqflite.firstIntValue(res) ?? 0;
  }

  Future<int> getConnectedLeadsCount() async {
    final d = await db;
    final res = await d.rawQuery("SELECT COUNT(*) as count FROM leads WHERE status = 'Interested' OR status = 'Connected'");
    return Sqflite.firstIntValue(res) ?? 0;
  }

  Future<int> getPendingLeadsCount() async {
    final d = await db;
    final res = await d.rawQuery("SELECT COUNT(*) as count FROM leads WHERE status = 'Pending'");
    return Sqflite.firstIntValue(res) ?? 0;
  }

  // ── Call Record CRUD ─────────────────────────────────────────────────────
  Future<List<CallRecord>> getAllRecords() async {
    final d = await db;
    final maps = await d.query('call_records',
        orderBy: 'calledAt DESC');
    return maps.map(CallRecord.fromMap).toList();
  }

  /// Save call record to SQLite, then check if 50-call batch sync needed.
  /// Mirrors Native Android CallViewModel.saveRecord() logic exactly.
  Future<int> insertRecord(CallRecord record) async {
    final d = await db;
    final id = await d.insert('call_records', record.toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace);

    // ── Batch sync: every 50 calls → push all data to Firebase RTDB ────────
    // Non-blocking — runs in background, does not delay UI
    FirebaseSyncService.batchSyncIfNeeded().catchError((e) {
      debugPrint('Batch sync error (non-fatal): $e');
    });

    return id;
  }

  // Get today's records sorted ascending (for gap calculation)
  Future<List<CallRecord>> getTodaySortedAsc() async {
    final now = DateTime.now();
    final start = DateTime(now.year, now.month, now.day).millisecondsSinceEpoch;
    final end   = DateTime(now.year, now.month, now.day, 23, 59, 59).millisecondsSinceEpoch;
    final d = await db;
    final maps = await d.query(
      'call_records',
      where: 'calledAt >= ? AND calledAt <= ?',
      whereArgs: [start, end],
      orderBy: 'calledAt ASC',
    );
    return maps.map(CallRecord.fromMap).toList();
  }
}

// Alias for cleaner imports in call history screen
class CallDbService extends AppDbService {
  CallDbService() : super._();
}
