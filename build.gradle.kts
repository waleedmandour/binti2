// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://developer.huawei.com/repo/")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.1")
        classpath("com.huawei.agconnect:agcp:1.9.1.301")
    }
}

plugins {
    id("com.android.application") version "8.2.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
