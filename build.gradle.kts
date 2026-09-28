plugins { id("hy.workspace") }

// CLAUDE.md § 1, § 8: every check lives in build-logic's conventions. A project that applies neither hy.java-core nor
// hy.hytale-mod would escape them all, so it fails the build.
gradle.projectsEvaluated {
    val bare = subprojects.filter { p -> listOf("hy.java-core", "hy.hytale-mod").none { p.pluginManager.hasPlugin(it) } }
    if (bare.isNotEmpty()) {
        throw GradleException("Projects without a hy.java-core or hy.hytale-mod convention (CLAUDE.md § 1):\n" +
            bare.joinToString("\n") { "  ${it.path}" })
    }
}
