plugins { id("hy.hytale-mod") }

// HyVanilla, Minecraft's vanilla blocks that Hytale lacks (spec 2026-09-29-hyvanilla-design).
group = "dev.hyvanilla"

dependencies {
    // Its own pure core ships inside its jar.
    bundled(project(":vanilla-core"))
    compileOnly(libs.gson)
}

hytaleTools {
    modId = "hyvanilla"
    mainClass = "dev.hyvanilla.plugin.HyVanillaPlugin"
    modDescription = "Minecraft's vanilla blocks that Hytale lacks: carpets and flower pots."
    modCredits = project.property("mod_author").toString()
    manifestDependencies = "Hytale:AssetModule=*"
    manifestOptionalDependencies = ""
}

tasks.named<Jar>("jar") { archiveBaseName.set("HyVanilla") }

// Hytale shuts the whole server down when a pack holds an invalid asset: check HyVanilla's own before it ships.
val checkPackAssets by tasks.registering(CheckPackAssets::class) {
    packs.from(layout.projectDirectory.dir("src/main/resources"))
    namespace.set("HyVanilla")
    stamp.set(layout.buildDirectory.file("tmp/checkPackAssets.stamp"))
    // The pack is the mod's resources, where hytale-tools writes manifest.json.
    mustRunAfter("createManifestIfMissing", "updatePluginManifest")
}
tasks.named("processResources") { dependsOn(checkPackAssets) }
