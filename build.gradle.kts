plugins {
    kotlin("jvm") version "2.2.0"
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    id("org.jetbrains.dokka") version "1.9.20"
}

group = "io.github.larsw.parsekek"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.arrow-kt:arrow-core:2.1.2")
    implementation("io.arrow-kt:arrow-optics:2.1.2")
    ksp("io.arrow-kt:arrow-optics-ksp-plugin:2.1.2")

    // Kotest testing framework
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation("io.kotest:kotest-framework-datatest:5.8.0")
    testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs = listOf(
        "-Xcontext-receivers"
    )
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// KDoc generation task
tasks.dokkaHtml.configure {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))

    dokkaSourceSets {
        named("main") {
            moduleName.set("ParseKek")
            moduleVersion.set(version.toString())

            // Include source links
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(uri("https://github.com/larsw/parsekek/tree/main/src/main/kotlin").toURL())
                remoteLineSuffix.set("#L")
            }

            // Package documentation
            perPackageOption {
                matchingRegex.set(".*\\.internal.*")
                suppress.set(true)
            }

            // Samples
            samples.from("src/test/kotlin")
        }
    }
}
