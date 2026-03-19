plugins {
    `kotlin-dsl`
    kotlin("jvm") version "2.3.0-RC"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(gradleApi()) // Gradle API for classes like RepositoryHandler
    implementation(localGroovy()) // Groovy support for Gradle
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDirs("kotlin")
            resources.srcDirs("resources")
        }
    }
}
