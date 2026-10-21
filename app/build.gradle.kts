plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// yt-dlp engine for Android (bundles Python, yt-dlp, QuickJS; ffmpeg is a separate artifact).
// To pick up a newer engine build, bump this number and sync Gradle.
val youtubedlAndroid = "0.18.1"

android {
    namespace = "com.easydownload.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.easydownload.app"
        // Android 10+. Lets us save into Gallery/Music with MediaStore, no storage permission needed.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Almost every phone made in the last 8 years is arm64. Keeps the APK smaller.
        ndk {
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

    packaging {
        jniLibs {
            // Required: the engine ships python/ffmpeg/qjs as native executables that must be extracted.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
