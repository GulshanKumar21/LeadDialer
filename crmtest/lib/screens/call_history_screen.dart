import 'dart:async';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/call_record.dart';
import '../services/call_db_service.dart';

// ────────────────────────────────────────────────────────────────────────────
//  CallHistoryScreen — Flutter port of CallHistoryFragment + daily summary
//
//  Features:
//  • Daily summary card: total calls, talk time, gap time, avg/call
//  • Countdown to 8 PM end-of-day
//  • Gap separator rows between consecutive calls (shows idle time)
//  • Grouped by Today / Yesterday / date
// ────────────────────────────────────────────────────────────────────────────

class CallHistoryScreen extends StatefulWidget {
  const CallHistoryScreen({super.key});

  @override
  State<CallHistoryScreen> createState() => _CallHistoryScreenState();
}

class _CallHistoryScreenState extends State<CallHistoryScreen> {
  List<CallRecord> _records = [];
  Timer? _countdownTimer;
  String _countdownText = '';
  bool _loading = true;

  // Work day: 11:30 AM → 8:00 PM
  static const _workStartHour = 11;
  static const _workStartMinute = 30;
  static const _workEndHour = 20;

  @override
  void initState() {
    super.initState();
    _loadRecords();
    _startCountdown();
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadRecords() async {
    final records = await CallDbService().getAllRecords();
    if (mounted) setState(() { _records = records; _loading = false; });
  }

  void _startCountdown() {
    _updateCountdown();
    _countdownTimer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (mounted) _updateCountdown();
    });
  }

  void _updateCountdown() {
    final now = DateTime.now();
    final eod = DateTime(now.year, now.month, now.day, _workEndHour, 0, 0);
    final diff = eod.difference(now);
    setState(() {
      if (diff.isNegative) {
        _countdownText = '✅ Work day ended at 8:00 PM';
      } else {
        final h = diff.inHours;
        final m = diff.inMinutes % 60;
        _countdownText = h > 0 ? '🕗 ${h}h ${m}m left to 8PM' : '🕗 ${m}m left to 8PM';
      }
    });
  }

  // ── Today's records within work window ──────────────────────────────────
  List<CallRecord> get _todayWindowRecords {
    final now = DateTime.now();
    final dayStart = DateTime(now.year, now.month, now.day);
    final windowStart = DateTime(now.year, now.month, now.day, _workStartHour, _workStartMinute);
    final dayEnd = dayStart.add(const Duration(days: 1));
    return _records.where((r) {
      final d = DateTime.fromMillisecondsSinceEpoch(r.calledAt);
      return d.isAfter(dayStart) && d.isBefore(dayEnd) && d.isAfter(windowStart);
    }).toList();
  }

  // ── Summary calculations ─────────────────────────────────────────────────
  int get _totalCalls => _todayWindowRecords.length;
  int get _totalTalkSec => _todayWindowRecords.fold(0, (s, r) => s + r.duration);

  int get _totalGapSec {
    final asc = [..._todayWindowRecords]..sort((a, b) => a.calledAt.compareTo(b.calledAt));
    int gap = 0;
    for (int i = 1; i < asc.length; i++) {
      final prevEnd = asc[i - 1].calledAt + asc[i - 1].duration * 1000;
      final nextStart = asc[i].calledAt;
      final g = (nextStart - prevEnd) ~/ 1000;
      if (g > 0) gap += g;
    }
    return gap;
  }

  int get _avgPerCallSec => _totalCalls > 0 ? _totalTalkSec ~/ _totalCalls : 0;

  // ── Build grouped list with gap separators ───────────────────────────────
  List<_ListItem> _buildDisplayList() {
    final sorted = [..._records]..sort((a, b) => b.calledAt.compareTo(a.calledAt));
    final grouped = <String, List<CallRecord>>{};

    for (final r in sorted) {
      final header = _dayHeader(r.calledAt);
      grouped.putIfAbsent(header, () => []).add(r);
    }

    final list = <_ListItem>[];
    for (final entry in grouped.entries) {
      list.add(_HeaderItem(entry.key));
      final desc = entry.value; // already descending
      for (int i = 0; i < desc.length; i++) {
        list.add(_CallItem(desc[i]));
        if (i + 1 < desc.length) {
          final newerEnd  = desc[i].calledAt + desc[i].duration * 1000;
          final olderStart = desc[i + 1].calledAt;
          final gapMs = newerEnd - olderStart;
          if (gapMs > 0) {
            list.add(_GapItem(gapMs ~/ 1000));
          }
        }
      }
    }
    return list;
  }

  String _dayHeader(int ms) {
    final d = DateTime.fromMillisecondsSinceEpoch(ms);
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final yesterday = today.subtract(const Duration(days: 1));
    final dayOf = DateTime(d.year, d.month, d.day);
    if (dayOf == today) return '📞 Today';
    if (dayOf == yesterday) return '🕒 Yesterday';
    return DateFormat('dd MMM yyyy').format(d);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator(color: Color(0xFFFF7043)));
    }

    final items = _buildDisplayList();

    return RefreshIndicator(
      color: const Color(0xFFFF7043),
      onRefresh: _loadRecords,
      child: CustomScrollView(
        slivers: [
          // ── Summary Card ────────────────────────────────────────────────
          SliverToBoxAdapter(child: _buildSummaryCard()),

          // ── Empty state ─────────────────────────────────────────────────
          if (items.isEmpty)
            const SliverFillRemaining(
              child: Center(
                child: Text(
                  '📞 No calls made yet.\nGo to Leads to start calling!',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Color(0xFFC47A50), fontSize: 15),
                ),
              ),
            )
          else
            SliverList(
              delegate: SliverChildBuilderDelegate(
                (ctx, i) {
                  final item = items[i];
                  if (item is _HeaderItem) return _buildHeader(item);
                  if (item is _GapItem)   return _buildGap(item);
                  return _buildCallCard((item as _CallItem).record);
                },
                childCount: items.length,
              ),
            ),

          const SliverToBoxAdapter(child: SizedBox(height: 16)),
        ],
      ),
    );
  }

  // ── Summary card ─────────────────────────────────────────────────────────
  Widget _buildSummaryCard() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 6),
      child: Container(
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFFFF8C5A), Color(0xFFFF5722)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: const Color(0xFFFF5722).withValues(alpha: 0.3),
              blurRadius: 16,
              offset: const Offset(0, 6),
            ),
          ],
        ),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Title row
            Row(
              children: [
                const Expanded(
                  child: Text(
                    '📊 Today\'s Call Summary',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 15,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                Text(
                  '11:30 AM – 8:00 PM',
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.7),
                    fontSize: 10,
                  ),
                ),
              ],
            ),
            const Divider(color: Colors.white30, height: 20),

            // Stats row
            Row(
              children: [
                _statCell('$_totalCalls', '📞 Calls'),
                _vDivider(),
                _statCell(_formatDuration(_totalTalkSec), '🗣 Talk Time'),
                _vDivider(),
                _statCell(_formatDuration(_totalGapSec), '⏳ Gap Time'),
              ],
            ),

            const Divider(color: Colors.white30, height: 16),

            // Avg + countdown row
            Row(
              children: [
                Expanded(
                  child: Text(
                    '⌀ Avg/call: ${_formatDuration(_avgPerCallSec)}',
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.8),
                      fontSize: 12,
                    ),
                  ),
                ),
                Text(
                  _countdownText,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _statCell(String value, String label) {
    return Expanded(
      child: Column(
        children: [
          Text(
            value,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 22,
              fontWeight: FontWeight.bold,
            ),
          ),
          Text(
            label,
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.75),
              fontSize: 11,
            ),
          ),
        ],
      ),
    );
  }

  Widget _vDivider() => Container(
        width: 1,
        height: 44,
        margin: const EdgeInsets.symmetric(horizontal: 4),
        color: Colors.white30,
      );

  // ── Header row ───────────────────────────────────────────────────────────
  Widget _buildHeader(_HeaderItem item) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Text(
        item.title,
        style: const TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.bold,
          color: Color(0xFF993800),
        ),
      ),
    );
  }

  // ── Gap separator row ─────────────────────────────────────────────────────
  Widget _buildGap(_GapItem item) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
      child: Row(
        children: [
          Expanded(child: Divider(color: Colors.orange.shade200)),
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 10),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
            decoration: BoxDecoration(
              color: const Color(0xFFFFF0E4),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: const Color(0xFFFFCCB0)),
            ),
            child: Text(
              '⏳ Gap: ${_formatDuration(item.gapSeconds)}',
              style: const TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.bold,
                color: Color(0xFFC47A50),
              ),
            ),
          ),
          Expanded(child: Divider(color: Colors.orange.shade200)),
        ],
      ),
    );
  }

  // ── Call record card ──────────────────────────────────────────────────────
  Widget _buildCallCard(CallRecord r) {
    final timeStr = DateFormat('hh:mm a')
        .format(DateTime.fromMillisecondsSinceEpoch(r.calledAt));

    final statusColor = switch (r.status) {
      'Connected'      => const Color(0xFF2E7D32),
      'Interested'     => const Color(0xFF1565C0),
      'Busy'           => const Color(0xFFE65100),
      'Not Connected'  => const Color(0xFF757575),
      'Not Interested' => const Color(0xFFC62828),
      _                => const Color(0xFF9E9E9E),
    };

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Card(
        elevation: 4,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              // Avatar
              CircleAvatar(
                radius: 22,
                backgroundColor: const Color(0xFFFF7043),
                child: Text(
                  r.name.isNotEmpty ? r.name[0].toUpperCase() : '?',
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 18,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              // Name + phone + status
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(r.name,
                        style: const TextStyle(
                            fontWeight: FontWeight.bold, fontSize: 15)),
                    Text(r.phone,
                        style: const TextStyle(
                            color: Colors.grey, fontSize: 12)),
                    Text(r.status,
                        style: TextStyle(
                            color: statusColor,
                            fontWeight: FontWeight.bold,
                            fontSize: 12)),
                  ],
                ),
              ),
              // Duration + time
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text('⏱ ${_formatDuration(r.duration)}',
                      style: const TextStyle(
                          fontSize: 12, fontWeight: FontWeight.bold)),
                  Text(timeStr,
                      style: const TextStyle(
                          color: Colors.grey, fontSize: 10)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDuration(int sec) {
    if (sec <= 0) return '0s';
    if (sec < 60) return '${sec}s';
    if (sec < 3600) return '${sec ~/ 60}m ${sec % 60}s';
    return '${sec ~/ 3600}h ${(sec % 3600) ~/ 60}m';
  }
}

// ── List item types ──────────────────────────────────────────────────────────
abstract class _ListItem {}

class _HeaderItem extends _ListItem {
  final String title;
  _HeaderItem(this.title);
}

class _CallItem extends _ListItem {
  final CallRecord record;
  _CallItem(this.record);
}

class _GapItem extends _ListItem {
  final int gapSeconds;
  _GapItem(this.gapSeconds);
}
