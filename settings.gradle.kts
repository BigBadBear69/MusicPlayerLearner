pluginManagement {
    repositories {
        google()
        // Google-hosted mirror of Maven Central (fewer rate limits), then Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google-hosted mirror of Maven Central (fewer rate limits), then Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "Wavelength"
include(":app")
