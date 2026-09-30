plugins { id("hy.hytale-mod") }

// HyLens, the debugging mod that sees HyColony through its api only (spec 2026-09-30-hycolony-api-hylens-design).
group = "dev.hylens"

dependencies {
    // Its own pure core ships inside its jar.
    bundled(project(":hylens-core"))
    // Other mods: compiled against, never shipped (plugin-b-api.md § 28.2). checkModApis keeps HyLens to their api
    // packages.
    compileOnly(project(":api"))
    compileOnly(project(":plugin"))
    compileOnly(project(":blockui"))
    compileOnly(libs.gson)
}

hytaleTools {
    modId = "hylens"
    mainClass = "dev.hylens.plugin.HyLensPlugin"
    modDescription = "Debugging lens on HyColony's colonies: what a citizen thinks, where it walks, what breaks."
    modCredits = project.property("mod_author").toString()
    // Exact versions: HyLens is built against these very jars (spec § 2).
    manifestDependencies =
        "Hytale:AssetModule=*,HyColony:hycolony==${project.version},HyColony:hyblockui==${project.version}"
    manifestOptionalDependencies = ""
}

tasks.named<Jar>("jar") { archiveBaseName.set("HyLens") }
