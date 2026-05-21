import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'login_screen.dart';

// ─────────────────────────────────────────────────────────────────────────────
//  OnboardingScreen — Flutter port of OnboardingActivity.kt
//
//  5 pages with:
//  • Full-screen gradient background per slide
//  • Emoji illustration + title + description
//  • Colored accent bar per slide
//  • Dot indicators
//  • Skip / Prev / Next buttons
//  • Cinematic page transition (scale + fade)
//  • SharedPreferences "firstTime" check — shown only once
// ─────────────────────────────────────────────────────────────────────────────

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final _controller = PageController();
  int _currentPage  = 0;

  // ── 5 onboarding pages — same as Native Android ───────────────────────────
  final List<_OnboardPage> _pages = const [
    _OnboardPage(
      emoji:       '📋',
      title:       'Smart Lead Management',
      description: 'Organize all your student leads in one place. Assign, track, and never miss a follow-up — all from your phone.',
      accentColor: Color(0xFFFF6A00),
      gradientColors: [Color(0xFFFF6A00), Color(0xFFEE0979)],
    ),
    _OnboardPage(
      emoji:       '📞',
      title:       'One-Tap Power Dialing',
      description: 'Call any lead with a single tap. Get automatic call logging, status updates, and WhatsApp follow-ups instantly.',
      accentColor: Color(0xFF0EA5E9),
      gradientColors: [Color(0xFF0EA5E9), Color(0xFF0284C7)],
    ),
    _OnboardPage(
      emoji:       '🤳',
      title:       'Selfie Attendance System',
      description: 'Mark attendance with a selfie — GPS verified, device locked. On time or late, every record is accurate.',
      accentColor: Color(0xFF8B5CF6),
      gradientColors: [Color(0xFF8B5CF6), Color(0xFF6D28D9)],
    ),
    _OnboardPage(
      emoji:       '📊',
      title:       'Live Reports & Insights',
      description: 'Watch your team\'s performance in real time. Track calls, leads, conversions, and monthly goals with beautiful charts.',
      accentColor: Color(0xFF10B981),
      gradientColors: [Color(0xFF10B981), Color(0xFF059669)],
    ),
    _OnboardPage(
      emoji:       '🎉',
      title:       'Welcome to Adyapan CRM!',
      description: 'India\'s smart CRM for education teams. Built to help you reach more students, close more admissions, every day.',
      accentColor: Color(0xFFF59E0B),
      gradientColors: [Color(0xFFF59E0B), Color(0xFFD97706)],
    ),
  ];

  @override
  void initState() {
    super.initState();
    // Full-screen immersive
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
  }

  @override
  void dispose() {
    _controller.dispose();
    // Restore system UI
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  void _goToLogin() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('firstTime', false);
    if (mounted) {
      Navigator.of(context).pushReplacement(
        PageRouteBuilder(
          pageBuilder: (_, __, ___) => const LoginScreen(),
          transitionsBuilder: (_, anim, __, child) =>
              FadeTransition(opacity: anim, child: child),
          transitionDuration: const Duration(milliseconds: 400),
        ),
      );
    }
  }

  void _next() {
    if (_currentPage < _pages.length - 1) {
      _controller.nextPage(
        duration: const Duration(milliseconds: 400),
        curve: Curves.easeInOutCubic,
      );
    } else {
      _goToLogin();
    }
  }

  void _prev() {
    if (_currentPage > 0) {
      _controller.previousPage(
        duration: const Duration(milliseconds: 400),
        curve: Curves.easeInOutCubic,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final page = _pages[_currentPage];
    final isLast = _currentPage == _pages.length - 1;

    return Scaffold(
      body: Stack(
        children: [
          // ── Animated background gradient ────────────────────────────────
          AnimatedContainer(
            duration: const Duration(milliseconds: 500),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: page.gradientColors,
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
            ),
          ),

          // ── Dark overlay for text readability ───────────────────────────
          Container(color: Colors.black.withOpacity(0.25)),

          // ── Page content ────────────────────────────────────────────────
          PageView.builder(
            controller: _controller,
            itemCount: _pages.length,
            onPageChanged: (i) => setState(() => _currentPage = i),
            itemBuilder: (_, i) => _buildPage(_pages[i]),
          ),

          // ── TOP: Skip button ─────────────────────────────────────────────
          Positioned(
            top: 52,
            right: 24,
            child: GestureDetector(
              onTap: _goToLogin,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: Colors.white30),
                ),
                child: const Text(
                  'Skip',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ),

          // ── BOTTOM: Dots + Buttons ───────────────────────────────────────
          Positioned(
            left: 0, right: 0, bottom: 48,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 28),
              child: Row(
                children: [
                  // Prev button
                  AnimatedOpacity(
                    opacity: _currentPage == 0 ? 0.0 : 1.0,
                    duration: const Duration(milliseconds: 250),
                    child: GestureDetector(
                      onTap: _prev,
                      child: Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white38),
                        ),
                        child: const Icon(Icons.arrow_back_rounded,
                            color: Colors.white, size: 22),
                      ),
                    ),
                  ),

                  // Dot indicators
                  Expanded(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: List.generate(_pages.length, (i) {
                        final isActive = i == _currentPage;
                        return AnimatedContainer(
                          duration: const Duration(milliseconds: 300),
                          margin: const EdgeInsets.symmetric(horizontal: 4),
                          width:  isActive ? 24 : 8,
                          height: 8,
                          decoration: BoxDecoration(
                            color: isActive
                                ? Colors.white
                                : Colors.white.withOpacity(0.4),
                            borderRadius: BorderRadius.circular(4),
                          ),
                        );
                      }),
                    ),
                  ),

                  // Next / Get Started button
                  GestureDetector(
                    onTap: _next,
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 300),
                      height: 48,
                      padding: EdgeInsets.symmetric(
                        horizontal: isLast ? 20 : 0,
                      ),
                      width: isLast ? null : 48,
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(isLast ? 24 : 100),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.2),
                            blurRadius: 12,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: isLast
                          ? Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(
                                  'Get Started',
                                  style: TextStyle(
                                    color: page.accentColor,
                                    fontWeight: FontWeight.bold,
                                    fontSize: 14,
                                  ),
                                ),
                                const SizedBox(width: 6),
                                Icon(Icons.rocket_launch_rounded,
                                    color: page.accentColor, size: 18),
                              ],
                            )
                          : Icon(Icons.arrow_forward_rounded,
                              color: page.accentColor, size: 22),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ── Single page ────────────────────────────────────────────────────────────
  Widget _buildPage(_OnboardPage p) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(32, 0, 32, 180),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Emoji illustration
          Container(
            width: 90,
            height: 90,
            margin: const EdgeInsets.only(bottom: 28),
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.15),
              borderRadius: BorderRadius.circular(28),
              border: Border.all(color: Colors.white24, width: 1.5),
            ),
            child: Center(
              child: Text(p.emoji,
                  style: const TextStyle(fontSize: 44)),
            ),
          ),

          // Accent bar
          Container(
            width: 48,
            height: 5,
            margin: const EdgeInsets.only(bottom: 18),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(3),
            ),
          ),

          // Title
          Text(
            p.title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 30,
              fontWeight: FontWeight.bold,
              letterSpacing: 0.2,
              height: 1.15,
              shadows: [
                Shadow(color: Colors.black38, offset: Offset(0, 2), blurRadius: 6),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // Description
          Text(
            p.description,
            style: TextStyle(
              color: Colors.white.withOpacity(0.88),
              fontSize: 15,
              height: 1.55,
              shadows: const [
                Shadow(color: Colors.black26, offset: Offset(0, 1), blurRadius: 4),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ── Data class ────────────────────────────────────────────────────────────────
class _OnboardPage {
  final String emoji;
  final String title;
  final String description;
  final Color accentColor;
  final List<Color> gradientColors;

  const _OnboardPage({
    required this.emoji,
    required this.title,
    required this.description,
    required this.accentColor,
    required this.gradientColors,
  });
}
