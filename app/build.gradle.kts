plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    id("com.huawei.agconnect")
}

android {
    namespace = "com.binti.dilink"
    // Phase 2 fix: compileSdk must be 35 because androidx.core:core-ktx:1.15.0
    // (and other AndroidX 1.15+ libraries) require compileSdk 35. This does
    // NOT change runtime behavior — targetSdk stays at 33 for BYD DiLink 3
    // compatibility. compileSdk only affects which APIs are visible at
    // compile time; targetSdk opts the app into new runtime behaviors.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.binti.dilink"
        minSdk = 26
        targetSdk = 33
        versionCode = 16
        versionName = "3.0.0-alpha"

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
            // build falls back to debug signing (for local dev / unsigned CI).
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
        // Phase 2: Enable Jetpack Compose. Existing XML views continue to work
        // alongside Compose — new screens (Settings, Diagnostics, Vehicle State)
        // will be built in Compose; existing screens migrate incrementally.
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = true
        xmlReport = true
        htmlReport = true
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/detekt.yml")
    parallel = true
    ignoreFailures = true
}

// KSP (Kotlin Symbol Processing) — used by Hilt and Room for code generation.
// Args are configured at the top level (NOT inside android { defaultConfig { } }).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    // ── Module dependencies ──────────────────────────────────────────────
    // :contract — public intent API for external automation (Tasker, ADB)
    implementation(project(":contract"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.gridlayout)

    // Material Design
    implementation(libs.material)

    // Jetpack Compose (Phase 2) — BOM-managed, incremental adoption.
    // Existing XML views + ViewBinding continue to work; new screens use Compose.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt (Phase 2) — dependency injection. Existing singletons continue
    // to work via their getInstance() pattern; new code uses @Inject.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room (Phase 2) — database for charging stations and diagnostics.
    // Existing StationManager (asset-based) continues to work; the new
    // StationRepository wraps Room and will gradually replace it.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // NOTE: TensorFlow Lite and ONNX Runtime dependencies were removed in
    // v2.6.0-beta. The TFLite-based WakeWordDetector (dead code) was deleted,
    // and IntentClassifier's ML stubs (classifyWithML / matchByFuzzy) were
    // removed — the classifier is now purely rule-based, which is sufficient
    // for the Egyptian-Arabic intent vocabulary. Sherpa-ONNX TTS continues
    // to load via reflection from a manually-placed JAR (see docs/BUILD.md).

    // Huawei HMS Core - AGConnect
    implementation(libs.huawei.agconnect.core)

    // Huawei HMS Core - ML Kit for ASR/TTS fallback
    implementation(libs.huawei.hms.ml.asr)
    implementation(libs.huawei.hms.ml.tts)

    // Vosk for offline ASR
    implementation(libs.vosk.android)

    // JSON parsing
    implementation(libs.gson)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json implementation for JVM tests — Android's org.json is a stub
    // that returns null/0 for everything, causing JSON parsing tests to fail.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    // Compose UI testing — add ui-test-junit4 when we write Compose tests
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Detekt formatting rules
    detektPlugins(libs.detekt.formatting)
}
