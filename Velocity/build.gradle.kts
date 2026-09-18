import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("net.kyori.blossom") version "1.3.0"
}

group = "${rootProject.group}"
val dependencyDir = "${group}.velocity.dependencies"
version = rootProject.version
base {
    archivesName.set("${rootProject.name}-Velocity")
}

java {
    sourceCompatibility = JavaVersion.toVersion(17)
    targetCompatibility = JavaVersion.toVersion(17)
}

configurations.configureEach {
    if (isCanBeResolved) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

repositories {
    mavenLocal()
    maven("https://nexus.velocitypowered.com/repository/maven-public/")
    maven("https://libraries.minecraft.net")
}

dependencies {
    implementation("cloud.commandframework:cloud-velocity:${VersionConstants.cloudVersion}")
    implementation("org.bstats:bstats-velocity:${VersionConstants.bstatsVersion}")
    implementation(project(":Common"))
    implementation("net.kyori:adventure-text-minimessage:${VersionConstants.adventureMinimessageVersion}") {
        exclude("net.kyori", "adventure-api")
    }
    compileOnly("com.velocitypowered:velocity-api:3.5.0-coral.1")
    compileOnly("com.velocitypowered:velocity-brigadier:1.0.0-SNAPSHOT")
    compileOnly("com.electronwill.night-config:toml:3.6.3")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-coral.1")
}

tasks {
    blossom {
        replaceToken("{version}", version, "src/main/java/net/frankheijden/serverutils/velocity/ServerUtils.java")
    }
}

tasks.withType<ShadowJar> {
    relocate("org.bstats", "${dependencyDir}.bstats")
}
