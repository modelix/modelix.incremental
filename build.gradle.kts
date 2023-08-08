plugins {
    kotlin("multiplatform") version "1.7.20"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.5"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.7.20"
    `maven-publish`
    id("com.palantir.git-version") version "0.15.0"
}

repositories {
    mavenCentral()
}

kotlin {
    /* Targets configuration omitted.
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

    jvm()
    js(IR) {
        browser {}
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("io.github.microutils:kotlin-logging:3.0.5")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.5")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.4")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}


allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("jvmTest")
        register("jsTest")
    }
    configurations {
        named("main") {
            warmups = 4
            iterations = 10
            iterationTime = 1
            outputTimeUnit = "s"
            //mode = "avgt"
            reportFormat = "text"
            include(".*")
        }
        create("sum") {
            warmups = 4
            iterations = 10
            iterationTime = 1
            outputTimeUnit = "s"
            reportFormat = "text"
            include("RecursiveSum")
            exclude("RecursiveSumLarge")
            exclude("RecursiveSumNonIncremental")
        }
    }
}

group = "org.modelix"
description = "Incremental computation engine"

val versionFile = projectDir.resolve("version.txt")
version = if (versionFile.exists()) {
    versionFile.readText().trim()
} else {
    val ci = !project.findProperty("ciBuild")?.toString().toBoolean()
    val gitVersion: groovy.lang.Closure<String> by extra
    gitVersion() + (if (ci) "-SNAPSHOT" else "")
}
println("Version: $version")

publishing {
    repositories {
        if (project.hasProperty("artifacts.itemis.cloud.user")) {
            maven {
                name = "itemisNexus3"
                url = if (version.toString().contains("SNAPSHOT"))
                          uri("https://artifacts.itemis.cloud/repository/maven-mps-snapshots/")
                      else
                          uri("https://artifacts.itemis.cloud/repository/maven-mps-releases/")
                credentials {
                    username = project.findProperty("artifacts.itemis.cloud.user").toString()
                    password = project.findProperty("artifacts.itemis.cloud.pw").toString()
                }
            }
        }

        val ghp_username = project.findProperty("gpr.user") as? String ?: System.getenv("GITHUB_ACTOR")
        val ghp_password = project.findProperty("gpr.key") as? String ?: System.getenv("GITHUB_TOKEN")

        if (ghp_username != null && ghp_password != null) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/modelix/incremental")
                credentials {
                    username = ghp_username
                    password = ghp_password
                }
            }
        }
    }
}
