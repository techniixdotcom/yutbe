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
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.TeamNewPipe")
                includeGroup("com.github.chrisbanes")
            }
        }
    }
}

// NewPipe Extractor is built from the vendored sources in ./newpipe-extractor.
includeBuild("newpipe-extractor") {
    dependencySubstitution {
        substitute(module("net.newpipe:extractor")).using(project(":extractor"))
    }
}

rootProject.name = "YuTbe"
include(":app")
