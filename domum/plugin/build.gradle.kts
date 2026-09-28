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
