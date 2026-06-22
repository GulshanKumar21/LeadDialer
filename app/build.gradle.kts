import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
    id ("kotlin-parcelize")
    alias(libs.plugins.google.gms.google.services)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties() // Now it works directly
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.adyapan.leaddialer"
    compileSdk = 36
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.adyapan.leaddialer"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GAS_SCRIPT_URL",
            "\"${System.getenv("GAS_SCRIPT_URL") ?: "https://script.google.com/macros/s/AKfycbx7q3iVs3h0tVUArSKJ5MF9EaogNPeuGCf6St4jBqDmO1pTC9O6QNhMsJscFH2lXHqRhg/exec"}\"")
        buildConfigField("String", "GAS_NOTIFY_URL",
            "\"${System.getenv("GAS_NOTIFY_URL") ?: "https://script.google.com/macros/s/AKfycbxdo6J3g_i3JXcX6MkSSIBoQyDniqOc6_0tpC8GZ4wwtCV-EIzLnoHdowu0e3GvRAVIHA/exec"}\"")
        // 🔒 SECURITY: HMAC secret for Google Apps Script authentication
        buildConfigField("String", "GAS_SECRET_TOKEN",
            "\"${System.getenv("GAS_SECRET_TOKEN") ?: "adyapan-crm-secret-2026-hmac-key"}\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias      = keystoreProperties["keyAlias"]      as String
                keyPassword   = keystoreProperties["keyPassword"]   as String
                storeFile     = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // 🔒 SECURITY FIX: Enable R8 code shrinking + obfuscation in release
            isMinifyEnabled   = true
            isShrinkResources = true
            isDebuggable      = false
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep minify disabled in debug for faster builds
            isMinifyEnabled   = false
            isShrinkResources = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/*.kotlin_module"

            merges += "META-INF/services/**"
        }
    }
}

dependencies {

    implementation("com.android.volley:volley:1.2.1")

    implementation("androidx.cardview:cardview:1.0.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    // 🔒 SECURITY FIX: Firebase App Check with Play Integrity
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug") // debug builds only

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Video
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.1")

    implementation("com.airbnb.android:lottie:6.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // implementation(libs.firebase.database.ktx)
    ksp(libs.androidx.room.compiler)

    implementation("com.github.denzcoskun:ImageSlideshow:0.1.2")

    implementation("org.apache.poi:poi:5.2.3") {
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
    implementation("org.apache.poi:poi-ooxml:5.2.3") {
        exclude(group = "com.google.guava", module = "listenablefuture")
    }

    implementation("com.google.guava:guava:31.1-android")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.activity:activity-compose:1.9.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
}