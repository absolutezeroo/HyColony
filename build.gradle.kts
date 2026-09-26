plugins {
    id("com.diffplug.spotless") version "8.10.3" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "pmd")

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

// CLAUDE.md § 3: formatting is checked by spotlessCheck (part of check). JSON, .ui and .lang are left alone.
subprojects {
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            palantirJavaFormat("2.99.0")
            removeUnusedImports()
            importOrder("""\#""", "") // Google Java Style: statics, blank line, the rest.
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}

// PMD on main sources. PMD has no baseline: config/pmd/known-violations.txt lists "Rule path" pairs
// that are suppressed (violationSuppressXPath on the file's top-level type). This list may only shrink.
val pmdRuleset = file("config/pmd/ruleset.xml")
val pmdKnownViolations = file("config/pmd/known-violations.txt")

fun pmdRulesetWithBaseline(): String {
    val entries = pmdKnownViolations.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    var ruleset = pmdRuleset.readText()
    entries.map { it.split(Regex("""\s+"""), 2) }.groupBy({ it[0] }, { it[1] }).forEach { (rule, paths) ->
        val matches = paths.joinToString(" or ") { path ->
            val type = path.substringAfter("/src/main/java/").removeSuffix(".java")
            "(@PackageName='${type.substringBeforeLast('/').replace('/', '.')}' and */@SimpleName='${type.substringAfterLast('/')}')"
        }
        val property = "<property name=\"violationSuppressXPath\" value=\"/CompilationUnit[$matches]\"/>"
        val anchor = Regex("""(ref="category/java/\w+\.xml/$rule">\s*<properties)(/?)>""")
        val found = anchor.find(ruleset) ?: throw GradleException("$pmdKnownViolations: rule $rule is not in $pmdRuleset")
        val closing = if (found.groupValues[2] == "/") "</properties>" else ""
        ruleset = ruleset.replaceRange(found.range, "${found.groupValues[1]}>$property$closing")
    }
    return ruleset
}

subprojects {
    extensions.configure<PmdExtension> {
        toolVersion = "7.28.0"
        ruleSets = emptyList()
        ruleSetConfig = resources.text.fromString(pmdRulesetWithBaseline())
        isConsoleOutput = true
        isIgnoreFailures = false
    }
    tasks.named("pmdTest") { enabled = false }
    tasks.withType<Pmd>().configureEach { inputs.files(pmdRuleset, pmdKnownViolations) }
}
