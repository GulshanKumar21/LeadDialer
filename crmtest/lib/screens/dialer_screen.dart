import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_phone_direct_caller/flutter_phone_direct_caller.dart';
import '../services/call_db_service.dart';
import '../models/call_record.dart';
import 'package:firebase_auth/firebase_auth.dart';

// ────────────────────────────────────────────────────────────────────────────
//  DialerScreen — Flutter port of DialerActivity.kt
//  Clean light-theme dialer matching the native Android design
// ────────────────────────────────────────────────────────────────────────────

class DialerScreen extends StatefulWidget {
  const DialerScreen({super.key});

  @override
  State<DialerScreen> createState() => _DialerScreenState();
}

class _DialerScreenState extends State<DialerScreen>
    with TickerProviderStateMixin {
  String _number = '';
  bool get _hasNumber => _number.isNotEmpty;

  void _append(String v) {
    if (_number.length >= 15) return;
    HapticFeedback.lightImpact();
    setState(() => _number += v);
  }

  void _backspace() {
    HapticFeedback.lightImpact();
    if (_number.isNotEmpty) {
      setState(() => _number = _number.substring(0, _number.length - 1));
    }
  }

  void _clear() {
    HapticFeedback.mediumImpact();
    setState(() => _number = '');
  }

  Future<void> _makeCall() async {
    if (_number.isEmpty) return;
    HapticFeedback.heavyImpact();

    final calledBy = FirebaseAuth.instance.currentUser?.email ?? 'Unknown';
    final now = DateTime.now().millisecondsSinceEpoch;

    await FlutterPhoneDirectCaller.callNumber(_number);

    // Save a call record after dialing
    await CallDbService().insertRecord(CallRecord(
      name: 'Manual Dial',
      phone: _number,
      status: 'Pending',
      duration: 0,
      calledAt: now,
      calledBy: calledBy,
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFFF8FAFC), Color(0xFFE2E8F0)],
          ),
        ),
        child: SafeArea(
          child: Column(
            children: [
              // ── Header ───────────────────────────────────────────────────
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                child: Row(
                  children: [
                    _CircleIconBtn(
                      icon: Icons.arrow_back_rounded,
                      onTap: () => Navigator.of(context).pop(),
                    ),
                    const SizedBox(width: 12),
                    const Text(
                      'Dialer',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF0F172A),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 20),

              // ── Number Display ───────────────────────────────────────────
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: Container(
                  width: double.infinity,
                  height: 80,
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(24),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x14000000),
                        blurRadius: 12,
                        offset: Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      AnimatedSwitcher(
                        duration: const Duration(milliseconds: 120),
                        child: Text(
                          _number.isEmpty ? 'Enter number' : _number,
                          key: ValueKey(_number),
                          style: TextStyle(
                            color: _number.isEmpty
                                ? const Color(0xFF94A3B8)
                                : const Color(0xFF0F172A),
                            fontSize: _number.length > 11 ? 22 : 28,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 2,
                          ),
                        ),
                      ),
                      if (_hasNumber)
                        Positioned(
                          right: 14,
                          child: GestureDetector(
                            onTap: _backspace,
                            onLongPress: _clear,
                            child: Container(
                              width: 38,
                              height: 38,
                              decoration: BoxDecoration(
                                color: const Color(0xFFF1F5F9),
                                borderRadius: BorderRadius.circular(19),
                              ),
                              child: const Icon(
                                Icons.backspace_rounded,
                                color: Color(0xFF64748B),
                                size: 18,
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 24),

              // ── Keypad ───────────────────────────────────────────────────
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 28),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      for (final row in [
                        ['1', '2', '3'],
                        ['4', '5', '6'],
                        ['7', '8', '9'],
                        ['*', '0', '#'],
                      ]) ...[
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: row
                              .map((d) => _DialKey(
                                    digit: d,
                                    onTap: () => _append(d),
                                  ))
                              .toList(),
                        ),
                        const SizedBox(height: 12),
                      ],
                    ],
                  ),
                ),
              ),

              // ── Bottom action row ────────────────────────────────────────
              Padding(
                padding: const EdgeInsets.only(bottom: 36, left: 40, right: 40, top: 4),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    // Save lead button
                    AnimatedOpacity(
                      opacity: _hasNumber ? 1.0 : 0.0,
                      duration: const Duration(milliseconds: 200),
                      child: _ActionCircle(
                        size: 52,
                        bgColor: const Color(0xFFE2E8F0),
                        child: const Icon(
                          Icons.person_add_outlined,
                          color: Color(0xFF475569),
                          size: 22,
                        ),
                        onTap: () {/* save contact */},
                      ),
                    ),
                    const SizedBox(width: 28),
                    // Call button — always visible, orange gradient
                    _CallButton(onTap: _makeCall),
                    const SizedBox(width: 28),
                    // Placeholder to balance layout
                    const SizedBox(width: 52),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Small circular icon button (e.g. back) ───────────────────────────────────
class _CircleIconBtn extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _CircleIconBtn({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.08),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Icon(icon, color: const Color(0xFF475569), size: 20),
      ),
    );
  }
}

// ── Dial key button (circle, no letters) ─────────────────────────────────────
class _DialKey extends StatefulWidget {
  final String digit;
  final VoidCallback onTap;
  const _DialKey({required this.digit, required this.onTap});

  @override
  State<_DialKey> createState() => _DialKeyState();
}

class _DialKeyState extends State<_DialKey> with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 80),
    );
    _scale = Tween(begin: 1.0, end: 0.88).animate(
      CurvedAnimation(parent: _ctrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => _ctrl.forward(),
      onTapUp: (_) {
        _ctrl.reverse();
        widget.onTap();
      },
      onTapCancel: () => _ctrl.reverse(),
      child: ScaleTransition(
        scale: _scale,
        child: Container(
          width: 68,
          height: 68,
          decoration: BoxDecoration(
            color: Colors.white,
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.08),
                blurRadius: 8,
                offset: const Offset(0, 3),
              ),
            ],
          ),
          child: Center(
            child: Text(
              widget.digit,
              style: const TextStyle(
                color: Color(0xFF0F172A),
                fontSize: 26,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ── Orange call button ───────────────────────────────────────────────────────
class _CallButton extends StatefulWidget {
  final VoidCallback onTap;
  const _CallButton({required this.onTap});

  @override
  State<_CallButton> createState() => _CallButtonState();
}

class _CallButtonState extends State<_CallButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 100),
    );
    _scale = Tween(begin: 1.0, end: 0.88).animate(
      CurvedAnimation(parent: _ctrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => _ctrl.forward(),
      onTapUp: (_) {
        _ctrl.reverse();
        HapticFeedback.heavyImpact();
        widget.onTap();
      },
      onTapCancel: () => _ctrl.reverse(),
      child: ScaleTransition(
        scale: _scale,
        child: Container(
          width: 68,
          height: 68,
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [Color(0xFFFF6A00), Color(0xFFFF8F3C)],
            ),
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: const Color(0xFFFF6A00).withValues(alpha: 0.4),
                blurRadius: 16,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          child: const Icon(
            Icons.call_rounded,
            color: Colors.white,
            size: 30,
          ),
        ),
      ),
    );
  }
}

// ── Generic circular action button ───────────────────────────────────────────
class _ActionCircle extends StatelessWidget {
  final double size;
  final Color bgColor;
  final Widget child;
  final VoidCallback onTap;
  const _ActionCircle({
    required this.size,
    required this.bgColor,
    required this.child,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: bgColor,
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.06),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Center(child: child),
      ),
    );
  }
}
