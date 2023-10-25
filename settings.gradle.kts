rootProject.name = "SARIF-viewer"

dependencyResolutionManagement {
    versionCatalogs {
        create("viewer") {
            library("java-sarif", "com.contrastsecurity:java-sarif:2.0")
        }
    }
}