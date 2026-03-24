plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.huawei.agconnect") version "1.9.1.301" // Huawei AGConnect for AppGallery
}

android {
    namespace = "com.binti.dilink"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.binti.dilink"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-beta01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Enable native AI model support
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        
        // VectorDrawables support
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0") // GPU delegate
    
    // ONNX Runtime for ASR
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")
    
    // Huawei HMS Core - AGConnect for AppGallery
    implementation("com.huawei.agconnect:agconnect-core:1.9.1.301")
    
    // Huawei HMS Core - ML Kit for ASR/TTS fallback
    implementation("com.huawei.hms:ml-speech-semantics-recognizer:3.8.0.301")
    implementation("com.huawei.hms:ml-tts:3.8.0.301")
    implementation("com.huawei.hms:ml-computer-voice-asr:3.8.0.301")
    
    // Huawei Account & In-App Updates
    implementation("com.huawei.hms:hwid:6.12.0.300")
    implementation("com.huawei.updatesdk:updatesdk:6.12.0.300")
    
    // Vosk for offline ASR (Apache 2.0)
    implementation("com.alphacephei:vosk-android:0.3.47")
    
    // OkHttp for model downloads
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-coroutines:5.0.0-alpha.12")
    
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
