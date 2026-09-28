import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("com.diffplug.spotless") version "8.10.3" apply false
    id("net.ltgt.errorprone") version "5.1.1" apply false
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

    // Error Prone on main sources: its ERROR-level checks fail the build, NullAway (JSpecify mode) included. A value
    // that may be null carries jspecify's @Nullable.
    apply(plugin = "net.ltgt.errorprone")
    dependencies {
        "errorprone"("com.google.errorprone:error_prone_core:2.50.0")
        "errorprone"("com.uber.nullaway:nullaway:0.14.2")
        "compileOnly"("org.jspecify:jspecify:1.0.1")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xmaxwarns", "10000"))
        options.errorprone {
            disableWarningsInGeneratedCode.set(true)
            option("NullAway:AnnotatedPackages", "dev.hycolony")
            option("NullAway:JSpecifyMode", "true")
            check("NullAway", CheckSeverity.ERROR)
        }
    }
    tasks.named<JavaCompile>("compileTestJava") { options.errorprone.enabled.set(false) }
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
    inputs.files(sources, allowlist, file("gradle/package-size-allowlist.txt"))
    doLast {
        val allowed = allowlist.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val tooBig = sources.files
            .map { it.relativeTo(rootDir).invariantSeparatorsPath to it.readLines().size }
            .filter { (path, lines) -> lines > maxLines && path !in allowed }
        val maxFilesPerPackage = 15
        val allowedPackages = file("gradle/package-size-allowlist.txt").readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val crowded = sources.files.groupBy { it.parentFile.relativeTo(rootDir).invariantSeparatorsPath }
            .filter { (dir, files) -> files.size > maxFilesPerPackage && dir !in allowedPackages }
        if (crowded.isNotEmpty()) {
            throw GradleException("Packages over $maxFilesPerPackage files (CLAUDE.md § 1), split them into sub-packages:\n" +
                crowded.entries.joinToString("\n") { (dir, files) -> "  $dir: ${files.size}" })
        }
        if (tooBig.isNotEmpty()) {
            throw GradleException("Files over $maxLines lines (CLAUDE.md § 2), split them:\n" +
                tooBig.joinToString("\n") { (path, lines) -> "  $path: $lines" })
        }
    }
}
// CLAUDE.md § 3: no section-divider comments ("// ---- section ----", "/* ---- */", or a " * ----" line in a block):
// a class that needs sections must be split.
val checkSectionDividers by tasks.registering {
    group = "verification"
    description = "Fails when a Java source file contains a section-divider comment"
    val divider = Regex("""^\s*(//|/\*+|\*)\s*[-=*]{3,}""")
    val sources = fileTree(rootDir) { include("core/src/*/java/**/*.java", "plugin/src/*/java/**/*.java") }
    inputs.files(sources)
    doLast {
        val found = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().withIndex().filter { divider.containsMatchIn(it.value) }
                .map { "  ${file.relativeTo(rootDir).invariantSeparatorsPath}:${it.index + 1}: ${it.value.trim()}" }
        }
        if (found.isNotEmpty()) {
            throw GradleException("Section-divider comments (CLAUDE.md § 3), remove them or split the class:\n" +
                found.joinToString("\n"))
        }
    }
}
subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkFileSizes, checkSectionDividers) }
}

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

// PMD on main sources. PMD has no baseline: config/pmd/known-violations.txt lists "Rule path" pairs whose violations
// are tolerated. pmdMain reads its XML report and fails on any other violation, and on a pair that no longer matches
// a violation, so the list is forced to shrink (CLAUDE.md § 8).
val pmdRuleset = file("config/pmd/ruleset.xml")
val pmdKnownViolations = file("config/pmd/known-violations.txt")

fun checkPmdBaseline(report: File, projectPath: String) {
    val known = pmdKnownViolations.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.split(Regex("""\s+"""), 2) }.map { it[0] to it[1] }.filter { it.second.startsWith("$projectPath/") }
        .toSet()
    val files = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report)
        .getElementsByTagName("file")
    val found = (0 until files.length).map { files.item(it) as org.w3c.dom.Element }.flatMap { file ->
        val path = File(file.getAttribute("name")).relativeTo(rootDir).invariantSeparatorsPath
        val violations = file.getElementsByTagName("violation")
        (0 until violations.length).map { violations.item(it) as org.w3c.dom.Element }.map {
            Triple(it.getAttribute("rule") to path, it.getAttribute("beginline"), it.textContent.trim())
        }
    }
    val unknown = found.filter { it.first !in known }
    val stale = known - found.map { it.first }.toSet()
    if (unknown.isNotEmpty()) {
        throw GradleException("PMD violations (config/pmd/ruleset.xml), fix them:\n" +
            unknown.joinToString("\n") { (key, line, message) -> "  ${key.second}:$line ${key.first}: $message" })
    }
    if (stale.isNotEmpty()) {
        throw GradleException("$pmdKnownViolations lists violations that are gone (CLAUDE.md § 8), remove the lines:\n" +
            stale.sortedBy { "${it.first} ${it.second}" }.joinToString("\n") { "  ${it.first} ${it.second}" })
    }
}

subprojects {
    extensions.configure<PmdExtension> {
        toolVersion = "7.28.0"
        ruleSets = emptyList()
        ruleSetConfig = resources.text.fromFile(pmdRuleset)
        isConsoleOutput = false
        isIgnoreFailures = true // checkPmdBaseline decides
    }
    tasks.named("pmdTest") { enabled = false }
    tasks.named<Pmd>("pmdMain") {
        inputs.files(pmdRuleset, pmdKnownViolations)
        reports.xml.required = true
        val report = reports.xml.outputLocation
        val projectPath = projectDir.relativeTo(rootDir).invariantSeparatorsPath
        doLast { checkPmdBaseline(report.get().asFile, projectPath) }
    }
}
