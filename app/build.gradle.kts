plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.breathy"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.breathy"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "placeholder"
            keyAlias = System.getenv("KEY_ALIAS") ?: "breathy"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "placeholder"
        }
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // ── AndroidX Core ──────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Compose BOM ────────────────────────────────────────────────────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ── Navigation ─────────────────────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Lifecycle ──────────────────────────────────────────────────────────
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // ── Firebase BOM ───────────────────────────────────────────────────────
    val firebaseBom = platform(libs.firebase.bom)
    implementation(firebaseBom)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    // ── Coroutines ─────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ── Coil (Image Loading) ──────────────────────────────────────────────
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // ── Accompanist ────────────────────────────────────────────────────────
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)

    // ── Play Billing ──────────────────────────────────────────────────────
    implementation(libs.billing)
    implementation(libs.billing.ktx)

    // ── AdMob ──────────────────────────────────────────────────────────────
    implementation(libs.admob)

    // ── Google Sign-In ──────────────────────────────────────────────────────
    implementation(libs.play.services.auth)

    // ── CameraX ────────────────────────────────────────────────────────────
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.video)
    implementation(libs.camera.view)

    // ── Image Cropper ──────────────────────────────────────────────────────
    implementation(libs.image.cropper)

    // ── Material Design (XML — for themes.xml, ShapeAppearance, etc.)
    implementation(libs.android.material)

    // ── Kotlin Serialization ───────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── Timber (Logging) ───────────────────────────────────────────────────
    implementation(libs.timber)

    // ── OkHttp (explicit for CloudinaryUploader) ───────────────────────────
    implementation(libs.okhttp)

    // ── ExifInterface (for ImageUploader EXIF orientation) ────────────────
    implementation(libs.exifinterface)

    // ── Testing ────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
