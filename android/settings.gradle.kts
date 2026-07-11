pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// Auto-provisions the JDK toolchain (jvmToolchain(17)) when no matching JDK is detected,
// so builds work in Android Studio / CLI without manually pointing at a JDK 17.
plugins {
    // 1.0.0+ 필요: 0.x는 Gradle 9에서 제거된 JvmVendorSpec.IBM_SEMERU를 참조해 설정 단계에서 실패
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "HolyBean"
include(":app")

