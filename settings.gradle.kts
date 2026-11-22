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

rootProject.name = "OpenTV"
include(":app")
// Home 2 de prueba: solo se incluye con -PwithNova=true (docs/HOME2-NOVA.md). Por defecto el build es el de siempre.
if (providers.gradleProperty("withNova").orNull == "true") include(":homenova")
