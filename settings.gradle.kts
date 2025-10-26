pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://chaquo.com/maven") }
    }
    plugins {
        id("com.android.application") version "8.5.2"  // Cập nhật AGP
        id("com.chaquo.python") version "16.1.0"  // Cập nhật từ 14.0.2 lên 16.1.0 (mới nhất October 2025)
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://chaquo.com/maven") }
    }
}

rootProject.name = "Into-Demo"
include(":app")