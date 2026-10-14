plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.consumer"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.consumer"
        minSdk = 26
        targetSdk = 37
    }
    buildTypes {
        release {
            optimization { enable = true }
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation("com.naurylab:voxera-livekit-compose:0.1.0-alpha01")
    implementation("com.naurylab:voxera-android:0.1.0-alpha01")
    implementation("androidx.activity:activity-compose:1.13.0")
}
