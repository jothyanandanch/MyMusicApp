plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.mymusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mymusic"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

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

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Core Android
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.compose)

    // ✅ Compose BOM (keeps versions aligned automatically)
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))

    // Compose UI Core (Compose 1.7.0)
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    implementation(libs.firebase.storage)
    implementation(libs.androidx.foundation)
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")

    // Material3 (1.3.0)
    implementation("androidx.compose.material3:material3:1.3.0")

    // Material Icons
    implementation("androidx.compose.material:material-icons-core:1.6.7")
    implementation("androidx.compose.material:material-icons-extended:1.6.7")

    // LiveData bridge for Compose
    implementation("androidx.compose.runtime:runtime-livedata:1.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ✅ ExoPlayer (Media3)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")

    // Coil for image loading
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
