// `-PcoreOnly=true` builds/tests the pure-Kotlin core without an Android SDK.
val coreOnly = providers.gradleProperty("coreOnly").orNull == "true"

pluginManagement {
    repositories {
        if (providers.gradleProperty("coreOnly").orNull != "true") google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("coreOnly").orNull != "true") google()
        mavenCentral()
    }
}

rootProject.name = "CamDect"

include(":core")
if (!coreOnly) {
    include(":app")
}
