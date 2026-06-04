// File generated manually from google-services.json and Firebase project config.
// For web: uses the Firebase project's web API key and project settings.

import 'package:firebase_core/firebase_core.dart' show FirebaseOptions;
import 'package:flutter/foundation.dart'
    show defaultTargetPlatform, kIsWeb, TargetPlatform;

class DefaultFirebaseOptions {
  static FirebaseOptions get currentPlatform {
    if (kIsWeb) {
      return web;
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return android;
      case TargetPlatform.iOS:
        throw UnsupportedError(
          'DefaultFirebaseOptions have not been configured for ios - '
          'you can reconfigure this by running the FlutterFire CLI again.',
        );
      case TargetPlatform.macOS:
        throw UnsupportedError(
          'DefaultFirebaseOptions have not been configured for macos.',
        );
      case TargetPlatform.windows:
        throw UnsupportedError(
          'DefaultFirebaseOptions have not been configured for windows.',
        );
      case TargetPlatform.linux:
        throw UnsupportedError(
          'DefaultFirebaseOptions have not been configured for linux.',
        );
      default:
        throw UnsupportedError(
          'DefaultFirebaseOptions are not supported for this platform.',
        );
    }
  }

  // Web config — uses the same project, API key from google-services.json
  static const FirebaseOptions web = FirebaseOptions(
    apiKey: 'AIzaSyCglTPydKZMApQuAVGgdxOozd5-rPeTgVs',
    appId: '1:279660317288:web:leaddialer_web',
    messagingSenderId: '279660317288',
    projectId: 'leaddialer-4ac7e',
    authDomain: 'leaddialer-4ac7e.firebaseapp.com',
    databaseURL: 'https://leaddialer-4ac7e-default-rtdb.firebaseio.com',
    storageBucket: 'leaddialer-4ac7e.firebasestorage.app',
  );

  // Android config from google-services.json
  static const FirebaseOptions android = FirebaseOptions(
    apiKey: 'AIzaSyCglTPydKZMApQuAVGgdxOozd5-rPeTgVs',
    appId: '1:279660317288:android:a80bb864437520c18189dd',
    messagingSenderId: '279660317288',
    projectId: 'leaddialer-4ac7e',
    databaseURL: 'https://leaddialer-4ac7e-default-rtdb.firebaseio.com',
    storageBucket: 'leaddialer-4ac7e.firebasestorage.app',
  );
}
