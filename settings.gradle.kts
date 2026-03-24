pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://developer.huawei.com/repo/") // HMS Core
        gradlePluginPortal()
    }
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
