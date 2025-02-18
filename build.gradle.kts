import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "net.frankheijden.serverutils"
val dependencyDir = "${group}.dependencies"
version = "3.5.5-SNAPSHOT"

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "checkstyle")
    apply(plugin = "com.github.johnrengelman.shadow")


    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://papermc.io/repo/repository/maven-public/")
        maven("https://libraries.minecraft.net")
    }

    dependencies {
        implementation("cloud.commandframework:cloud-core:${VersionConstants.cloudVersion}")
        implementation("cloud.commandframework:cloud-brigadier:${VersionConstants.cloudVersion}")
        implementation("com.github.FrankHeijden:MinecraftReflection:1.0.0")
        implementation("com.google.code.gson:gson:2.8.6")
        implementation("me.lucko:commodore:2.2")
        compileOnly("com.mojang:brigadier:1.0.18")

        testImplementation("org.assertj:assertj-core:3.18.1")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
        testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.0")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.0")
    }

    tasks {
        build {
            dependsOn("shadowJar", "checkstyleMain", "checkstyleTest", "test")
        }

        compileJava {
            options.encoding = Charsets.UTF_8.name()
            options.isDeprecation = true
        }

        javadoc {
            options.encoding = Charsets.UTF_8.name()
        }

        processResources {
            filteringCharset = Charsets.UTF_8.name()
        }

        test {
            useJUnitPlatform()
        }
    }

    tasks.withType<Checkstyle>().configureEach {
        configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
        ignoreFailures = false
        maxErrors = 0
        maxWarnings = 0
    }

    tasks.withType<ShadowJar> {
        exclude("com/mojang/**")
        exclude("javax/annotation/**")
        exclude("org/checkerframework/**")
        relocate("com.google.gson", "${dependencyDir}.gson")
        relocate("dev.frankheijden.minecraftreflection", "${dependencyDir}.minecraftreflection")
        relocate("cloud.commandframework", "${dependencyDir}.cloud")
        relocate("me.lucko.commodore", "${dependencyDir}.commodore")
        relocate("io.leangen.geantyref", "${dependencyDir}.typetoken")
        if (project.name != "Velocity") {
            relocate("net.kyori.adventure", "${dependencyDir}.adventure")
            relocate("net.kyori.examination", "${dependencyDir}.examination")
        }
        relocate("net.kyori.adventure.text.minimessage", "${dependencyDir}.adventure.text.minimessage")
        relocate("dev.frankheijden.minecraftreflection", "${dependencyDir}.minecraftreflection")
    }

    publishing {
        repositories {
            maven {
                name = "fvdh"
                url = if (rootProject.version.toString().endsWith("-SNAPSHOT")) {
                    uri("https://repo.fvdh.dev/snapshots")
                } else {
                    uri("https://repo.fvdh.dev/releases")
                }

                credentials {
                    username = System.getenv("FVDH_USERNAME")
                    password = System.getenv("FVDH_TOKEN")
                }
            }
        }

        publications {
            create<MavenPublication>("ServerUtils") {
                artifact(tasks["shadowJar"]) {
                    classifier = ""
                }
                artifactId = "ServerUtils-$artifactId"
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":Common", "shadow"))
    implementation(project(":Bungee", "shadow"))
    implementation(project(":Velocity", "shadow"))
    implementation("net.kyori:adventure-text-serializer-gson:${VersionConstants.adventureVersion}") {
        exclude("net.kyori", "adventure-api")
        exclude("com.google.code.gson", "gson")
    }
}

tasks {
    clean {
        dependsOn("cleanJars")
    }

    build {
        dependsOn("shadowJar", "copyJars")
    }
}

tasks.withType<ShadowJar> {
    relocate("net.kyori.adventure.text.serializer.gson", "${dependencyDir}.impl.adventure.text.serializer.gson")
}

fun outputTasks(): List<Task> {
    return listOf(
        ":Bukkit:shadowJar",
        ":Bungee:shadowJar",
        ":Velocity:shadowJar",
    ).map { tasks.findByPath(it)!! }
}

tasks.register("cleanJars") {
    delete(file("jars"))
}

tasks.register<Copy>("copyJars") {
    outputTasks().forEach {
        from(it) {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }
    into(file("jars"))
    rename("(.*)-all.jar", "$1.jar")
}

publishing {
    repositories {
        maven {
            name = "fvdh"
            url = if (version.toString().endsWith("-SNAPSHOT")) {
                uri("https://repo.fvdh.dev/snapshots")
            } else {
                uri("https://repo.fvdh.dev/releases")
            }

            credentials {
                username = System.getenv("FVDH_USERNAME")
                password = System.getenv("FVDH_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("ServerUtils") {
            artifact(tasks["shadowJar"]) {
                classifier = ""
            }
            artifactId = "ServerUtils"
        }
    }
}
