plugins { id("hy.java-core") }

// HyLens's pure-Java core (spec 2026-09-30-hycolony-api-hylens-design § 3): what its HUD, menu and drawings show,
// built from HyColony's api snapshots, no Hytale.
group = "dev.hylens"

dependencies {
    // HyColony's api: HyColony's jar provides it at runtime, HyLens never ships a copy.
    compileOnly(project(":api"))
    testImplementation(project(":api"))
}
