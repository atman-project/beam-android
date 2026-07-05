plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "sh.atman.beam"
    compileSdk = 35

    defaultConfig {
        applicationId = "sh.atman.beam"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            val storePath = providers.gradleProperty("BEAM_KEYSTORE_PATH").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("BEAM_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("BEAM_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("BEAM_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // CameraX for the QR scanner preview surface.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // ML Kit decodes the barcode frames out of the CameraX stream.
    implementation(libs.mlkit.barcode.scanning)

    // ZXing core encodes the outgoing-ticket QR. (Bitmap is rendered by us.)
    implementation(libs.zxing.core)

    // UniFFI-generated atman.kt depends on JNA at runtime; `@aar` picks
    // the Android variant that bundles per-ABI libjnidispatch.so.
    implementation(libs.jna) { artifact { type = "aar" } }
    implementation(libs.kotlinx.coroutines.core)
}
