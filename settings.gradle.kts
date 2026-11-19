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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.mik3y") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "OpenLauncher"
include(":app")
