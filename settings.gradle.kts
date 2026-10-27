pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Aerial"
include(":app")
// Lite has its own signing setup; personal builds must not even configure it.
if (providers.gradleProperty("aluBuild").orNull != "true") include(":lite")
