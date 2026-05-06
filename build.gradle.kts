plugins {
    checkstyle
}

val kotlin_version by extra("2.1.0")

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
    }
}

configure<CheckstyleExtension> {
    toolVersion = "8.13"
    isIgnoreFailures = false
    isShowViolations = true
}

val ci by extra { project.hasProperty("ci") }

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

tasks.register<Checkstyle>("checkstyle") {
    configFile = file("checkstyle.xml")
    source("app/src/main/java")
    include("**/*.java")
    exclude("**/gen/**")
    classpath = files()
}
