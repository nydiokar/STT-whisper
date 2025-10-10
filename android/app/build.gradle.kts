plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.voiceinput"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.voiceinput"
        minSdk = 26  // Android 8.0 (required by whisperlib)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    // Core Android (aligned versions)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Lifecycle for proper activity/fragment handling
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Coroutines (aligned versions)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Permissions (for audio recording)
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // ONNX Runtime (for both Silero VAD and Whisper)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-extensions-android:0.12.4")

    // JSON (for config) - updated version
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing (updated versions)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}