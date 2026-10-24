pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack is used only for libadb-android and its own transitive dependencies.
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\..*") } }
    }
}
rootProject.name = "miftah-core"
include(":app", ":core:model", ":core:capability", ":core:execution", ":core:adb", ":core:audit")
