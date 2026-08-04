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
include(":feature:project")
include(":feature:app-promotion")
include(":feature:group-promotion")
include(":feature:team-leader")
include(":feature:content-management")
include(":feature:activity")
include(":feature:chat")
include(":feature:support")
include(":feature:order")
include(":feature:membership")
