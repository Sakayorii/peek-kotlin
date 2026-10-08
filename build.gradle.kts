plugins {
    kotlin("jvm") version "2.4.10"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// The golden corpus is generated from peek-vanilla (see gen-corpus.mjs).
// Regenerate before running tests after touching the port:
//   node gen-corpus.mjs
tasks.named<Test>("test") {
    dependsOn("regenerateCorpus")
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<Exec>("regenerateCorpus") {
    workingDir = projectDir
    commandLine("node", "gen-corpus.mjs")
}
