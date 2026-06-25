import 'dart:async';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import '../services/call_db_service.dart';
import '../services/firebase_sync_service.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  // Stats counters
  int _dTotal = 0;
  int _dCalled = 0;
  int _dBusy = 0;
  int _dSales = 0;
  int _dConnected = 0;
  int _dPending = 0;

  // Expected Sales & Admin Target
  int _expectedSales = 0;
  String _adminTarget = 'Not Set';

  // Thought of the Day
  String _thoughtText = 'Believe you can and you\'re halfway there.';
  String _thoughtAuthor = 'Theodore Roosevelt';

  // Announcements
  List<Map<String, dynamic>> _announcements = [];


  Timer? _refreshTimer;

  // Firebase listeners
  StreamSubscription? _expectedSalesSub;
  StreamSubscription? _adminTargetSub;
  StreamSubscription? _totdSub;
  StreamSubscription? _announcementsSub;

  @override
  void initState() {
    super.initState();
    _loadStats();
    _setupFirebaseListeners();
    // Auto-refresh stats every 5 seconds to catch updates from call activities
    _refreshTimer = Timer.periodic(const Duration(seconds: 5), (_) => _loadStats());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _expectedSalesSub?.cancel();
    _adminTargetSub?.cancel();
    _totdSub?.cancel();
    _announcementsSub?.cancel();
    super.dispose();
  }

  String get _displayName {
    final email = FirebaseAuth.instance.currentUser?.email ?? 'user@gmail.com';
    final namePart = email.split('@').first;
    final cleanName = namePart.replaceAll(RegExp(r'[^a-zA-Z]'), '').toLowerCase();
    if (cleanName.isNotEmpty) {
      return cleanName[0].toUpperCase() + cleanName.substring(1);
    }
    return 'User';
  }

  String get _initials {
    final name = _displayName;
    return name.isNotEmpty ? name[0].toUpperCase() : 'U';
  }

  String get _greeting {
    final hour = DateTime.now().hour;
    if (hour >= 0 && hour < 12) {
      return 'Good Morning,';
    } else if (hour >= 12 && hour < 17) {
      return 'Good Afternoon,';
    } else if (hour >= 17 && hour < 21) {
      return 'Good Evening,';
    } else {
      return 'Good Night,';
    }
  }

  void _setupFirebaseListeners() {
    final uid = FirebaseAuth.instance.currentUser?.uid;
    if (uid == null) return;

    final dateKey = DateFormat('dd-MM-yyyy').format(DateTime.now());

    // 1. Expected Sales listener
    _expectedSalesSub = FirebaseDatabase.instance
        .ref('expectedSales/$uid/$dateKey')
        .onValue
        .listen((event) {
      if (mounted) {
        final val = event.snapshot.value;
        setState(() {
          _expectedSales = (val as num?)?.toInt() ?? 0;
        });
      }
    });

    // 2. Admin Target listener
    _adminTargetSub = FirebaseDatabase.instance
        .ref('adminTargets/$uid/target')
        .onValue
        .listen((event) {
      if (mounted) {
        final val = event.snapshot.value;
        setState(() {
          _adminTarget = val != null ? val.toString() : 'Not Set';
        });
      }
    });

    // 3. Thought of the Day listener
    _totdSub = FirebaseFirestore.instance
        .collection('settings')
        .doc('thoughtOfTheDay')
        .snapshots()
        .listen((snap) {
      if (mounted && snap.exists) {
        final data = snap.data();
        setState(() {
          _thoughtText = data?['text']?.toString() ?? "Believe you can and you're halfway there.";
          _thoughtAuthor = data?['author']?.toString() ?? "Theodore Roosevelt";
        });
      }
    });

    // 4. Announcements listener
    _announcementsSub = FirebaseFirestore.instance
        .collection('announcements')
        .orderBy('createdAt', descending: true)
        .snapshots()
        .listen((snap) {
      if (mounted) {
        setState(() {
          _announcements = snap.docs.map((doc) => {
            'id': doc.id,
            'text': doc.data()['text']?.toString() ?? '',
            'createdAt': doc.data()['createdAt'] as int? ?? 0,
          }).toList();
        });
      }
    });
  }

  Future<void> _loadStats() async {
    final db = AppDbService();
    final total = await db.getTotalLeadsCount();
    final called = await db.getCalledLeadsCount();
    final busy = await db.getBusyLeadsCount();
    final sales = await db.getSalesLeadsCount();
    final connected = await db.getConnectedLeadsCount();
    final pending = await db.getPendingLeadsCount();

    if (mounted) {
      setState(() {
        _dTotal = total;
        _dCalled = called;
        _dBusy = busy;
        _dSales = sales;
        _dConnected = connected;
        _dPending = pending;
      });
    }
  }

  Future<void> _handleRefresh() async {
    await FirebaseSyncService.syncFromFirebase();
    await _loadStats();
  }

  void _showEditExpectedSalesDialog() {
    final controller = TextEditingController(
      text: _expectedSales > 0 ? _expectedSales.toString() : '',
    );
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Expected Sales'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Enter your expected sales for today:'),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              keyboardType: TextInputType.number,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Sales count',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFFF6A00),
            ),
            onPressed: () async {
              final newVal = int.tryParse(controller.text) ?? 0;
              final uid = FirebaseAuth.instance.currentUser?.uid;
              if (uid != null) {
                final dateKey = DateFormat('dd-MM-yyyy').format(DateTime.now());
                await FirebaseDatabase.instance
                    .ref('expectedSales/$uid/$dateKey')
                    .set(newVal);
              }
              if (ctx.mounted) Navigator.pop(ctx);
            },
            child: const Text('Save', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  void _showAnnouncementsDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            const Text('📢 Announcements'),
            const Spacer(),
            IconButton(
              icon: const Icon(Icons.close),
              onPressed: () => Navigator.pop(ctx),
            )
          ],
        ),
        content: SizedBox(
          width: double.maxFinite,
          height: 350,
          child: _announcements.isEmpty
              ? const Center(
                  child: Text('No announcements yet.', style: TextStyle(color: Colors.grey)),
                )
              : ListView.separated(
                  itemCount: _announcements.length,
                  separatorBuilder: (_, __) => const Divider(),
                  itemBuilder: (ctx, i) {
                    final item = _announcements[i];
                    String dateStr = '';
                    if (item['createdAt'] > 0) {
                      dateStr = DateFormat('dd MMM, hh:mm a')
                          .format(DateTime.fromMillisecondsSinceEpoch(item['createdAt']));
                    }
                    return ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(item['text'] ?? '', style: const TextStyle(fontSize: 14)),
                      subtitle: dateStr.isNotEmpty
                          ? Padding(
                              padding: const EdgeInsets.only(top: 4),
                              child: Text(dateStr, style: const TextStyle(fontSize: 11, color: Colors.grey)),
                            )
                          : null,
                    );
                  },
                ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final progressPercent = _dTotal > 0 ? (_dCalled * 100 ~/ _dTotal) : 0;
    final progressVal = _dTotal > 0 ? (_dCalled / _dTotal) : 0.0;

    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFFF8FAFC), Color(0xFFEDF2F7)],
        ),
      ),
      child: RefreshIndicator(
        color: const Color(0xFFFF6A00),
        onRefresh: _handleRefresh,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // ── 1. HEADER SECTION (Greeting, Name, Avatar & Bell) ──
            Row(
              children: [
                // Profile Avatar with Gradient
                GestureDetector(
                  onTap: () {
                    // Navigate to Profile or stub action
                  },
                  child: Container(
                    width: 46,
                    height: 46,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border: Border.all(color: const Color(0xFFFF6A00).withOpacity(0.55), width: 2),
                      gradient: const LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [Color(0xFFFF6A00), Color(0xFFFF8C00)],
                      ),
                    ),
                    child: Center(
                      child: Text(
                        _initials,
                        style: GoogleFonts.poppins(
                          color: Colors.white,
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                // Greeting text
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _greeting,
                        style: GoogleFonts.poppins(
                          color: const Color(0xFF64748B),
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      Text(
                        _displayName,
                        style: GoogleFonts.poppins(
                          color: const Color(0xFF0F172A),
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                // Notification Bell
                GestureDetector(
                  onTap: _showAnnouncementsDialog,
                  child: Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.05),
                          blurRadius: 4,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                    child: Stack(
                      alignment: Alignment.center,
                      children: [
                        const Icon(
                          Icons.notifications,
                          color: Color(0xFF475569),
                          size: 20,
                        ),
                        if (_announcements.isNotEmpty)
                          Positioned(
                            right: 10,
                            top: 10,
                            child: Container(
                              width: 8,
                              height: 8,
                              decoration: const BoxDecoration(
                                color: Color(0xFFFF6A00),
                                shape: BoxShape.circle,
                              ),
                            ),
                          )
                      ],
                    ),
                  ),
                )
              ],
            ),
            const SizedBox(height: 16),

            // ── 2. MINI STATS ROW (Total, Called, Busy, Sales) ──
            Row(
              children: [
                _buildMiniStat('Total', _dTotal.toString(), const Color(0xFF5856D6)),
                const SizedBox(width: 8),
                _buildMiniStat('Called', _dCalled.toString(), const Color(0xFF007AFF)),
                const SizedBox(width: 8),
                _buildMiniStat('Busy', _dBusy.toString(), const Color(0xFFFF9500)),
                const SizedBox(width: 8),
                _buildMiniStat('Sales', _dSales.toString(), const Color(0xFF22C55E)),
              ],
            ),
            const SizedBox(height: 16),

            // ── 3. STUDENT COMMUNITY BANNER (Clean Styled Card) ──
            Container(
              width: double.infinity,
              height: 130,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(20),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFFFF8C5A), Color(0xFFFF5722)],
                ),
                boxShadow: [
                  BoxShadow(
                    color: const Color(0xFFFF5722).withOpacity(0.3),
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Stack(
                children: [
                  Positioned(
                    right: -20,
                    bottom: -20,
                    child: Opacity(
                      opacity: 0.15,
                      child: Icon(Icons.school, size: 150, color: Colors.white),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          'ADYAPAN CRM',
                          style: GoogleFonts.poppins(
                            color: Colors.white.withOpacity(0.9),
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                            letterSpacing: 1.5,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          "India's Largest\nStudent Community",
                          style: GoogleFonts.poppins(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                            fontSize: 20,
                            height: 1.2,
                          ),
                        ),
                      ],
                    ),
                  )
                ],
              ),
            ),
            const SizedBox(height: 16),

            // ── 4. TODAY'S PROGRESS CARD (Clean Widget) ──
            Container(
              padding: const EdgeInsets.all(18),
              decoration: _cardDecoration(),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              "Today's Progress",
                              style: GoogleFonts.poppins(
                                color: const Color(0xFF64748B),
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              "$_dCalled of $_dTotal leads called",
                              style: GoogleFonts.poppins(
                                color: const Color(0xFF0F172A),
                                fontSize: 16,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFFF4EE),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          "$progressPercent%",
                          style: GoogleFonts.poppins(
                            color: const Color(0xFFFF6A00),
                            fontSize: 20,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      )
                    ],
                  ),
                  const SizedBox(height: 14),
                  // Progress Bar
                  ClipRRect(
                    borderRadius: BorderRadius.circular(5),
                    child: Container(
                      height: 10,
                      color: const Color(0xFFF1F5F9),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: FractionallySizedBox(
                          widthFactor: progressVal,
                          child: Container(
                            decoration: const BoxDecoration(
                              gradient: LinearGradient(
                                colors: [Color(0xFFFF6A00), Color(0xFFFFAA55)],
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  // Expected Sales + Admin Target
                  Row(
                    children: [
                      // Expected Sales
                      Expanded(
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: const Color(0xFFECFDF5),
                            borderRadius: BorderRadius.circular(14),
                            border: Border.all(color: const Color(0xFFD1FAE5)),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Expected Sales',
                                style: GoogleFonts.poppins(
                                  color: const Color(0xFF047857),
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      _expectedSales.toString(),
                                      style: GoogleFonts.poppins(
                                        color: const Color(0xFF065F46),
                                        fontSize: 20,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                                  GestureDetector(
                                    onTap: _showEditExpectedSalesDialog,
                                    child: Container(
                                      width: 28,
                                      height: 28,
                                      decoration: BoxDecoration(
                                        color: const Color(0xFFA7F3D0),
                                        borderRadius: BorderRadius.circular(8),
                                      ),
                                      child: const Icon(
                                        Icons.edit,
                                        color: Color(0xFF065F46),
                                        size: 14,
                                      ),
                                    ),
                                  )
                                ],
                              )
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      // Admin Target
                      Expanded(
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: const Color(0xFFEFF6FF),
                            borderRadius: BorderRadius.circular(14),
                            border: Border.all(color: const Color(0xFFDBEAFE)),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Admin Target',
                                style: GoogleFonts.poppins(
                                  color: const Color(0xFF1D4ED8),
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                _adminTarget,
                                style: GoogleFonts.poppins(
                                  color: const Color(0xFF1E40AF),
                                  fontSize: 20,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                        ),
                      )
                    ],
                  )
                ],
              ),
            ),
            const SizedBox(height: 20),

            // ── 5. OVERVIEW SECTION HEADER ──
            Row(
              children: [
                Text(
                  'Overview',
                  style: GoogleFonts.poppins(
                    color: const Color(0xFF0F172A),
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFF6A00).withOpacity(0.10),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 6,
                        height: 6,
                        decoration: const BoxDecoration(
                          color: Color(0xFFFF6A00),
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        'LIVE STATS',
                        style: GoogleFonts.poppins(
                          color: const Color(0xFFFF6A00),
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                )
              ],
            ),
            const SizedBox(height: 12),

            // ── 6. OVERVIEW GRID (Total Leads, Connected, Busy, Pending) ──
            Row(
              children: [
                _buildOverviewCard('Total Leads', _dTotal.toString(), Icons.group, Colors.blue),
                const SizedBox(width: 12),
                _buildOverviewCard('Connected', _dConnected.toString(), Icons.phone, Colors.green),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                _buildOverviewCard('Busy Leads', _dBusy.toString(), Icons.phone_paused, Colors.orange),
                const SizedBox(width: 12),
                _buildOverviewCard('Pending Leads', _dPending.toString(), Icons.schedule, Colors.red),
              ],
            ),
            const SizedBox(height: 12),

            // ── 7. SALES DONE FULL WIDTH CARD ──
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFD1FAE5)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.03),
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: const Color(0xFFD1FAE5),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.star,
                      color: Color(0xFF059669),
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Sales Done (Total Converted)',
                          style: GoogleFonts.poppins(
                            color: const Color(0xFF475569),
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          _dSales.toString(),
                          style: GoogleFonts.poppins(
                            color: const Color(0xFF065F46),
                            fontSize: 22,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        'Converted',
                        style: GoogleFonts.poppins(
                          color: const Color(0xFF059669),
                          fontSize: 10,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Active',
                        style: GoogleFonts.poppins(
                          color: const Color(0xFF059669),
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  )
                ],
              ),
            ),
            const SizedBox(height: 22),

            // ── 8. THOUGHT OF THE DAY ──
            Row(
              children: [
                Text(
                  'Thought of the Day',
                  style: GoogleFonts.poppins(
                    color: const Color(0xFF0F172A),
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const Spacer(),
                const Text('💡', style: TextStyle(fontSize: 18)),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 20),
              decoration: BoxDecoration(
                color: const Color(0xFFFFF7ED),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFFFF7ED)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.02),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Stack(
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        '“',
                        style: TextStyle(
                          color: Color(0x33FF6A00),
                          fontSize: 48,
                          fontWeight: FontWeight.bold,
                          height: 0.5,
                        ),
                      ),
                      Text(
                        _thoughtText,
                        style: GoogleFonts.poppins(
                          color: const Color(0xFF334155),
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          height: 1.5,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Container(
                            width: 6,
                            height: 6,
                            decoration: const BoxDecoration(
                              color: Color(0xFFFF6A00),
                              shape: BoxShape.circle,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            _thoughtAuthor,
                            style: GoogleFonts.poppins(
                              color: const Color(0xFFFF6A00),
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                            ),
                          )
                        ],
                      )
                    ],
                  )
                ],
              ),
            ),
            const SizedBox(height: 30),
          ],
        ),
      ),
    );
  }

  Widget _buildMiniStat(String title, String count, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.04),
              blurRadius: 6,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          children: [
            Text(
              title,
              style: GoogleFonts.poppins(
                color: const Color(0xFF64748B),
                fontSize: 10,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              count,
              style: GoogleFonts.poppins(
                color: color,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildOverviewCard(String title, String value, IconData icon, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: _cardDecoration(),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    title.toUpperCase(),
                    style: GoogleFonts.poppins(
                      color: const Color(0xFF7F8C8D),
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: color.withOpacity(0.12),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(icon, color: color, size: 18),
                )
              ],
            ),
            const SizedBox(height: 8),
            Text(
              value,
              style: GoogleFonts.poppins(
                color: const Color(0xFF2C3E50),
                fontSize: 26,
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
      ),
    );
  }

  BoxDecoration _cardDecoration() {
    return BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      boxShadow: [
        BoxShadow(
          color: Colors.black.withOpacity(0.04),
          blurRadius: 10,
          offset: const Offset(0, 4),
        ),
      ],
    );
  }
}
