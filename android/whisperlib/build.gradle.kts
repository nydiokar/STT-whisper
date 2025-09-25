plugins {
    id("com.android.library")
    kotlin("android")
    id("idea")
}

android {
    namespace = "com.whispercpp"
    compileSdk = 34

    defaultConfig {
        minSdk = 26  // Match app minSdk for consistency

        ndk {
            // Only real device architectures (removes x86/x86_64 emulator bloat)
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        targetSdk = 34
    }

    // Let Android Studio use its default NDK version
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/whisper/CMakeLists.txt")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Ensure only our library sources are compiled; exclude example sources under whisper.cpp
    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/main/java"))
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")

    // Coroutines - aligned versions
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Try using a pre-built whisper library instead of building from source
    // implementation("io.github.ggerganov:whisper-android:1.5.4")
}

// Help Android Studio/IntelliJ ignore large example/binding trees inside the vendored whisper.cpp
// This does not affect Gradle compilation (we already use only src/main/java),
// but it prevents editor-level duplicate symbol warnings and heavy indexing.
idea {
    module {
        excludeDirs.add(file("whisper.cpp/examples"))
        excludeDirs.add(file("whisper.cpp/bindings"))
        excludeDirs.add(file("whisper.cpp/tests"))
        excludeDirs.add(file("whisper.cpp/models"))
    }
}