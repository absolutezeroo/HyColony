// CLAUDE.md § 10: the build checks and their tools' versions (a guardrail).
plugins { `kotlin-dsl` }

dependencies {
    // Workspace and mods must share this classpath: HytaleWorkspacePlugin looks up each mod's HytaleExtension.
    implementation("com.azuredoom.gradle:hytale-gradle-plugin:1.0.51")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.3")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.1")
}
