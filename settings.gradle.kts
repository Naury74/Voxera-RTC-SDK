pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiveKit Android가 쓰는 audioswitch 포크만 JitPack에서 받는다. 다른 의존성이 JitPack으로 새지 않게 그룹을 제한한다.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.davidliu") }
        }
        // Kinetica UI 키트는 정식 배포 전이라 로컬 Maven에 올린 판을 쓴다. kinetica-* 모듈만 여기서 받는다.
        // 정식 배포 뒤에는 이 블록만 지우면 같은 좌표가 Maven Central에서 온다.
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeModuleByRegex("com\\.naurylab", "kinetica-.*") }
        }
    }
}

rootProject.name = "Voxera"

include(":app")
include(":voxera-rtc-api", ":voxera-core")
include(":voxera-invitation")
include(":voxera-android")
include(":voxera-livekit", ":voxera-livekit-compose")
include(":voxera-testkit")
include(":voxera-ui-compose")
