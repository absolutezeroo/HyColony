plugins { id("hy.hytale-mod") }

// HyDomum, the port of Domum Ornamentum (split spec § Identité de chaque mod).
group = "dev.hydomum"

dependencies {
    // Its own pure core ships inside its jar.
    bundled(project(":domum-core"))
    // Another mod: compiled against, never shipped (plugin-b-api.md § 28.2).
    compileOnly(project(":blockui"))
    compileOnly(libs.gson)
}

hytaleTools {
    modId = "hydomum"
    mainClass = "dev.hydomum.plugin.HyDomumPlugin"
    modDescription = "Domum Ornamentum for Hytale: architect's blocks in any materials, cut at the architect's cutter."
    modCredits = project.property("mod_author").toString()
    manifestDependencies = "Hytale:AssetModule=*,HyColony:hyblockui==0.1.0"
    manifestOptionalDependencies = ""
}

tasks.named<Jar>("jar") { archiveBaseName.set("HyDomum") }

// Hytale shuts the whole server down when a pack holds an invalid asset: check HyDomum's own before it ships.
val checkPackAssets by tasks.registering(CheckPackAssets::class) {
    packs.from(layout.projectDirectory.dir("src/main/resources"))
    namespace.set("HyDomum")
    stamp.set(layout.buildDirectory.file("tmp/checkPackAssets.stamp"))
    // The pack is the mod's resources, where hytale-tools writes manifest.json.
    mustRunAfter("createManifestIfMissing", "updatePluginManifest")
}
// Before anything packs or serves the resources (jar, runAllMods), as the sub-packs' check ran before their zips.
tasks.named("processResources") { dependsOn(checkPackAssets) }
