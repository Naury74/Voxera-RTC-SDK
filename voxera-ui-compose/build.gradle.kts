plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.naury.voxerartc.ui"
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
    buildFeatures {
        compose = true
    }
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all {
            // Robolectric은 JDK 내부 API로 FileDescriptor를 다루는데, JDK 17+는 이를 기본으로 export하지 않는다.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
        }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":voxera-core"))
    api(project(":voxera-invitation"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // 화면 구성 요소와 움직임은 Kinetica UI 키트로 그린다. 공개 API에는 Kinetica 타입을 내보내지 않는다.
    implementation(libs.kinetica.ui)
    implementation(libs.kinetica.render.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(project(":voxera-testkit"))
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
