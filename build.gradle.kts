plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // 공개 API 목록을 모듈마다 api/ 아래에 두고, 바뀌면 apiCheck가 실패한다. 의도한 변경이면 apiDump로 갱신한다.
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka)
}

apiValidation {
    ignoredProjects += listOf("app")
}

// SDK 모듈은 같은 좌표로 배포한다. 저장소의 app(Showcase)은 배포하지 않는다.
val sdkModules = mapOf(
    "voxera-rtc-api" to "엔진에 매이지 않는 통화 모델, 오류, 토큰 공급자 계약 (Kotlin JVM)",
    "voxera-core" to "Voxera 진입점과 통화 세션: 상태 머신, 재연결 제한 시간, 토큰 재요청, Java 진입점 (Kotlin JVM)",
    "voxera-invitation" to "전화처럼 걸고 받는 Call Mode: 초대 계약, CallCoordinator, 푸시 payload 파서 (Kotlin JVM)",
    "voxera-android" to "통화 중 백그라운드 유지와 통화 알림, PiP, 근접 화면 끄기, 시스템 통화 등록",
    "voxera-livekit" to "LiveKit Android SDK로 실제 통화를 하는 엔진과 토큰 공급자",
    "voxera-livekit-compose" to "LiveKit 영상을 Voxera 통화 화면에 그리는 Compose 렌더러",
    "voxera-testkit" to "테스트용 가짜 엔진과 네트워크 없이 도는 데모 시나리오 (Kotlin JVM)",
    "voxera-ui-compose" to "Compose 통화 화면: 입장 전 화면, 영상 무대, 조작 막대, 채팅, 반응, 품질 시트",
)

val projectUrl = "https://github.com/Naury74/Voxera-RTC-SDK"

// Maven Central 업로드 묶음을 만들 임시 저장소. 서명·체크섬이 붙은 배포 파일이 모듈별로 모인다.
val centralStaging = layout.buildDirectory.dir("central-staging")

// 공식 배포 없이 다른 프로젝트가 받아 보는 개발 판 저장소. mavenLocal과 따로 둬서, 받는 쪽이 켰을 때만 쓰이게 한다.
val devRepo = providers.gradleProperty("naurylabDevRepo").orNull
    ?: "${System.getProperty("user.home")}/.naurylab/dev-repo"

// publishDev를 실행할 때만 개발 판 버전으로 바꾸고 서명하지 않는다.
val publishingDev = gradle.startParameter.taskNames.any { it == "publishDev" || it.endsWith(":publishDev") }

// 호스트 앱이 더 낮은 Kotlin(2.2 이상)으로 빌드해도 SDK를 읽을 수 있게 언어·API 버전과 표준 라이브러리 버전을 낮춰 둔다.
val minimumConsumerKotlin = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2

subprojects {
    val summary = sdkModules[name] ?: return@subprojects
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "org.jetbrains.dokka")
    // Maven Central은 javadoc jar를 요구한다. Dokka HTML을 묶어 javadoc 분류자로 함께 올린다.
    val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
        description = "Dokka HTML을 javadoc jar로 묶는다."
        from(tasks.named("dokkaGeneratePublicationHtml"))
        archiveClassifier.set("javadoc")
    }
    pluginManager.withPlugin("com.android.library") { configureConsumerKotlin() }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { configureConsumerKotlin() }
    group = providers.gradleProperty("VOXERA_GROUP").get()
    version = providers.gradleProperty(if (publishingDev) "VOXERA_DEV_VERSION" else "VOXERA_VERSION").get()

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
                    artifact(dokkaJavadocJar)
                    artifactId = project.name
                    pom {
                        name.set(project.name)
                        description.set(summary)
                        url.set(projectUrl)
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
                            }
                        }
                        developers {
                            developer {
                                id.set("Naury74")
                                name.set("Naury74")
                            }
                        }
                        scm {
                            url.set(projectUrl)
                            connection.set("scm:git:$projectUrl.git")
                            developerConnection.set("scm:git:ssh://git@github.com/Naury74/Voxera-RTC-SDK.git")
                        }
                        issueManagement {
                            system.set("GitHub")
                            url.set("$projectUrl/issues")
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "CentralStaging"
                    url = uri(centralStaging)
                }
                maven {
                    name = "NaurylabDev"
                    url = uri(devRepo)
                }
            }
        }
        configureSigning()
    }
}

/**
 * 서명 키가 있으면 배포 파일에 서명한다. 키는 저장소에 두지 않고 `~/.gradle/gradle.properties`나 환경 변수에서 읽는다.
 *
 * - `signingInMemoryKey` / `signingInMemoryKeyPassword`: ASCII armor 비밀 키와 비밀번호(CI에 알맞음).
 * - `signing.gnupg.keyName` (+ `signing.gnupg.passphrase`): 설치된 gpg로 서명(개인 컴퓨터에 알맞음).
 *
 * 키가 없으면 서명하지 않으므로 `publishToMavenLocal`은 그대로 동작한다. Central 묶음을 만들 때는 서명이 필수다.
 */
fun Project.configureSigning() {
    if (publishingDev) return
    val inMemoryKey = providers.gradleProperty("signingInMemoryKey").orNull?.trim()?.takeIf { it.isNotEmpty() }
    val useGpg = providers.gradleProperty("signing.gnupg.keyName").isPresent
    if (inMemoryKey == null && !useGpg) return
    extensions.configure<SigningExtension> {
        if (inMemoryKey != null) {
            useInMemoryPgpKeys(inMemoryKey, providers.gradleProperty("signingInMemoryKeyPassword").orNull?.trim())
        } else {
            useGpgCmd()
        }
        sign(extensions.getByType<PublishingExtension>().publications)
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

// API 문서: ./gradlew :dokkaGenerate → build/dokka/html. SDK 모듈을 한 사이트로 묶는다.
dependencies {
    sdkModules.keys.forEach { dokka(project(":$it")) }
}

dokka {
    moduleName.set("Voxera RTC SDK")
}

val sdkProjects = subprojects.filter { it.name in sdkModules }

/** 모든 SDK 모듈을 서명해 임시 저장소에 모으고 Central Portal 업로드용 zip을 만든다. */
val centralBundle = tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Maven Central 업로드 묶음(build/central-bundle.zip)을 만든다."
    dependsOn(sdkProjects.map { "${it.path}:publishReleasePublicationToCentralStagingRepository" })
    doFirst {
        val root = centralStaging.get().asFile
        val unsigned = root.walkTopDown().filter { it.isFile && it.extension in setOf("aar", "jar", "pom", "module") }
            .filter { !java.io.File(it.path + ".asc").exists() }.toList()
        check(unsigned.isEmpty()) { "서명되지 않은 파일이 있습니다. 서명 키 설정(~/.gradle/gradle.properties)을 확인하세요: ${unsigned.take(3)}" }
    }
    from(centralStaging) { exclude("**/maven-metadata*") }
    archiveFileName.set("central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory)
    notCompatibleWithConfigurationCache("임시 저장소 내용을 실행 시점에 검사한다")
}

tasks.register("cleanCentralStaging") {
    group = "publishing"
    description = "이전 버전이 섞이지 않도록 Central 임시 저장소를 비운다."
    val dir = centralStaging
    doLast { dir.get().asFile.deleteRecursively() }
}

/**
 * 묶음을 Central Portal에 올린다. 기본은 검증 후 대기(USER_MANAGED)라 포털에서 내용을 확인하고 직접 Publish를 누른다.
 * 자격 증명은 Central Portal에서 만든 사용자 토큰(`centralPortalUsername` / `centralPortalPassword`)이다.
 * `-PcentralAutoPublish=true`를 주면 검증이 끝나는 대로 바로 공개한다(되돌릴 수 없다).
 */
tasks.register("publishToCentral") {
    group = "publishing"
    description = "build/central-bundle.zip을 Maven Central(Central Portal)에 업로드한다."
    dependsOn(centralBundle)
    notCompatibleWithConfigurationCache("업로드는 네트워크 작업이다")
    doLast {
        // 복사해 붙여 넣으며 생긴 앞뒤 공백이 토큰에 섞이면 인증이 실패하므로 잘라 낸다.
        val user = providers.gradleProperty("centralPortalUsername").orNull?.trim()?.takeIf { it.isNotEmpty() }
        val password = providers.gradleProperty("centralPortalPassword").orNull?.trim()?.takeIf { it.isNotEmpty() }
        check(user != null && password != null) { "centralPortalUsername / centralPortalPassword가 없습니다. ~/.gradle/gradle.properties에 넣으세요." }
        val bundle = layout.buildDirectory.file("central-bundle.zip").get().asFile
        val auto = providers.gradleProperty("centralAutoPublish").orNull == "true"
        val token = java.util.Base64.getEncoder().encodeToString("$user:$password".toByteArray())
        val boundary = "----Voxera${System.currentTimeMillis()}"
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"${bundle.name}\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n"
        val body = head.toByteArray() + bundle.readBytes() + "\r\n--$boundary--\r\n".toByteArray()
        val name = java.net.URLEncoder.encode("${project.property("VOXERA_GROUP")}:voxera:${project.property("VOXERA_VERSION")}", "UTF-8")
        val type = if (auto) "AUTOMATIC" else "USER_MANAGED"
        val request = java.net.http.HttpRequest.newBuilder(
            uri("https://central.sonatype.com/api/v1/publisher/upload?publishingType=$type&name=$name"),
        )
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        val response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "업로드 실패(${response.statusCode()}): ${response.body()}" }
        logger.lifecycle("업로드 완료. 배포 ID: ${response.body()}")
        logger.lifecycle(if (auto) "검증이 끝나면 자동으로 공개됩니다." else "https://central.sonatype.com/publishing/deployments 에서 확인 후 Publish를 누르세요.")
    }
}

/**
 * 공식 배포 없이 다른 프로젝트에서 바로 시험할 개발 판을 올린다. 모든 SDK 모듈을 `VOXERA_DEV_VERSION`으로
 * 개발 판 저장소(기본 `~/.naurylab/dev-repo`, `-PnaurylabDevRepo`로 바꿈)에 서명 없이 올리고,
 * 어느 커밋에서 올렸는지 `voxera-dev.txt`에 남긴다. 커밋하지 않은 변경이 있으면 멈추고, `-PallowDirty=true`면 그대로 올린다.
 */
tasks.register("publishDev") {
    group = "publishing"
    description = "개발 판(VOXERA_DEV_VERSION)을 개발 판 저장소에 올린다."
    dependsOn(sdkProjects.map { "${it.path}:publishReleasePublicationToNaurylabDevRepository" })
    val repo = devRepo
    val devVersion = providers.gradleProperty("VOXERA_DEV_VERSION")
    val allowDirty = providers.gradleProperty("allowDirty").orNull == "true"
    val commit = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }.standardOutput.asText.map { it.trim() }
    val status = providers.exec { commandLine("git", "status", "--porcelain") }.standardOutput.asText.map { it.trim() }
    doFirst {
        check(allowDirty || status.get().isEmpty()) {
            "커밋하지 않은 변경이 있습니다. 커밋한 뒤 올리거나 -PallowDirty=true를 붙이세요."
        }
    }
    doLast {
        val dirty = status.get().isNotEmpty()
        val info = buildString {
            appendLine("version=${devVersion.get()}")
            appendLine("commit=${commit.get()}${if (dirty) "-dirty" else ""}")
            appendLine("time=${java.time.OffsetDateTime.now().withNano(0)}")
        }
        java.io.File(repo, "voxera-dev.txt").writeText(info)
        logger.lifecycle("개발 판을 올렸습니다: $repo\n$info")
    }
}
