plugins { id("hy.java-core") }

dependencies {
    // The core implements the public api and hands out its types.
    api(project(":api"))
}
