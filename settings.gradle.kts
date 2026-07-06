pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://jitpack.io")
        maven {
            name = "githubPackages"
            // A repository must be specified for some reason. "registry" is a dummy.
            url = uri("https://maven.pkg.github.com/revanced/registry")
            credentials {
                username = providers.gradleProperty("githubPackagesUsername")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orElse(providers.environmentVariable("GH_USERNAME"))
                    .orElse("github-actions")
                    .get()
                password = providers.gradleProperty("githubPackagesPassword")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orElse(providers.environmentVariable("GH_TOKEN"))
                    .orElse("")
                    .get()
            }
        }
    }
}

rootProject.name = "revanced-manager"
include(":app", ":api")
