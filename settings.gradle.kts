pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net")
        }
        maven {
            name = "NeoForged"
            url = uri("https://neoforged.net")
        }

        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("net.fabricmc.fabric-loom") version providers.gradleProperty("fabric_loom_version")
        id("net.neoforged.moddev") version providers.gradleProperty("neoforge_moddev_version")
    }
}

rootProject.name = "ClientChatPlus"

include("common", "fabric", "neoforge")
