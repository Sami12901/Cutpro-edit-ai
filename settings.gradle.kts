pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "cutpro"

// Milestone 1 Foundation Modules
include(":core:common")
include(":core:model")
include(":core:security")
include(":core:data")

// Milestone 2 Modules
include(":core:timeline")
include(":core:designsystem")
include(":feature:dashboard")
include(":feature:editor")
include(":app")

// Milestone 3 Modules
include(":core:playback")
include(":core:audio")

// Milestone 4 Modules
include(":core:subtitles")
include(":core:motiongraphics")

// Milestone 5 Modules
include(":core:ai")

// Milestone 6 Export Modules
include(":core:export")
include(":feature:export")


