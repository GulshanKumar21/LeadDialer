import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/call_db_service.dart';
import '../models/call_record.dart';
import 'package:intl/intl.dart';

// Simple Dashboard screen showing today's stats
class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  int _totalCalls = 0;
  int _totalDuration = 0;
  int _todayCalls = 0;

  @override
  void initState() {
    super.initState();
    _loadStats();
  }

  Future<void> _loadStats() async {
    final records = await CallDbService().getAllRecords();
    final now = DateTime.now();
    final todayStart = DateTime(now.year, now.month, now.day);
    final todayRecs = records.where((r) =>
        DateTime.fromMillisecondsSinceEpoch(r.calledAt).isAfter(todayStart)).toList();

    if (mounted) {
      setState(() {
        _totalCalls = records.length;
        _totalDuration = records.fold(0, (s, r) => s + r.duration);
        _todayCalls = todayRecs.length;
      });
    }
  }

  String _fmt(int sec) {
    if (sec < 60) return '${sec}s';
    if (sec < 3600) return '${sec ~/ 60}m ${sec % 60}s';
    return '${sec ~/ 3600}h ${(sec % 3600) ~/ 60}m';
  }

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      color: const Color(0xFFFF7043),
      onRefresh: _loadStats,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Header
          Text('Dashboard', style: GoogleFonts.poppins(
            fontSize: 24, fontWeight: FontWeight.bold, color: const Color(0xFF2A2A35),
          )),
          Text(
            DateFormat('EEEE, dd MMMM').format(DateTime.now()),
            style: const TextStyle(color: Color(0xFF9E9E9E), fontSize: 13),
          ),
          const SizedBox(height: 20),

          // Stats row
          Row(children: [
            _StatCard('📞 Today\'s Calls', '$_todayCalls', const Color(0xFFFF7043)),
            const SizedBox(width: 12),
            _StatCard('📊 Total Calls', '$_totalCalls', const Color(0xFF1565C0)),
          ]),
          const SizedBox(height: 12),
          Row(children: [
            _StatCard('⏱ Total Talk Time', _fmt(_totalDuration), const Color(0xFF2E7D32)),
            const SizedBox(width: 12),
            _StatCard('⌀ Avg/Call',
                _totalCalls > 0 ? _fmt(_totalDuration ~/ _totalCalls) : '0s',
                const Color(0xFF7B1FA2)),
          ]),

          const SizedBox(height: 24),
          // Quick tips card
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFFFF8C5A), Color(0xFFFF5722)],
              ),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('🎯 Today\'s Target',
                    style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                const SizedBox(height: 8),
                Text('Work window: 11:30 AM – 8:00 PM',
                    style: TextStyle(color: Colors.white.withValues(alpha: 0.85), fontSize: 13)),
                Text('Calls made today: $_todayCalls',
                    style: TextStyle(color: Colors.white.withValues(alpha: 0.85), fontSize: 13)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  final String label;
  final String value;
  final Color color;

  const _StatCard(this.label, this.value, this.color);

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: color.withValues(alpha: 0.2),
              blurRadius: 8,
              offset: const Offset(0, 3),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label,
                style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            Text(value,
                style: TextStyle(
                    color: color, fontSize: 26, fontWeight: FontWeight.bold)),
          ],
        ),
      ),
    );
  }
}
