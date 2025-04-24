pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io")
            gradlePluginPortal()
        }

    }


}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // For Supabase SDK
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
        maven { url = uri("https://jitpack.io") }
        // For testing dependencies
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
        // For Cash App's Turbine
        maven { url = uri("https://maven.pkg.github.com/cashapp/turbine") }
    }

}

rootProject.name = "Gigs"
include(":app")