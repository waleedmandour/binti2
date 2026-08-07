plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.huawei.agconnect")
}

android {
    namespace = "com.binti.dilink"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.binti.dilink"
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "2.4.2-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tier 2 — Product Flavors
    //
    // `store`       — the build that ships to Huawei AppGallery. Tier 2
    //                (Advanced Telemetry) code is COMPILED OUT entirely;
    //                no references to `android.hardware.bydauto.*` or any
    //                reflection-based probing can survive into this build.
    //
    // `enthusiast`  — distributed only via GitHub Releases and sideload.
    //                Contains the optional Advanced Telemetry module under
    //                `src/enthusiast/java/...`. All Tier 2 code lives here.
    //
    // The shared `BUILD_FLAVOR_ENTHUSIAST` BuildConfig flag lets main-source
    // code conditionally surface the Advanced Telemetry opt-in UI without
    // ever referencing the enthusiast classes directly.
    // ──────────────────────────────────────────────────────────────────────
    flavorDimensions += "distribution"
    productFlavors {
        create("store") {
            dimension = "distribution"
            buildConfigField("boolean", "ENTHUSIAST_FLAVOR", "false")
        }
        create("enthusiast") {
            dimension = "distribution"
            applicationIdSuffix = ".enthusiast"
            versionNameSuffix = "-enthusiast"
            buildConfigField("boolean", "ENTHUSIAST_FLAVOR", "true")
        }
    }

    signingConfigs {
        create("release") {
            // No hardcoded password fallbacks — release builds MUST be signed
            // with real credentials provided via environment variables. If any
            // of KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD is missing, the
            // build fails with a clear error rather than silently falling back
            // to a weak public password.
            //
            // CI provides these via GitHub Secrets. Local developers must set
            // them in their shell environment before running assembleRelease.
            storeFile = file("release-keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only use the release signing config if the keystore exists AND
            // all three credentials are non-empty. Otherwise fall back to the
            // debug signing config (for local development / unsigned CI builds).
            // This prevents accidental release builds signed with empty credentials.
            val hasReleaseCreds = file("release-keystore.jks").exists() &&
                !(System.getenv("KEYSTORE_PASSWORD").isNullOrEmpty() ||
                  System.getenv("KEY_ALIAS").isNullOrEmpty() ||
                  System.getenv("KEY_PASSWORD").isNullOrEmpty())
            signingConfig = if (hasReleaseCreds) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("⚠️ Release keystore or credentials not set — " +
                    "release APK will be debug-signed. " +
                    "Set KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD env vars " +
                    "for a properly signed release build.")
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }

    // Unit tests: return default values (0 / false / null) for Android
    // framework calls that aren't explicitly mocked — e.g. android.util.Log.v
    // is called from VehicleDataRepository.publishAccessibility() during
    // repository tests. Without this, every Log call throws RuntimeException
    // "Method v in android.util.Log not mocked".
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    
    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // TensorFlow Lite for Wake Word & NLU
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    
    // ONNX Runtime for ASR/NLU
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")

    // Sherpa-ONNX for neural TTS (VITS/MMS Arabic model) — 100% offline,
    // significantly better Arabic voice quality than Android TTS.
    // NOTE: sherpa-onnx is loaded via reflection at runtime if the user
    // downloads the Arabic VITS model. The library JAR is not on Maven
    // Central, so it must be manually placed in app/libs/ or the build
    // will gracefully skip sherpa-onnx and fall back to Android TTS.
    // See docs/BUILD.md for sherpa-onnx setup instructions.
    
    // Huawei HMS Core - AGConnect
    implementation("com.huawei.agconnect:agconnect-core:1.9.1.301")
    
    // Huawei HMS Core - ML Kit for ASR/TTS fallback
    implementation("com.huawei.hms:ml-computer-voice-asr:3.11.0.301")
    implementation("com.huawei.hms:ml-computer-voice-tts:3.11.0.301")
    
    // Vosk for offline ASR
    implementation("com.alphacephei:vosk-android:0.3.47")
    
    // OkHttp for model downloads
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
