plugins { id("hy.java-core") }

// HyAngler's pure-Java core (spec 2026-10-04-hyangler-design § 3): conditions, catalogue, rolls, casts, no Hytale.
group = "dev.hyangler"

dependencies {
    // The core implements the public api and hands out its types.
    api(project(":angler-api"))
}

// OurDataFilesTest reads the plugin's data files: a changed file must run the tests again.
tasks.test {
    inputs.dir("../plugin/src/main/resources/Server/HyAngler")
        .withPropertyName("anglerData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
