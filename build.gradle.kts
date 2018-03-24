/*
 * build.gradle.kts
 * TubeGradlePlugin
 */

/* -------------------------------------------------------------------------- */
// 🛃 Imports
/* -------------------------------------------------------------------------- */

import java.util.Date
import org.gradle.api.JavaVersion.*
import org.gradle.internal.impldep.org.junit.experimental.categories.Categories.CategoryFilter.include
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.junit.platform.console.options.Details
import org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.jetbrains.kotlin.preprocessor.mkdirsOrFail

/* -------------------------------------------------------------------------- */
// 🔌 Plugins
/* -------------------------------------------------------------------------- */

plugins {
    // Gradle built-in
    `java-gradle-plugin`
    `maven-publish`

    // Gradle plugin portal - https://plugins.gradle.org/
    id("com.jfrog.bintray") version "1.8.0"
    id("com.gradle.plugin-publish") version "0.9.10"
    id("org.jetbrains.kotlin.jvm") version "1.2.30"

    // Custom handling in pluginManagement
    id("org.junit.platform.gradle.plugin") version "1.1.0"
}

/* -------------------------------------------------------------------------- */
// 📋 Properties
/* -------------------------------------------------------------------------- */

val artifactName by project
val javaPackage = "$group.$artifactName"
val pluginClass by project

val jvmTarget = JavaVersion.VERSION_1_8.toString()
val spekVersion by project

// This is necessary to make the plugin version accessible in other places
// https://stackoverflow.com/questions/46053522/how-to-get-ext-variables-into-plugins-block-in-build-gradle-kts/47507441#47507441
val junitPlatformVersion: String? by extra {
    buildscript.configurations["classpath"]
            .resolvedConfiguration.firstLevelModuleDependencies
            .find { it.moduleName == "junit-platform-gradle-plugin" }?.moduleVersion
}

/* -------------------------------------------------------------------------- */
// 👪 Dependencies
/* -------------------------------------------------------------------------- */

repositories {
    jcenter()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    // JUnit Platform
    testImplementation("org.junit.platform:junit-platform-runner:$junitPlatformVersion")
    // Spek
    testImplementation("org.jetbrains.spek:spek-api:$spekVersion")
    testImplementation("org.jetbrains.spek:spek-junit-platform-engine:$spekVersion")
}

/* -------------------------------------------------------------------------- */
// 🏗 Assemble
/* -------------------------------------------------------------------------- */

tasks.withType<JavaCompile> {
    sourceCompatibility = jvmTarget
    targetCompatibility = jvmTarget
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = jvmTarget
}

// Include resources
java.sourceSets["main"].resources {
    setSrcDirs(mutableListOf("src/main/resources"))
    include("VERSION.txt")
}

val updateVersionFile by tasks.creating {
    description = "Updates the VERSION.txt file included with the plugin"
    group = "Build"
    doLast {
        val resources = "src/main/resources"
        project.file(resources).mkdirsOrFail()
        val versionFile = project.file("$resources/VERSION.txt")
        versionFile.createNewFile()
        versionFile.writeText(version.toString())
    }
}

tasks.getByName("assemble").dependsOn(updateVersionFile)

val sourcesJar by tasks.creating(Jar::class) {
    dependsOn("classes")
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

val javadocJar by tasks.creating(Jar::class) {
    dependsOn("javadoc")
    classifier = "javadoc"
    val javadoc = tasks.withType<Javadoc>().first()
    from(javadoc.destinationDir)
}

artifacts.add("archives", sourcesJar)
artifacts.add("archives", javadocJar)

configure<BasePluginConvention> {
    // at.phatbl.tube-1.0.0.jar
    archivesBaseName = javaPackage
}

gradlePlugin.plugins.create("$artifactName") {
    id = javaPackage
    implementationClass = "$javaPackage.$pluginClass"
}

pluginBundle {
    website = "https://github.com/phatblat/ShellExec"
    vcsUrl = "https://github.com/phatblat/ShellExec"
    description = "Exec base task alternative which runs commands in a Bash shell."
    tags = mutableListOf("gradle", "exec", "shell", "bash", "kotlin")

    plugins.create("$artifactName") {
        id = javaPackage
        displayName = "Tube plugin"
    }
    mavenCoordinates.artifactId = "$artifactName"
}

/* -------------------------------------------------------------------------- */
// ✅ Test
/* -------------------------------------------------------------------------- */

junitPlatform {
    filters {
        engines {
            include("spek")
        }
        includeClassNamePatterns("^.*Tests?$", ".*Spec", ".*Spek")
    }
    details = Details.TREE
}

/* -------------------------------------------------------------------------- */
// 🚀 Deployment
/* -------------------------------------------------------------------------- */

publishing {
    (publications) {
        "mavenJava"(MavenPublication::class) {
            from(components["java"])
            artifactId = "$artifactName"

            artifact(sourcesJar) { classifier = "sources" }
            artifact(javadocJar) { classifier = "javadoc" }
        }
    }
}

bintray {
    user = property("bintray.user") as String
    key = property("bintray.api.key") as String
    setPublications("mavenJava")
    setConfigurations("archives")
    dryRun = false
    publish = true
    pkg.apply {
        repo = "maven-open-source"
        name = "ShellExec"
        desc = "Gradle plugin with a simpler Exec task."
        websiteUrl = "https://github.com/phatblat/ShellExec"
        issueTrackerUrl = "https://github.com/phatblat/ShellExec/issues"
        vcsUrl = "https://github.com/phatblat/ShellExec.git"
        setLicenses("MIT")
        setLabels("gradle", "plugin", "exec", "shell", "bash")
        publicDownloadNumbers = true
        version.apply {
            name = project.version.toString()
            desc = "ShellExec Gradle Plugin ${project.version}"
            released = Date().toString()
            vcsTag = project.version.toString()
            attributes = mapOf("gradle-plugin" to "${project.group}:$artifactName:$version")

            mavenCentralSync.apply {
                sync = false //Optional (true by default). Determines whether to sync the version to Maven Central.
                user = "userToken" //OSS user token
                password = "password" //OSS user password
                close = "1" //Optional property. By default the staging repository is closed and artifacts are released to Maven Central. You can optionally turn this behaviour off (by puting 0 as value) and release the version manually.
            }
        }
    }
}

val deploy by tasks.creating {
    description = "Deploys the artifact."
    group = "Deployment"
    dependsOn("bintrayUpload")
    dependsOn("publishPlugins")
}
