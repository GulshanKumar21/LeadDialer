import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/call_db_service.dart';
import '../services/firebase_sync_service.dart';
import 'main_shell.dart';

// ─────────────────────────────────────────────────────────────────────────────
//  LoginScreen — Flutter port of LoginPage.kt
//
//  Features:
//  • Premium hero gradient (deep orange) matching native Android
//  • ADYAPANSCHOOL branding in hero
//  • Floating card form with progressive password reveal
//  • Firebase Email/Password Auth
//  • Sync-before-logout (same as Native Android fix)
// ─────────────────────────────────────────────────────────────────────────────

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailCtrl    = TextEditingController();
  final _passCtrl     = TextEditingController();
  final _emailFocus   = FocusNode();
  final _passFocus    = FocusNode();

  bool _showPassword  = false;
  bool _obscurePass   = true;
  bool _loading       = false;

  @override
  void initState() {
    super.initState();
    // Show password field once email has text
    _emailCtrl.addListener(() {
      final show = _emailCtrl.text.isNotEmpty;
      if (show != _showPassword) {
        setState(() => _showPassword = show);
      }
    });
  }

  @override
  void dispose() {
    _emailCtrl.dispose();
    _passCtrl.dispose();
    _emailFocus.dispose();
    _passFocus.dispose();
    super.dispose();
  }

  // ── Login ──────────────────────────────────────────────────────────────────
  Future<void> _login() async {
    final email = _emailCtrl.text.trim();
    final pass  = _passCtrl.text.trim();

    if (email.isEmpty) {
      _showError('Email required');
      return;
    }
    if (!RegExp(r'^[^@]+@[^@]+\.[^@]+').hasMatch(email)) {
      _showError('Invalid email');
      return;
    }
    if (pass.isEmpty) {
      _showError('Password required');
      return;
    }
    if (pass.length < 6) {
      _showError('Minimum 6 characters');
      return;
    }

    setState(() => _loading = true);
    try {
      await FirebaseAuth.instance.signInWithEmailAndPassword(
        email: email,
        password: pass,
      );

      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('is_logged_in', true);
      await prefs.setString('saved_email', email);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text("🔥 Let's Close More Deals Today"),
            backgroundColor: Color(0xFFE64A19),
          ),
        );
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const MainShell()),
        );
      }
    } on FirebaseAuthException catch (e) {
      final msg = switch (e.code) {
        'user-not-found'   => 'User not found ❌',
        'wrong-password'   => 'Wrong password ❌',
        'invalid-email'    => 'Invalid email ❌',
        'user-disabled'    => 'Account disabled ❌',
        _                  => 'Login failed ❌',
      };
      _showError(msg);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ── Forgot Password ────────────────────────────────────────────────────────
  void _forgotPassword() {
    final emailCtrl = TextEditingController(text: _emailCtrl.text);
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
          top: 24, left: 24, right: 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Reset Password',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            const Text('Enter your email to receive a reset link.',
                style: TextStyle(color: Colors.grey, fontSize: 13)),
            const SizedBox(height: 16),
            TextField(
              controller: emailCtrl,
              keyboardType: TextInputType.emailAddress,
              decoration: InputDecoration(
                hintText: 'you@adyapan.com',
                prefixIcon: const Icon(Icons.email_outlined),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFE64A19),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
                onPressed: () async {
                  final e = emailCtrl.text.trim();
                  if (e.isEmpty) return;
                  try {
                    await FirebaseAuth.instance.sendPasswordResetEmail(email: e);
                    if (ctx.mounted) {
                      Navigator.pop(ctx);
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Reset link sent! Check your email.')),
                      );
                    }
                  } catch (_) {
                    if (ctx.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Failed to send reset link.')),
                      );
                    }
                  }
                },
                child: const Text('Send Reset Link',
                    style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), backgroundColor: Colors.red.shade700),
    );
  }

  // ── Build ──────────────────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // adjustPan equivalent — resizeToAvoidBottomInset scrolls view up
      resizeToAvoidBottomInset: true,
      body: SingleChildScrollView(
        child: Column(
          children: [
            // ── HERO SECTION ─────────────────────────────────────────────
            _buildHero(),

            // ── FORM CARD ────────────────────────────────────────────────
            Transform.translate(
              offset: const Offset(0, -36),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Card(
                  elevation: 16,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Brand section inside card
                        Center(
                          child: Column(
                            children: [
                              const Text(
                                'ADYAPANSCHOOL',
                                style: TextStyle(
                                  color: Color(0xFFE64A19),
                                  fontWeight: FontWeight.bold,
                                  fontSize: 26,
                                  letterSpacing: 2,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Container(
                                width: 48,
                                height: 3,
                                decoration: BoxDecoration(
                                  color: const Color(0xFFE64A19).withOpacity(0.5),
                                  borderRadius: BorderRadius.circular(2),
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                "India's Largest Student Community",
                                style: TextStyle(
                                  color: Colors.grey.shade500,
                                  fontSize: 11,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 24),

                        // Welcome text
                        const Text('Welcome Back 👋',
                            style: TextStyle(
                              fontSize: 22,
                              fontWeight: FontWeight.bold,
                              color: Color(0xFF1A1A1A),
                            )),
                        const SizedBox(height: 4),
                        Text('Sign in to your account',
                            style: TextStyle(
                              fontSize: 13,
                              color: Colors.grey.shade500,
                            )),
                        const SizedBox(height: 24),

                        // Email label
                        const Text('Email Address',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              color: Color(0xFF444444),
                              letterSpacing: 0.5,
                            )),
                        const SizedBox(height: 8),

                        // Email field
                        _buildField(
                          controller: _emailCtrl,
                          focusNode: _emailFocus,
                          hint: 'you@adyapan.com',
                          icon: Icons.person_outline_rounded,
                          keyboardType: TextInputType.emailAddress,
                          textInputAction: TextInputAction.next,
                          onSubmitted: (_) => _passFocus.requestFocus(),
                        ),
                        const SizedBox(height: 16),

                        // Password field — progressive reveal
                        AnimatedSize(
                          duration: const Duration(milliseconds: 250),
                          curve: Curves.easeOut,
                          child: _showPassword
                              ? Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const Text('Password',
                                        style: TextStyle(
                                          fontSize: 12,
                                          fontWeight: FontWeight.bold,
                                          color: Color(0xFF444444),
                                          letterSpacing: 0.5,
                                        )),
                                    const SizedBox(height: 8),
                                    _buildPasswordField(),
                                    const SizedBox(height: 4),
                                  ],
                                )
                              : const SizedBox.shrink(),
                        ),

                        // Forgot password
                        Align(
                          alignment: Alignment.centerRight,
                          child: GestureDetector(
                            onTap: _forgotPassword,
                            child: const Padding(
                              padding: EdgeInsets.symmetric(vertical: 8),
                              child: Text(
                                'Forgot Password?',
                                style: TextStyle(
                                  color: Color(0xFFE64A19),
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),

                        // Sign In button
                        SizedBox(
                          width: double.infinity,
                          height: 54,
                          child: ElevatedButton(
                            onPressed: _loading ? null : _login,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: const Color(0xFFE64A19),
                              disabledBackgroundColor: Colors.grey.shade300,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(14),
                              ),
                              elevation: 4,
                            ),
                            child: _loading
                                ? const SizedBox(
                                    width: 22,
                                    height: 22,
                                    child: CircularProgressIndicator(
                                      color: Colors.white,
                                      strokeWidth: 2.5,
                                    ),
                                  )
                                : const Text(
                                    'Sign In  →',
                                    style: TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 16,
                                    ),
                                  ),
                          ),
                        ),

                        const SizedBox(height: 20),
                        // Divider
                        Row(children: [
                          Expanded(child: Divider(color: Colors.grey.shade200)),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            child: Text('Adyapan CRM',
                                style: TextStyle(
                                    color: Colors.grey.shade400, fontSize: 10)),
                          ),
                          Expanded(child: Divider(color: Colors.grey.shade200)),
                        ]),
                      ],
                    ),
                  ),
                ),
              ),
            ),

            // ── FOOTER STATS ─────────────────────────────────────────────
            Transform.translate(
              offset: const Offset(0, -36),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
                child: Row(
                  children: [
                    _statItem('50K+', 'Students'),
                    _divider(),
                    _statItem('100+', 'Courses'),
                    _divider(),
                    _statItem('#1', 'In India'),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── Hero ───────────────────────────────────────────────────────────────────
  Widget _buildHero() {
    return SizedBox(
      height: 300,
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Gradient background
          Container(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                colors: [Color(0xFFBF360C), Color(0xFFE64A19), Color(0xFFFF7043)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
            ),
          ),
          // Dark overlay
          Container(color: Colors.black.withOpacity(0.3)),
          // Content
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const SizedBox(height: 40), // status bar
              const Text(
                'ADYAPANSCHOOL',
                style: TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                  fontSize: 26,
                  letterSpacing: 3,
                ),
              ),
              const SizedBox(height: 8),
              Container(width: 40, height: 2, color: const Color(0xFFFFCC80)),
              const SizedBox(height: 10),
              Text(
                "India's Largest Student Community",
                style: TextStyle(
                  color: Colors.white.withOpacity(0.85),
                  fontSize: 12,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // ── Input field ────────────────────────────────────────────────────────────
  Widget _buildField({
    required TextEditingController controller,
    required FocusNode focusNode,
    required String hint,
    required IconData icon,
    TextInputType keyboardType = TextInputType.text,
    TextInputAction textInputAction = TextInputAction.done,
    void Function(String)? onSubmitted,
  }) {
    return Container(
      height: 52,
      decoration: BoxDecoration(
        color: const Color(0xFFF8F8F8),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFE8E8E8), width: 1.5),
      ),
      child: Row(
        children: [
          const SizedBox(width: 16),
          Icon(icon, color: const Color(0xFFE64A19), size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: TextField(
              controller: controller,
              focusNode: focusNode,
              keyboardType: keyboardType,
              textInputAction: textInputAction,
              onSubmitted: onSubmitted,
              decoration: InputDecoration(
                hintText: hint,
                hintStyle: const TextStyle(color: Color(0xFFC5C5C5), fontSize: 14),
                border: InputBorder.none,
              ),
              style: const TextStyle(fontSize: 14, color: Color(0xFF1A1A1A)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPasswordField() {
    return Container(
      height: 52,
      decoration: BoxDecoration(
        color: const Color(0xFFF8F8F8),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFE8E8E8), width: 1.5),
      ),
      child: Row(
        children: [
          const SizedBox(width: 16),
          const Icon(Icons.lock_outline_rounded, color: Color(0xFFE64A19), size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: TextField(
              controller: _passCtrl,
              focusNode: _passFocus,
              obscureText: _obscurePass,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _login(),
              decoration: const InputDecoration(
                hintText: '••••••••',
                hintStyle: TextStyle(color: Color(0xFFC5C5C5), fontSize: 14),
                border: InputBorder.none,
              ),
              style: const TextStyle(fontSize: 14, color: Color(0xFF1A1A1A)),
            ),
          ),
          IconButton(
            icon: Icon(
              _obscurePass ? Icons.visibility_off_outlined : Icons.visibility_outlined,
              color: Colors.grey.shade400,
              size: 20,
            ),
            onPressed: () => setState(() => _obscurePass = !_obscurePass),
          ),
        ],
      ),
    );
  }

  // ── Footer stat ────────────────────────────────────────────────────────────
  Widget _statItem(String value, String label) {
    return Expanded(
      child: Column(
        children: [
          Text(value,
              style: const TextStyle(
                color: Color(0xFFE64A19),
                fontWeight: FontWeight.bold,
                fontSize: 20,
              )),
          Text(label,
              style: const TextStyle(color: Colors.grey, fontSize: 11)),
        ],
      ),
    );
  }

  Widget _divider() => Container(
        width: 1, height: 32,
        color: Colors.grey.shade200,
      );
}

// ─────────────────────────────────────────────────────────────────────────────
//  Logout Helper — sync-before-logout (mirrors LoginPage.kt logic)
// ─────────────────────────────────────────────────────────────────────────────
class AuthService {
  /// Syncs all pending local data to Firebase, then signs out.
  /// [onDone] is called on completion (success or failure).
  static Future<void> logout(BuildContext context, {VoidCallback? onDone}) async {
    // Show sync progress dialog
    if (context.mounted) {
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (_) => const AlertDialog(
          title: Text('⏳ Syncing Data'),
          content: Text('Uploading pending records to cloud before logout...'),
        ),
      );
    }

    try {
      // Sync pending local data to Firebase RTDB before clearing
      await FirebaseSyncService.syncAllPendingData();
    } catch (e) {
      debugPrint('Pre-logout sync failed: $e');
      // Still proceed — don't block logout on sync failure
    }

    // Sign out
    await FirebaseAuth.instance.signOut();

    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('is_logged_in', false);
    await prefs.remove('saved_email');

    if (context.mounted) {
      Navigator.of(context).pop(); // close progress dialog
      onDone?.call();
    }
  }
}
