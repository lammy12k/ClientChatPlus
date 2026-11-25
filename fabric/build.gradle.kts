plugins {
    id("net.fabricmc.fabric-loom")
}

repositories {
    maven {
        name = "Terraformers"
        url = uri("https://maven.terraformersmc.com/")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("fabric_loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

    implementation("dev.isxander:yet-another-config-lib:${providers.gradleProperty("yacl_version").get()}-fabric")
    implementation("com.terraformersmc:modmenu:${providers.gradleProperty("modmenu_version").get()}")

    // For runClient to launch
    val tinyfdVersion = providers.gradleProperty("tinyfd_version").get()

    compileOnly("org.lwjgl:lwjgl-tinyfd:$tinyfdVersion")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd:$tinyfdVersion")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd:$tinyfdVersion:natives-windows")

    runtimeOnly("net.java.dev.jna:jna:${providers.gradleProperty("jna_version").get()}")
    runtimeOnly("net.java.dev.jna:jna-platform:${providers.gradleProperty("jna_version").get()}")

    compileOnly(project(":common"))
}

tasks.named<JavaCompile>("compileJava") {
    source(project(":common").sourceSets.main.get().allSource)
}

tasks.named<ProcessResources>("processResources") {
    from(project(":common").sourceSets.main.get().resources)
}

loom {
    runs {
        named("client") {
            runDirectory.set(rootProject.file("run_fabric"))
        }
    }
}
