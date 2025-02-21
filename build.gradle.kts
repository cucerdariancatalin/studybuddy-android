// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
    }

    dependencies {
        // (Plugin marker for OSS Licenses doesn't exist yet)
        classpath(libs.playServices.ossLicenses.gradle)
        // Declared here such that a custom reporter can be used
        classpath(libs.gradleVersions.gradle)
    }
}

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.dagger.hilt) apply false
    id(libs.plugins.gradleVersions.asProvider().get().pluginId)
}

tasks {
    val clean by registering(Delete::class) {
        delete(rootProject.buildDir)
    }
}

apply<com.edricchan.studybuddy.gradle.versions.DependencyUpdatesPlugin>()
