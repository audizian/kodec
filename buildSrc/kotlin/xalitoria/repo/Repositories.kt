@file:Suppress("Unused")

package xalitoria.repo

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.maven

fun RepositoryHandler.sonatypeRepo() =
    maven("https://oss.sonatype.org/content/repositories/snapshots") { name = "sonatype" }

fun RepositoryHandler.spigotRepo() =
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "spigotmc-repo" }

fun RepositoryHandler.paperRepo() =
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc-repo" }

fun RepositoryHandler.jitpack() =
    maven("https://jitpack.io") { name = "Jitpack" }

fun RepositoryHandler.helpChat() {
    maven("https://repo.extendedclip.com/snapshots") { name = "HelpChat-snapshots" }
    maven("https://repo.extendedclip.com/releases") { name = "HelpChat-releases" }
}

fun RepositoryHandler.hytale() {
    maven("https://maven.hytale.com/release") { name = "hytale-release" }
    maven("https://maven.hytale.com/pre-release") { name = "hytale-pre-release" }
}

fun RepositoryHandler.hytaleMods() =
    maven("https://maven.hytale-mods.dev/releases") { name = "hytale-mods" }

fun RepositoryHandler.curseforge() =
    maven("https://cursemaven.com") {
        name = "CurseForge"
        groupBy { "curse.maven" }
    }

fun RepositoryHandler.fabric() =
    maven("https://maven.fabricmc.net/") { name = "FabricMC" }

fun RepositoryHandler.sponge() =
    maven("https://repo.spongepowered.org/maven/") { name = "Sponge" }

fun RepositoryHandler.reposilite(project: Project, url: String) = maven(url) {
    credentials {
        username = System.getenv("MAVEN_USER") ?: project.findProperty("mvn.user") as? String
        password = System.getenv("MAVEN_TOKEN") ?: project.findProperty("mvn.key") as? String
    }
}