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
    final path = join(await getDatabasesPath(), 'lead_dialer.db');
    return openDatabase(
      path,
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE leads (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            phone TEXT NOT NULL,
            status TEXT DEFAULT 'Pending',
            isHotLead INTEGER DEFAULT 0,
            salesDone INTEGER DEFAULT 0,
            collegeName TEXT,
            collegeCity TEXT,
            notes TEXT,
            calledBy TEXT,
            calledAt INTEGER,
            firestoreId TEXT
          )
        ''');
        await db.execute('''
          CREATE TABLE call_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            phone TEXT NOT NULL,
            status TEXT DEFAULT 'Pending',
            duration INTEGER DEFAULT 0,
            calledAt INTEGER,
            calledBy TEXT
          )
        ''');
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
class CallDbService extends AppDbService {}
