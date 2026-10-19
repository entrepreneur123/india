import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.yaari.chat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yaari.chat"
        minSdk = 24          // Android 7.0+: low-end phones are still common in India
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-proto"
    }

    // English + Hindi; Android 13+ also lists them in the per-app language setting.
    androidResources {
        localeFilters += listOf("en", "hi")
        generateLocaleConfig = true
    }

    flavorDimensions += "server"
    productFlavors {
        create("demo") {
            dimension = "server"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
        create("live") {
            dimension = "server"
            // Public client keys of the Supabase project "yaari-chat" (Mumbai). Not secrets: RLS protects the data.
            buildConfigField("String", "SUPABASE_URL", "\"https://dgynsckrqgnpyyvqekzi.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${providers.gradleProperty("SUPABASE_ANON_KEY").get()}\"")
        }
    }

    // Play upload key: chat/upload.properties (never committed) with storeFile, storePassword, keyAlias, keyPassword.
    val upload = rootProject.file("upload.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    if (upload != null) {
        signingConfigs.create("upload") {
            storeFile = file(upload.getProperty("storeFile"))
            storePassword = upload.getProperty("storePassword")
            keyAlias = upload.getProperty("keyAlias")
            keyPassword = upload.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without upload.properties (CI, prototype APKs) the debug key lets the release build install from a file.
            signingConfig = signingConfigs.getByName(if (upload != null) "upload" else "debug")
        }
    }

    // Direct-install APKs per CPU with -PabiSplits (LiveKit's WebRTC is native). Off by default because
    // AGP cannot build split APKs and the Play App Bundle (which splits by itself) in the same run.
    splits {
        abi {
            isEnable = providers.gradleProperty("abiSplits").isPresent
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true // java.time on Android 7.x
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric UI tests
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    implementation(libs.compose.icons.extended) // unused icons are stripped by R8
    implementation(libs.coil.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Live server, video and payments only in the live flavor (keeps the demo APK small).
    "liveImplementation"(platform(libs.supabase.bom))
    "liveImplementation"(libs.supabase.auth)
    "liveImplementation"(libs.supabase.postgrest)
    "liveImplementation"(libs.supabase.realtime)
    "liveImplementation"(libs.supabase.storage)
    "liveImplementation"(libs.supabase.functions)
    "liveImplementation"(libs.ktor.okhttp)
    // Coil needs a network fetcher for https images (signed Supabase URLs); without it they silently fail.
    "liveImplementation"(libs.coil.network.okhttp)
    "liveImplementation"(libs.kotlinx.serialization.json)
    "liveImplementation"(libs.livekit.android)
    "liveImplementation"(libs.billing.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
