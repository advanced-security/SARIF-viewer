pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://plugins.jetbrains.com/maven")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
    }
}

rootProject.name = "SARIF-viewer"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
        maven("https://plugins.jetbrains.com/maven")
    }
    versionCatalogs {
        create("viewer") {
            library("java-sarif", "com.contrastsecurity:java-sarif:2.0")
        }
    }
}