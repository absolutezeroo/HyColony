plugins { id("hy.java-core") }

// HyAngler's pure-Java core (spec 2026-10-04-hyangler-design § 3): conditions, catalogue, rolls, casts, no Hytale.
group = "dev.hyangler"

dependencies {
    // The core implements the public api and hands out its types.
    api(project(":angler-api"))
}
