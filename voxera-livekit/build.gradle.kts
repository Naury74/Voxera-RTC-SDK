plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.naury.voxerartc.livekit"
    resourcePrefix = "voxera_"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":voxera-core"))
    // LiveKit 타입은 이 모듈 밖으로 드러나지 않으므로 implementation으로 둔다.
    implementation(libs.livekit.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
}
