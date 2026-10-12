pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Guardian Project raw GitHub Maven (hosts info.guardianproject:arti-mobile-ex)
        maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/master") }
        // Briar Project Maven (hosts org.briarproject:onionwrapper-android for hidden services)
        maven { url = uri("https://briarproject.org/raw/maven/") }
    }
}

rootProject.name = "seamless-android"
include(":app")

// Using published Arti AAR; local module not included
