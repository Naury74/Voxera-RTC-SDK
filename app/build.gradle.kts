plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.naury.voxerartc.showcase"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.naury.voxerartc.showcase"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = providers.gradleProperty("VOXERA_VERSION").get()
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // 소비자 검증용 release를 별도 키 없이 설치할 수 있도록 debug 서명을 사용한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":voxera-ui-compose"))
    // 쇼케이스 화면 사이 이동(밀어 넣기, 예측 뒤로 가기)에 Kinetica 화면 전환을 쓴다.
    implementation(libs.kinetica.ui)
    // 오프라인 데모는 자격 증명 없이 가짜 엔진으로 동작한다. 실제 통화와 타입 경계로 분리된다.
    implementation(project(":voxera-testkit"))
    // 실제 통화(BYO LiveKit)와 영상 렌더링. 사용자가 입력한 서버와 토큰으로만 연결한다.
    implementation(project(":voxera-livekit-compose"))
    // 실제 통화 중 앱이 백그라운드로 가도 마이크·카메라를 유지한다.
    implementation(project(":voxera-android"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
}
