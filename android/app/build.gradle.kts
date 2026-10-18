plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.lottodost.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lottodost.app"
        minSdk = 24          // Android 7.0+: covers the low-end phones still common in India
        targetSdk = 36       // Google Play requirement for new apps/updates from 31 Aug 2026
        versionCode = 1
        versionName = "0.1.0"

        // Static result JSON produced by /pipeline (v2 feeds), tried in this order until one works.
        // Order follows reachability measured from Indian ISPs on 18-10-2026 (Globalping):
        // github.io and jsDelivr 100 %, raw.githubusercontent.com ~50 % (TCP timeouts on Jio/Airtel/ACT).
        // config.json can replace this list remotely (e.g. a Cloudflare custom domain).
        buildConfigField(
            "String[]", "RESULT_MIRRORS",
            "{\"https://entrepreneur123.github.io/india/v2/\"," +
                "\"https://cdn.jsdelivr.net/gh/entrepreneur123/india@data/v2/\"," +
                "\"https://raw.githubusercontent.com/entrepreneur123/india/data/v2/\"}"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Only ship the UI languages we translate (drops dozens of library locales from the APK).
    androidResources {
        localeFilters += listOf("en", "hi", "bn", "ne", "pa", "mr")
        generateLocaleConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true // java.time on Android 7.x
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric screenshot tests
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    // Unbundled ML Kit: the OCR model comes from Google Play services, keeping our APK small.
    implementation(libs.mlkit.text.recognition)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
