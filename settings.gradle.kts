pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://developer.huawei.com/repo/") // HMS Core
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
        maven("https://developer.huawei.com/repo/") // HMS Core
    }
}

rootProject.name = "binti2"
include(":app")
