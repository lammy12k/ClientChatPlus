plugins {
    id("net.fabricmc.fabric-loom")
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

    // Minecraft Dependencies
    compileOnly("net.fabricmc:fabric-loader:${providers.gradleProperty("fabric_loader_version").get()}")
    compileOnly("dev.isxander:yet-another-config-lib:${providers.gradleProperty("yacl_version").get()}-fabric")

    // Standard Dependencies
    compileOnly("org.lwjgl:lwjgl-tinyfd:${providers.gradleProperty("tinyfd_version").get()}")
}
