// A pure-Java core (CLAUDE.md § 1): no Hytale, Gson provided at runtime by the server, JUnit and ArchUnit for tests.
plugins {
    id("hy.java-checks")
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(libs.findLibrary("gson").get())
    testImplementation(libs.findLibrary("gson").get())
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
    // ArchUnit 1.4.1+ reads Java 25 class files (version 69).
    testImplementation(libs.findLibrary("archunit").get())
}

tasks.test { useJUnitPlatform() }
