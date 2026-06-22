// Root build file. Per the T-001 blueprint (single Gradle module decision, §1), this project
// uses one Gradle module (:app) with ui/domain/data/service Kotlin package boundaries instead of
// separate Gradle modules. This file only declares plugin versions for the root classpath;
// the actual Android application configuration lives in app/build.gradle.kts.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    // Required from Kotlin 2.0 onward when Compose is enabled — replaces the old
    // composeOptions.kotlinCompilerExtensionVersion mechanism used pre-Kotlin-2.0.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.24" apply false
}
