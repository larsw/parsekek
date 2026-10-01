plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.dokka") version "2.2.0"
}

group = "io.github.larsw.parsekek"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // runParser returns arrow.core.Either, so Arrow is part of the public API
    api("io.arrow-kt:arrow-core:2.2.3")

    // Kotest testing framework
    testImplementation("io.kotest:kotest-runner-junit5:6.2.5")
    testImplementation("io.kotest:kotest-assertions-core:6.2.5")
    testImplementation("io.kotest:kotest-property:6.2.5")
    testImplementation("io.kotest:kotest-assertions-arrow:6.2.5")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// KDoc generation: ./gradlew dokkaGenerate
dokka {
    moduleName.set("ParseKek")
    moduleVersion.set(version.toString())

    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("dokka"))
    }

    dokkaSourceSets.main {
        // Include source links
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/larsw/parsekek/tree/main/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}
