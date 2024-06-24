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

    // Set maven metadata for all known publishing tasks. The exact tasks and names are only known after evaluation.
    afterEvaluate {
        tasks.withType<AbstractPublishToMaven>() {
            this.publication?.apply {
                setMetadata()
            }
        }
    }
}

fun MavenPublication.setMetadata() {
    pom {
        url.set("https://github.com/modelix/modelix.incremental")
        scm {
            connection.set("scm:git:https://github.com/modelix/modelix.incremental.git")
            url.set("https://github.com/modelix/modelix.incremental")
        }
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
    }
}
