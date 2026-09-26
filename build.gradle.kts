subprojects {
    apply(plugin = "java")

    group = rootProject.property("group").toString()
    version = rootProject.property("version").toString()

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(rootProject.property("java_version").toString().toInt()))
    }

    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
    tasks.withType<Javadoc>().configureEach {
        (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
    }
}

// CLAUDE.md § 2: no source file over MAX_LINES. The allowlist only shrinks (files being split).
val checkFileSizes by tasks.registering {
    group = "verification"
    description = "Fails when a main source file exceeds the size limit of CLAUDE.md"
    val maxLines = 400
    val allowlist = file("gradle/file-size-allowlist.txt")
    val sources = fileTree(rootDir) { include("core/src/main/java/**/*.java", "plugin/src/main/java/**/*.java") }
    inputs.files(sources, allowlist)
    doLast {
        val allowed = allowlist.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val tooBig = sources.files
            .map { it.relativeTo(rootDir).invariantSeparatorsPath to it.readLines().size }
            .filter { (path, lines) -> lines > maxLines && path !in allowed }
        if (tooBig.isNotEmpty()) {
            throw GradleException("Files over $maxLines lines (CLAUDE.md § 2), split them:\n" +
                tooBig.joinToString("\n") { (path, lines) -> "  $path: $lines" })
        }
    }
}
subprojects { tasks.matching { it.name == "check" }.configureEach { dependsOn(checkFileSizes) } }
