rootProject.name = "gradle-git-versioning-plugin"

enableFeaturePreview("STABLE_CONFIGURATION_CACHE") // https://docs.gradle.org/7.5.1/userguide/configuration_cache.html#config_cache:stable

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
