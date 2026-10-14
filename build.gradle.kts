plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// SDK 모듈은 같은 좌표로 배포한다. 저장소의 app(Showcase)은 배포하지 않는다.
val sdkModules = mapOf(
    "voxera-rtc-api" to "엔진 중립 RTC 모델·오류·자격 증명 계약 (Kotlin JVM)",
    "voxera-core" to "Voxera 진입점, 세션 상태 머신과 명령 처리 (Kotlin JVM)",
    "voxera-livekit" to "LiveKit Android SDK 기반 실제 RtcEngine",
    "voxera-testkit" to "결정적 FakeRtcEngine과 오프라인 데모 시나리오",
    "voxera-ui-compose" to "Voxera Glass 테마와 통화 Compose UI",
)

// 호스트 앱이 더 낮은 Kotlin(2.2 이상)으로 빌드해도 SDK를 읽을 수 있게 언어·API 버전과 표준 라이브러리 버전을 낮춰 둔다.
val minimumConsumerKotlin = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2

subprojects {
    val summary = sdkModules[name] ?: return@subprojects
    apply(plugin = "maven-publish")
    pluginManager.withPlugin("com.android.library") { configureConsumerKotlin() }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { configureConsumerKotlin() }
    group = providers.gradleProperty("VOXERA_GROUP").get()
    version = providers.gradleProperty("VOXERA_VERSION").get()

    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> { withSourcesJar() }
    }

    // AGP는 평가가 끝난 뒤에 컴포넌트를 만들므로, 컴포넌트가 생길 때 publication을 만든다.
    components.configureEach {
        val component = this
        val wanted = if (plugins.hasPlugin("com.android.library")) "release" else "java"
        if (component.name != wanted) return@configureEach
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("release") {
                    from(component)
                    artifactId = project.name
                    pom {
                        name.set(project.name)
                        description.set(summary)
                        url.set("https://github.com/Naury74/Voxera-RTC-SDK")
                        scm {
                            url.set("https://github.com/Naury74/Voxera-RTC-SDK")
                        }
                    }
                }
            }
        }
    }
}

fun Project.configureConsumerKotlin() {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension>("kotlin") {
        coreLibrariesVersion = "2.2.0"
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            languageVersion.set(minimumConsumerKotlin)
            apiVersion.set(minimumConsumerKotlin)
        }
    }
}
