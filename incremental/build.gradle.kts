plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlin.benchmark)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
    jvm {}
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
                api(project(":dependency-tracking"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlin.benchmark.runtime)
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
                implementation(libs.logback.classic)
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
            // mode = "avgt"
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
