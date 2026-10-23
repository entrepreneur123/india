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
        minSdk = 24          // Android 7.0+: low-end phones are still common in many markets
        targetSdk = 36
        versionCode = 33
        versionName = "0.18.9"

        // Phone push (FCM) without the google-services plugin: the Firebase project's values from gradle properties or
        // the environment (FIREBASE_APP_ID, FIREBASE_API_KEY, FIREBASE_PROJECT_ID, FIREBASE_SENDER_ID). Empty (the
        // default) = push off: no Firebase start-up and no token (live/Fcm.kt). docs/LAUNCH_GUIDE.md 4-2.
        listOf("FIREBASE_APP_ID", "FIREBASE_API_KEY", "FIREBASE_PROJECT_ID", "FIREBASE_SENDER_ID").forEach { key ->
            val value = providers.gradleProperty(key).orElse(providers.environmentVariable(key)).getOrElse("")
            require(value.none { it == '"' || it == '\\' }) { "$key: no quotes or backslashes" }
            buildConfigField("String", key, "\"$value\"")
        }
    }

    // UI languages (the in-app picker, AppLocale.languages, and Android 13+'s per-app language list from
    // res/xml/locales_config.xml): English, Hindi, Bengali, Urdu, Punjabi, Marathi, Gujarati, Tamil, Telugu, Kannada,
    // Malayalam, Nepali, Portuguese (Brazil), Spanish, French, German, Italian, Dutch, Polish, Romanian, Turkish,
    // Russian, Ukrainian, Arabic, Persian, Hebrew (iw), Swahili, Vietnamese, Thai, Indonesian (in), Malay, Filipino,
    // Japanese, Korean, Chinese (Simplified, Traditional). A language without its res/values-* strings yet shows in
    // English. localeFilters also drops every other language from the libraries' resources (smaller APK); zh-rCN/TW/HK
    // and es-rUS keep the libraries' own Chinese and Latin American Spanish. Keep in sync with AppLocale.languages,
    // xml/locales_config.xml and TranslationsTest.
    androidResources {
        localeFilters += listOf(
            "en", "hi", "bn", "ur", "pa", "mr", "gu", "ta", "te", "kn", "ml", "ne", "pt-rBR", "es", "es-rUS", "fr", "de", "it",
            "nl", "pl", "ro", "tr", "ru", "uk", "ar", "fa", "iw", "sw", "vi", "th", "in", "ms", "fil", "ja", "ko",
            "b+zh+Hans", "b+zh+Hant", "zh-rCN", "zh-rTW", "zh-rHK",
        )
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

    // Direct-install APKs per CPU with -PabiSplits (LiveKit's WebRTC is native): arm64-v8a for most phones,
    // armeabi-v7a for older / 32-bit Android Go phones still common in many markets. Off by default because AGP cannot build
    // split APKs and the Play App Bundle (which splits by itself) in the same run.
    val directApks = providers.gradleProperty("abiSplits").isPresent
    splits {
        abi {
            isEnable = directApks
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    // Play App Bundle: split by CPU and screen density, but keep every language in the base APK so the in-app
    // language switch (Settings > Language) works offline on every phone. Together they are only a few hundred KB.
    bundle {
        language { enableSplit = false }
    }

    packaging {
        // APKs shared as files (WhatsApp, Files Go) are downloaded on mobile data: compress the native libraries
        // (WebRTC alone is 12 MB stored, about 5 MB compressed). The Play bundle keeps them uncompressed: Play
        // compresses the download itself and the phone then needs no extra copy.
        jniLibs.useLegacyPackaging = directApks
        // Sources and debug data nobody reads at runtime.
        resources.excludes += listOf("google/protobuf/*.proto", "DebugProbesKt.bin", "META-INF/*.version")
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
    // Voice rooms' "listen together": YouTube's official IFrame player in a WebView (MIT, ~80 KB), both flavors.
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")
    // The identity check's hosted page (ID + selfie) in a Chrome Custom Tab. The live build already had it through
    // supabase-kt; a few KB in the demo.
    implementation("androidx.browser:browser:1.9.0")
    // Installs the baseline profile (src/main/baseline-prof.txt + the libraries' own) on sideloaded installs too,
    // so the first launches run precompiled code instead of the interpreter.
    implementation(libs.androidx.profileinstaller)
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
    // On-device nudity check of the partner's video (live/assets/nsfw_mobilenet_v2.tflite, data/Nudity.kt). LiteRT 1.x
    // is the classic TensorFlow Lite interpreter, CPU (XNNPACK) only; 2.x adds a GPU accelerator and Guava.
    "liveImplementation"("com.google.ai.edge.litert:litert:1.4.2")
    // Phone push (FCM). FirebaseApp is set up by hand from BuildConfig (no google-services plugin); see live/Fcm.kt.
    "liveImplementation"("com.google.firebase:firebase-messaging:25.0.1")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
