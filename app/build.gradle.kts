import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Load API keys from local.properties (local dev) or environment (CI)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

fun apiKey(name: String): String {
    val raw = localProps.getProperty(name)
        ?: System.getenv(name)
        ?: ""
    return "\"$raw\""
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

android {
    namespace   = "com.iris.assistant"
    compileSdk  = 36

    defaultConfig {
        applicationId = "com.iris.assistant"
        minSdk        = 26
        targetSdk     = 36
        versionCode   = 1
        versionName   = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY",   apiKey("GEMINI_API_KEY"))
        buildConfigField("String", "GROQ_API_KEY",     apiKey("GROQ_API_KEY"))
        buildConfigField("String", "WEATHER_API_KEY",  apiKey("WEATHER_API_KEY"))
        buildConfigField("String", "BRAVE_SEARCH_API_KEY", apiKey("BRAVE_SEARCH_API_KEY"))
        buildConfigField("String", "NEWS_API_KEY",         apiKey("NEWS_API_KEY"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material.components)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.googlefonts)
    implementation(libs.phosphoricons.core)
    implementation(libs.phosphoricons.regular)
    implementation(libs.phosphoricons.filled)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Lifecycle + ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // DataStore
    implementation(libs.datastore.preferences)

    // Networking
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Wake Word — openWakeWord via ONNX Runtime
    // Requires 3 ONNX assets in app/src/main/assets/:
    //   hey_jarvis.onnx, melspectrogram.onnx, embedding_model.onnx
    // onnxruntime-android:1.18.0 comes in as a transitive dependency
    implementation(libs.openwakeword)

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
}