plugins {
    id("java")
    id("net.minecraftforge.gradle")
}

val mcVersion = sc.current.version
version = "${property("mod.version")}-$mcVersion-Release"
group = "laggyboi"
base.archivesName = property("mod.archive") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    // ForgeGradle 7 serves Minecraft + Forge through its local "mavenizer" repo.
    minecraft.mavenizer(this)
    mavenCentral()
    maven("https://maven.minecraftforge.net/")
    maven("https://libraries.minecraft.net/")
}

dependencies {
    // Forge 1.20.6+ runs on Mojang names, so there is no reobf / refmap step.
    // Vivecraft is deliberately NOT a dependency: VivecraftBridge uses reflection.
    implementation(minecraft.dependency("net.minecraftforge:forge:$mcVersion-${property("deps.forge")}"))
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version.toString(),
            "minecraft_version" to mcVersion,
            // Exact: each jar is built for one Minecraft version.
            "minecraft_range" to "[$mcVersion]",
            "forge_version" to project.property("deps.forge").toString(),
        )
        inputs.properties(props)
        filesMatching("META-INF/mods.toml") { expand(props) }
        filesMatching("pack.mcmeta") { expand("pack_meta" to packMeta(mcVersion)) }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = requiredJava.majorVersion.toInt()
    }

    jar {
        manifest {
            attributes(
                // Forge finds mixin configs through this manifest attribute; it
                // ignores a [[mixins]] table in mods.toml.
                "MixinConfigs" to "vivemonkecraft.client.mixins.json",
                "Specification-Title" to "vivemonkecraft",
                "Specification-Vendor" to "Laggyboi",
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Laggyboi",
            )
        }
    }

    withType<Jar> {
        from(rootProject.file("LICENSE")) { rename { "${it}_ViveMonkeCraft-Forge" } }
    }

    // Builds every version's jar and copies them to build/libs/<mod version>/.
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to the root build/libs/<mod version>/"
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}

// The "pack" block of pack.mcmeta for this version. Forge rejects a mod's
// resources/data without one ("Missing metadata in pack mod:vivemonkecraft").
// The one file covers both the assets (resource format) and the hoverboard
// recipe (data format), so it declares a range spanning both. Numbers are the
// pack_version from each Minecraft jar's version.json.
fun packMeta(mc: String): String {
    // Before 1.21.9: integer formats, resource to data.
    val old = mapOf(
        "1.21.4" to (46 to 61), "1.21.5" to (55 to 71), "1.21.6" to (63 to 80),
        "1.21.7" to (64 to 81), "1.21.8" to (64 to 81),
    )
    old[mc]?.let { (res, data) ->
        return "\"pack_format\": $res,\n    \"supported_formats\": [$res, $data]"
    }
    // 1.21.9+: [major, minor] formats, resource to data. The range dips below the
    // data side's first new-style format, so the old fields must be present too.
    val new = mapOf(
        "1.21.9" to ("[69, 0]" to "[88, 0]"), "1.21.10" to ("[69, 0]" to "[88, 0]"),
        "1.21.11" to ("[75, 0]" to "[94, 1]"),
        "26.1.1" to ("[84, 0]" to "[101, 1]"), "26.1.2" to ("[84, 0]" to "[101, 1]"),
        "26.2" to ("[88, 0]" to "[107, 1]"), "26.3" to ("[97, 0]" to "[121, 0]"),
    )
    val (res, data) = new.getValue(mc)
    val resMajor = res.substringAfter("[").substringBefore(",")
    val dataMajor = data.substringAfter("[").substringBefore(",")
    return "\"pack_format\": $resMajor,\n    \"supported_formats\": [$resMajor, $dataMajor],\n" +
            "    \"min_format\": $res,\n    \"max_format\": $data"
}
