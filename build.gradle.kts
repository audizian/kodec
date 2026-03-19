import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import xalitoria.repo.hytale

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "hytale.xalitoria"
version = "0.1.0"

repositories {
    mavenCentral()
    hytale()
}

dependencies {
    compileOnly(libs.hytale.server)
    implementation(libs.ksp)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_24)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
            "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlinx.serialization.InternalSerializationApi",
        )
    }
    sourceSets {
        main {
            kotlin.srcDirs("kotlin")
            resources.srcDirs("resources")
        }
    }
}

java {
    toolchain {
        // use JDK 25 to compile
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    // but emit Java 24 bytecode:
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24

    sourceSets {
        main {
            java.srcDirs("java")
        }
    }
    disableAutoTargetJvm()
}

publishing {
    publications.create<MavenPublication>("plugin") {
        artifact(
            tasks.register("mainJar", Jar::class) {
                archiveClassifier.set("")
                from(sourceSets["main"].output)
            }.get()
        )
        artifact(
            tasks.register("sourceJar", Jar::class) {
                archiveClassifier.set("sources")
                from(sourceSets["main"].allSource)
            }.get()
        )
    }
}