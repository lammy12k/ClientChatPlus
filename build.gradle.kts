plugins {
    id("java")
    id("idea")
    id("com.gradleup.shadow") version "9.6.1"
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "idea")

    group = "com.kyfstore.ClientChatPlus"
    version = "0.1.1-SNAPSHOT"

    repositories {
        mavenCentral()

        maven {
            name = "Xander Maven"
            url = uri("https://maven.isxander.dev/releases")
        }
    }

    val shadowImplementation = configurations.create("shadowImplementation")
    val shadowCompileOnly = configurations.create("shadowCompileOnly")
    val shadowRuntimeOnly = configurations.create("shadowRuntimeOnly")

    configurations {
        named("compileClasspath") {
            extendsFrom(shadowImplementation, shadowCompileOnly)
        }

        named("runtimeClasspath") {
            extendsFrom(shadowImplementation, shadowRuntimeOnly)
        }
    }

    dependencies {
        add("shadowImplementation", "org.commonmark:commonmark:${providers.gradleProperty("commonmark_version").get()}")
        add(
            "shadowImplementation",
            "org.commonmark:commonmark-ext-gfm-strikethrough:${providers.gradleProperty("commonmark_version").get()}"
        )
        add("shadowImplementation", "net.kyori:adventure-api:${providers.gradleProperty("adventure_version").get()}")
        add(
            "shadowImplementation",
            "net.kyori:adventure-text-minimessage:${providers.gradleProperty("adventure_version").get()}"
        )
        add("shadowImplementation", "uk.co.caprica:vlcj:${providers.gradleProperty("vlcj_version").get()}") {
            exclude(group = "net.java.dev.jna")
        }
        compileOnly("net.java.dev.jna:jna:${providers.gradleProperty("jna_version").get()}")
        compileOnly("net.java.dev.jna:jna-platform:${providers.gradleProperty("jna_version").get()}")
    }

    java {
        withSourcesJar()

        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
    }

    idea {
        module {
            isDownloadSources = true
            isDownloadJavadoc = true
        }
    }
}

val shadowBundle = configurations.create("shadowBundle") {
    isCanBeConsumed = false
    isCanBeResolved = true

    extendsFrom(
        configurations.getByName("shadowImplementation"),
        configurations.getByName("shadowCompileOnly"),
        configurations.getByName("shadowRuntimeOnly")
    )
}

subprojects {
    afterEvaluate {
        rootProject.configurations.getByName("shadowImplementation").dependencies.forEach {
            dependencies.add("shadowImplementation", it)
        }
        rootProject.configurations.getByName("shadowCompileOnly").dependencies.forEach {
            dependencies.add("shadowCompileOnly", it)
        }
        rootProject.configurations.getByName("shadowRuntimeOnly").dependencies.forEach {
            dependencies.add("shadowRuntimeOnly", it)
        }
    }

    tasks.named<ProcessResources>("processResources") {
        val excludedKeys = setOf(
            "org.gradle.jvmargs",
            "org.gradle.daemon",
            "org.gradle.parallel",
            "org.gradle.caching",
            "org.gradle.configuration-cache"
        )

        val metadataProperties = providers.gradlePropertiesPrefixedBy("").get()
            .filterKeys { it !in excludedKeys }
            .mapKeys { (key, _) -> "gp.$key" }
            .mapValues { (_, value) -> value }
            .toMutableMap()

        metadataProperties["version"] = rootProject.version.toString()
        metadataProperties["mod_id"] = "clientchatplus"
        metadataProperties["mod_name"] = "ClientChatPlus"
        metadataProperties["mod_group_id"] = "com.kyfstore.ClientChatPlus"
        metadataProperties["mod_description"] =
            "A modern, Discord-inspired chat overhaul for Minecraft 26.3 (Fabric & NeoForge in one JAR). Features smooth slide animations, Markdown & MiniMessage, syntax-highlighted codeblocks, inline images, animated GIFs, website hover cards, file previews, VLC/WaterMedia video playback, live search, and bookmarks."

        inputs.properties(metadataProperties)

        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
            filter { line ->
                var result = line

                metadataProperties.forEach { (key, value) ->
                    result = result.replace("\${$key}", value)
                }

                result
            }
        }
    }
}

tasks {
    compileJava {
        enabled = false
    }

    processResources {
        enabled = false
    }

    jar {
        enabled = false
    }

    named<Jar>("sourcesJar") {
        dependsOn(":common:sourcesJar", ":fabric:sourcesJar", ":neoforge:sourcesJar")

        from(project(":common").sourceSets.main.get().allSource)
        from(project(":fabric").sourceSets.main.get().allSource)
        from(project(":neoforge").sourceSets.main.get().allSource)

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    shadowJar {
        dependsOn(":common:classes", ":fabric:classes", ":neoforge:classes")

        from(project(":common").sourceSets.main.get().output)
        from(project(":fabric").sourceSets.main.get().output)
        from(project(":neoforge").sourceSets.main.get().output)

        val licenseSuffix = project.name
        from("LICENSE") {
            rename { "${it}_$licenseSuffix" }
        }

        configurations = listOf(shadowBundle)
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isZip64 = true

        mergeServiceFiles()
        exclude("META-INF/services/uk.co.caprica.*")
        exclude("META-INF/versions/**")

        relocate("org.commonmark", "com.kyfstore.ClientChatPlus.libs.commonmark")
        relocate("net.kyori", "com.kyfstore.ClientChatPlus.libs.kyori")
        relocate("uk.co.caprica.vlcj", "com.kyfstore.ClientChatPlus.libs.vlcj")
    }

    build {
        dependsOn("shadowJar")
    }
}
