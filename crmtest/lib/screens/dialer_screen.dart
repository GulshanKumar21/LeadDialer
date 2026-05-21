import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_phone_direct_caller/flutter_phone_direct_caller.dart';
import '../models/lead.dart';
import '../services/call_db_service.dart';
import '../models/call_record.dart';
import 'package:firebase_auth/firebase_auth.dart';

// ────────────────────────────────────────────────────────────────────────────
//  DialerScreen — Flutter port of DialerActivity.kt
//  Full keypad with animated buttons, save, call, delete
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

    final manualLead = Lead(name: 'Manual Dial', phone: _number);
    final now = DateTime.now().millisecondsSinceEpoch;
    final calledBy = FirebaseAuth.instance.currentUser?.email ?? 'Unknown';

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
      backgroundColor: const Color(0xFF111827),
      body: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: 24),
            // Title
            const Text(
              'Dialer',
              style: TextStyle(
                color: Color(0xFF8B95A7),
                fontSize: 18,
                fontWeight: FontWeight.w500,
              ),
            ),
            const SizedBox(height: 16),

            // ── Number Display ─────────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Container(
                width: double.infinity,
                height: 90,
                decoration: BoxDecoration(
                  color: const Color(0xFF1B2230),
                  borderRadius: BorderRadius.circular(28),
                  boxShadow: const [
                    BoxShadow(
                      color: Colors.black38,
                      blurRadius: 14,
                      offset: Offset(0, 4),
                    ),
                  ],
                ),
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    AnimatedSwitcher(
                      duration: const Duration(milliseconds: 150),
                      child: Text(
                        _number.isEmpty ? '—' : _number,
                        key: ValueKey(_number),
                        style: TextStyle(
                          color: _number.isEmpty ? Colors.grey : Colors.white,
                          fontSize: 32,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 3,
                        ),
                      ),
                    ),
                    if (_hasNumber)
                      Positioned(
                        right: 16,
                        child: GestureDetector(
                          onTap: _backspace,
                          onLongPress: _clear,
                          child: Container(
                            width: 42,
                            height: 42,
                            decoration: BoxDecoration(
                              color: const Color(0xFF252D3B),
                              borderRadius: BorderRadius.circular(21),
                            ),
                            child: const Icon(
                              Icons.backspace_rounded,
                              color: Color(0xFFB0BACB),
                              size: 20,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            // ── Keypad ─────────────────────────────────────────────────────
            Expanded(
              child: GridView.count(
                crossAxisCount: 3,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                mainAxisSpacing: 4,
                crossAxisSpacing: 4,
                padding: const EdgeInsets.symmetric(horizontal: 32),
                children: [
                  for (final btn in [
                    '1','2','3','4','5','6','7','8','9','*','0','#'
                  ])
                    _DialButton(
                      label: btn,
                      onTap: () => _append(btn),
                    ),
                ],
              ),
            ),

            // ── Action row ─────────────────────────────────────────────────
            AnimatedOpacity(
              opacity: _hasNumber ? 1.0 : 0.0,
              duration: const Duration(milliseconds: 200),
              child: Padding(
                padding: const EdgeInsets.only(
                    bottom: 32, left: 40, right: 40, top: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    // Save button
                    _ActionBtn(
                      icon: Icons.person_add_rounded,
                      color: const Color(0xFF1F2937),
                      size: 58,
                      onTap: () {/* save contact */},
                    ),
                    const SizedBox(width: 16),
                    // Call button (big green)
                    _ActionBtn(
                      icon: Icons.call_rounded,
                      color: const Color(0xFF22C55E),
                      size: 72,
                      onTap: _makeCall,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Keypad digit button ──────────────────────────────────────────────────────
class _DialButton extends StatefulWidget {
  final String label;
  final VoidCallback onTap;
  const _DialButton({required this.label, required this.onTap});

  @override
  State<_DialButton> createState() => _DialButtonState();
}

class _DialButtonState extends State<_DialButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 100));
    _scale = Tween(begin: 1.0, end: 0.90).animate(_ctrl);
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        _ctrl.forward().then((_) => _ctrl.reverse());
        widget.onTap();
      },
      child: ScaleTransition(
        scale: _scale,
        child: Container(
          margin: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: const Color(0xFF1E2A3A),
            borderRadius: BorderRadius.circular(20),
            boxShadow: const [
              BoxShadow(color: Colors.black45, blurRadius: 6, offset: Offset(0, 3)),
            ],
          ),
          child: Center(
            child: Text(
              widget.label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 28,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ── Action button ────────────────────────────────────────────────────────────
class _ActionBtn extends StatelessWidget {
  final IconData icon;
  final Color color;
  final double size;
  final VoidCallback onTap;

  const _ActionBtn({
    required this.icon,
    required this.color,
    required this.size,
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
          color: color,
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: color.withValues(alpha: 0.5),
              blurRadius: 14,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Icon(icon, color: Colors.white, size: size * 0.38),
      ),
    );
  }
}
