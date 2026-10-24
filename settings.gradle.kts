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

rootProject.name = "OpeningSchool"

include(":app")
include(":core:model")
include(":core:data")
include(":core:ui")
include(":core:plugin-api")
include(":feature:timetable")
include(":feature:home")
include(":feature:settings")
include(":feature:plugins")
include(":adapter:zhengfang")
include(":plugin:hotwater")
include(":plugin:scores")

include(":adapter:zhengfang-external")
include(":adapter:sample-external")
