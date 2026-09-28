pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "AzureDoom Maven"
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "HyColony"
include(":core", ":plugin", ":blockui", ":domum-core", ":domum-plugin")
// Unique project names (gradle/gradle#847: two ":core" projects would be confused in dependency resolution).
project(":domum-core").projectDir = file("domum/core")
project(":domum-plugin").projectDir = file("domum/plugin")
