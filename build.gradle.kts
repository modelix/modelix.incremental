plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.gitVersion)
    `maven-publish`
}

val versionFile = projectDir.resolve("version.txt")
version = if (versionFile.exists()) {
    versionFile.readText().trim()
} else {
    val ci = !project.findProperty("ciBuild")?.toString().toBoolean()
    val gitVersion: groovy.lang.Closure<String> by extra
    gitVersion() + (if (ci) "-SNAPSHOT" else "")
}
println("Version: $version")

allprojects {
    apply(plugin = "maven-publish")
    repositories {
        mavenCentral()
    }

    group = "org.modelix"
    version = rootProject.version

    publishing {
        repositories {
            if (project.hasProperty("artifacts.itemis.cloud.user")) {
                maven {
                    name = "itemisNexus3"
                    url = if (version.toString().contains("SNAPSHOT")) {
                        uri("https://artifacts.itemis.cloud/repository/maven-mps-snapshots/")
                    } else {
                        uri("https://artifacts.itemis.cloud/repository/maven-mps-releases/")
                    }
                    credentials {
                        username = project.findProperty("artifacts.itemis.cloud.user").toString()
                        password = project.findProperty("artifacts.itemis.cloud.pw").toString()
                    }
                }
            }
        }
    }
}
