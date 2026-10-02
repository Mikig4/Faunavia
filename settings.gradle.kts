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

rootProject.name = "Faunavia"

include(":app")
include(":core:domain")
include(":core:network")
include(":core:testing")
include(":core:local")
include(":core:taxonomy")
include(":core:route")
include(":core:occurrence")
include(":core:plausibility")
include(":core:exploration")
