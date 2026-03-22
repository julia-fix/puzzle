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

rootProject.name = "puzzle"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":domain:jigsaw")
include(":data:progress")
include(":data:catalog")
include(":feature:gallery")
include(":feature:piececount")
include(":feature:gameplay")
