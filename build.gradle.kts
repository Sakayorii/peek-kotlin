plugins {
    kotlin("jvm") version "2.4.10"
    `maven-publish`
}

group = "com.sakayori.peek"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// The golden corpus is generated from peek-vanilla (see gen-corpus.mjs).
// It is committed to the repo; regenerate manually after touching the port:
//   ./gradlew regenerateCorpus
tasks.named<Test>("test") {
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<Exec>("regenerateCorpus") {
    workingDir = projectDir
    commandLine("node", "gen-corpus.mjs")
}
