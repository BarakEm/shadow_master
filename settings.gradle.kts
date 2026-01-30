pluginManagement {
    repositories {
        maven { url = uri("https://maven.google.com") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.google.com") }
        mavenCentral()
        maven { url = uri("https://csspeechstorage.blob.core.windows.net/maven/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ShadowMaster"
include(":app")
