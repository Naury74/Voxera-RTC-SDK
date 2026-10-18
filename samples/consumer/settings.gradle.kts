pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 아직 배포하지 않은 Voxera만 로컬 Maven에서 받는다. Kinetica는 바깥 앱처럼 Maven Central에서 온다.
        mavenLocal { content { includeModuleByRegex("com\\.naurylab", "voxera-.*") } }
        google()
        mavenCentral()
        // LiveKit Android가 쓰는 audioswitch 포크.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.davidliu") }
        }
    }
}
rootProject.name = "voxera-consumer"
include(":app")
