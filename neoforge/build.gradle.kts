plugins {
    id("net.neoforged.moddev")
}

val localRuntime = configurations.create("localRuntime")

configurations {
    named("runtimeClasspath") {
        extendsFrom(localRuntime)
    }
    configureEach {
        resolutionStrategy {
            force(
                "net.java.dev.jna:jna:${providers.gradleProperty("jna_version").get()}",
                "net.java.dev.jna:jna-platform:${providers.gradleProperty("jna_version").get()}"
            )
        }
    }
}


dependencies {
    implementation("dev.isxander:yet-another-config-lib:${providers.gradleProperty("yacl_version").get()}-neoforge")

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

neoForge {
    version = providers.gradleProperty("neoforge_version").get()

    runs {
        create("clientNeoforge") {
            client()
            gameDirectory.set(rootProject.file("run_neoforge"))
            systemProperty("neoforge.enabledGameTestNamespaces", "clientchatplus")
        }

        create("serverNeoforge") {
            server()
            gameDirectory.set(rootProject.file("run_neoforge"))
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", "clientchatplus")
        }

        create("gameTestServerNeoforge") {
            type = "gameTestServer"
            gameDirectory.set(rootProject.file("run_neoforge"))
            systemProperty("neoforge.enabledGameTestNamespaces", "clientchatplus")
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        create("clientchatplus") {
            sourceSet(sourceSets.main.get())
        }
    }
}
