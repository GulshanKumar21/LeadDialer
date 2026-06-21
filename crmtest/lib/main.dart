import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'firebase_options.dart';
import 'screens/main_shell.dart';
import 'screens/login_screen.dart';
import 'screens/onboarding_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );
  runApp(const LeadDialerApp());
}

class LeadDialerApp extends StatelessWidget {
  const LeadDialerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Lead Dialer CRM',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFFFF7043),
          brightness: Brightness.light,
        ),
        textTheme: GoogleFonts.poppinsTextTheme(),
        scaffoldBackgroundColor: const Color(0xFFFFF3E8),
      ),
      home: const _AppRouter(),
    );
  }
}

/// Decides which screen to show on launch:
///   1. First time ever  → OnboardingScreen
///   2. Not logged in    → LoginScreen
///   3. Already logged in → MainShell
class _AppRouter extends StatelessWidget {
  const _AppRouter();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<SharedPreferences>(
      future: SharedPreferences.getInstance(),
      builder: (context, snap) {
        // Still loading prefs
        if (!snap.hasData) {
          return const Scaffold(
            backgroundColor: Color(0xFFE64A19),
            body: Center(child: CircularProgressIndicator(color: Colors.white)),
          );
        }

        final prefs     = snap.data!;
        final firstTime = prefs.getBool('firstTime') ?? true;

        // First time → show onboarding
        if (firstTime) {
          return const OnboardingScreen();
        }

        // Returning user → check Firebase auth state
        return StreamBuilder<User?>(
          stream: FirebaseAuth.instance.authStateChanges(),
          builder: (context, authSnap) {
            if (authSnap.connectionState == ConnectionState.waiting) {
              return const Scaffold(
                backgroundColor: Color(0xFFE64A19),
                body: Center(
                    child: CircularProgressIndicator(color: Colors.white)),
              );
            }
            if (authSnap.hasData && authSnap.data != null) {
              return const MainShell(); // already logged in
            }
            return const LoginScreen(); // need to log in
          },
        );
      },
    );
  }
}
