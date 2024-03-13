plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.gitVersion)
}

allprojects {
    repositories {
        mavenCentral()
    }
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
