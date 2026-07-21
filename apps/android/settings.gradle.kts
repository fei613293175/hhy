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

rootProject.name = "hhy-android"
include(":app")
include(":core:designsystem")
include(":core:network")
include(":feature:shell")
include(":feature:startup")
include(":feature:auth")
include(":feature:media")
include(":feature:identity")
include(":feature:discovery")
