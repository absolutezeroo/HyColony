plugins { id("hy.hytale-mod") }

// HyAngler, a fishing mod for Hytale with an api for other mods (spec 2026-10-04-hyangler-design).
group = "dev.hyangler"

dependencies {
    // The api and the pure core ship inside the plugin's jar: the only copy of the api other mods see.
    bundled(project(":angler-api"))
    bundled(project(":angler-core"))
    compileOnly(libs.gson)
}

hytaleTools {
    modId = "hyangler"
    mainClass = "dev.hyangler.plugin.HyAnglerPlugin"
    modDescription = "Fishing for Hytale: rods, bobbers, fish by environment and hour, and an api for other mods."
    modCredits = project.property("mod_author").toString()
    // Beam and Weather are built-in plugins HyAngler calls from Java (fishing-hytale.md § 5.3, § 5.6).
    manifestDependencies = "Hytale:AssetModule=*,Hytale:Beam=*,Hytale:Weather=*"
    manifestOptionalDependencies = ""
}

tasks.named<Jar>("jar") { archiveBaseName.set("HyAngler") }

// Hytale shuts the whole server down when a pack holds an invalid asset: check HyAngler's own before it ships.
val checkPackAssets by tasks.registering(CheckPackAssets::class) {
    packs.from(layout.projectDirectory.dir("src/main/resources"))
    namespace.set("HyAngler")
    stamp.set(layout.buildDirectory.file("tmp/checkPackAssets.stamp"))
    // The pack is the mod's resources, where hytale-tools writes manifest.json.
    mustRunAfter("createManifestIfMissing", "updatePluginManifest")
}
tasks.named("processResources") { dependsOn(checkPackAssets) }
