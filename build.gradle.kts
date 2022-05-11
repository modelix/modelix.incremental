plugins {
    kotlin("multiplatform") version "1.6.20"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.2"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.6.21"
}

group = "org.modelix"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    /* Targets configuration omitted. 
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

    jvm()
    js(IR) {
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
            iterations = 5
            iterationTime = 1
            outputTimeUnit = "s"
            //mode = "avgt"
            //reportFormat = "text"
            include(".*")
        }
    }
}