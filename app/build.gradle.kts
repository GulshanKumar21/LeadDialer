import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
    id ("kotlin-parcelize")
    alias(libs.plugins.google.gms.google.services)
}


val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties() // Now it works directly
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.adyapan.leaddialer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adyapan.leaddialer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            isMinifyEnabled   = true
            isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
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
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-messaging")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-location:21.0.1")

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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}