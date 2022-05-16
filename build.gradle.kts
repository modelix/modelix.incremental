plugins {
    kotlin("multiplatform") version "1.6.20"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.2"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.6.21"
    `maven-publish`
    id("com.palantir.git-version") version "0.13.0"
}

repositories {
    mavenCentral()
}

kotlin {
    /* Targets configuration omitted. 
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

    jvm()
    js() {
        //browser {}
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
                implementation("io.github.microutils:kotlin-logging:2.1.21")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.1")
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
    val gitVersion: groovy.lang.Closure<String> by extra
    gitVersion()
}
if (!project.findProperty("ciBuild")?.toString().toBoolean()) {
    version = "$version-SNAPSHOT"
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
    }
}