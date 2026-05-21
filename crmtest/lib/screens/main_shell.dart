import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../widgets/island_nav_bar.dart';
import 'dashboard_screen.dart';
import 'leads_screen.dart';
import 'call_history_screen.dart';
import 'dialer_screen.dart';
import 'login_screen.dart'; // for AuthService logout

// ────────────────────────────────────────────────────────────────────────────
//  MainShell — Flutter equivalent of activity_main.xml + MainActivity.kt
//
//  Features:
//  • Island Nav Bar (floating pill)
//  • Global Manual Dial FAB (green, pulsing, always visible)
//  • 5 screens: Dashboard, Leads, Call History, Calendar, Attendance
// ────────────────────────────────────────────────────────────────────────────

class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell>
    with SingleTickerProviderStateMixin {
  int _currentIndex = 0;
  late AnimationController _dialGlowCtrl;
  late Animation<double> _dialGlowScale;
  late Animation<double> _dialGlowAlpha;

  final List<Widget> _screens = const [
    DashboardScreen(),
    LeadsScreen(),
    CallHistoryScreen(),
    _PlaceholderScreen('📅 Calendar', 'Calendar coming soon'),
    _PlaceholderScreen('🕒 Attendance', 'Attendance coming soon'),
  ];

  final List<String> _titles = [
    'Dashboard', 'Leads', 'Call History', 'Calendar', 'Attendance',
  ];

  @override
  void initState() {
    super.initState();
    SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
    ));

    _dialGlowCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    )..repeat(reverse: true);

    _dialGlowScale = Tween<double>(begin: 1.0, end: 1.5).animate(
      CurvedAnimation(parent: _dialGlowCtrl, curve: Curves.easeInOut),
    );
    _dialGlowAlpha = Tween<double>(begin: 0.5, end: 0.0).animate(
      CurvedAnimation(parent: _dialGlowCtrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _dialGlowCtrl.dispose();
    super.dispose();
  }

  void _openDialer() {
    Navigator.of(context).push(
      PageRouteBuilder(
        pageBuilder: (_, __, ___) => const DialerScreen(),
        transitionsBuilder: (_, anim, __, child) {
          return SlideTransition(
            position: Tween<Offset>(
              begin: const Offset(0, 1),
              end: Offset.zero,
            ).animate(CurvedAnimation(parent: anim, curve: Curves.easeOutCubic)),
            child: child,
          );
        },
        transitionDuration: const Duration(milliseconds: 400),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFFFF3E8),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: Text(
          _titles[_currentIndex],
          style: const TextStyle(
            color: Color(0xFF2A2A35),
            fontWeight: FontWeight.bold,
            fontSize: 20,
          ),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: GestureDetector(
              onTap: () {
                showDialog(
                  context: context,
                  builder: (_) => AlertDialog(
                    title: const Text('Logout'),
                    content: const Text('Are you sure you want to logout?'),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context),
                        child: const Text('Cancel'),
                      ),
                      TextButton(
                        onPressed: () {
                          Navigator.pop(context);
                          AuthService.logout(context, onDone: () {
                            Navigator.of(context).pushAndRemoveUntil(
                              MaterialPageRoute(
                                  builder: (_) => const LoginScreen()),
                              (_) => false,
                            );
                          });
                        },
                        child: const Text('Logout',
                            style: TextStyle(color: Colors.red)),
                      ),
                    ],
                  ),
                );
              },
              child: Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(20),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.1),
                      blurRadius: 6,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: const Icon(Icons.person_rounded,
                    color: Color(0xFF7A8B99)),
              ),
            ),
          ),
        ],
      ),

      body: Stack(
        children: [
          // ── Main screen content ──────────────────────────────────────
          Positioned.fill(
            child: _screens[_currentIndex],
          ),

          // ── Island Nav Bar at bottom ─────────────────────────────────
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: IslandNavBar(
              selectedIndex: _currentIndex,
              onTabChanged: (i) => setState(() => _currentIndex = i),
            ),
          ),

          // ── 📞 Global Manual Dial FAB — always visible ───────────────
          Positioned(
            right: 24,
            bottom: 90,
            child: GestureDetector(
              onTap: _openDialer,
              child: SizedBox(
                width: 64,
                height: 64,
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    // Pulsing glow ring
                    AnimatedBuilder(
                      animation: _dialGlowCtrl,
                      builder: (_, __) => Transform.scale(
                        scale: _dialGlowScale.value,
                        child: Container(
                          width: 64,
                          height: 64,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: const Color(0xFF22C55E)
                                .withValues(alpha: _dialGlowAlpha.value),
                          ),
                        ),
                      ),
                    ),
                    // Green FAB
                    Container(
                      width: 56,
                      height: 56,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: const Color(0xFF22C55E),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF22C55E)
                                .withValues(alpha: 0.5),
                            blurRadius: 14,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: const Icon(
                        Icons.dialpad_rounded,
                        color: Colors.white,
                        size: 28,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Placeholder screen ───────────────────────────────────────────────────────
class _PlaceholderScreen extends StatelessWidget {
  final String emoji;
  final String text;

  const _PlaceholderScreen(this.emoji, this.text);

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(emoji, style: const TextStyle(fontSize: 64)),
          const SizedBox(height: 12),
          Text(
            text,
            style: const TextStyle(
              fontSize: 18,
              color: Color(0xFFC47A50),
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}
