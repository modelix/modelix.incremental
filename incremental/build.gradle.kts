plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.benchmark)
    `maven-publish`
}

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        jvmToolchain(11)
    }
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
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlin.benchmark.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlin.coroutines.swing)
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
            iterationTimeUnit = "s"
            outputTimeUnit = "s"
            //mode = "avgt"
            reportFormat = "text"
            include(".*")
        }
        create("sum") {
            warmups = 4
            iterations = 10
            iterationTime = 1
            iterationTimeUnit = "s"
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
