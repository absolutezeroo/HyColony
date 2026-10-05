import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

// CLAUDE.md §§ 1-3, 8: the checks every Java project of the workspace runs, applied through hy.java-core or
// hy.hytale-mod. They check what the root build.gradle.kts checked before build-logic, project by project.
plugins {
    java
    pmd
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

repositories { mavenCentral() }

java { toolchain.languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java_version").get().toInt())) }

val libs = the<VersionCatalogsExtension>().named("libs")

// Error Prone on main sources: its ERROR-level checks fail the build, NullAway (JSpecify mode) included. A value
// that may be null carries jspecify's @Nullable.
dependencies {
    "errorprone"("com.google.errorprone:error_prone_core:2.50.0")
    "errorprone"("com.uber.nullaway:nullaway:0.14.2")
    "compileOnly"(libs.findLibrary("jspecify").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xmaxwarns", "10000"))
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        option(
            "NullAway:AnnotatedPackages",
            "dev.hycolony,dev.hydomum,dev.hyblockui,dev.hyvanilla,dev.hylens,dev.hyangler",
        )
        option("NullAway:JSpecifyMode", "true")
        check("NullAway", CheckSeverity.ERROR)
    }
}
tasks.named<JavaCompile>("compileTestJava") { options.errorprone.enabled.set(false) }
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
}

val root: File = rootDir

// CLAUDE.md § 2: no source file over 400 lines; § 1: no package over 15 files. The allowlists only shrink.
val checkFileSizes by tasks.registering {
    group = "verification"
    description = "Fails when a main source file exceeds the size limit of CLAUDE.md"
    val maxLines = 400
    val maxFilesPerPackage = 15
    val allowlist = rootProject.file("gradle/file-size-allowlist.txt")
    val packageAllowlist = rootProject.file("gradle/package-size-allowlist.txt")
    val sources = fileTree("src/main/java") { include("**/*.java") }
    inputs.files(sources, allowlist, packageAllowlist)
    doLast {
        fun entries(file: File) = file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val allowed = entries(allowlist)
        val tooBig = sources.files
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readLines().size }
            .filter { (path, lines) -> lines > maxLines && path !in allowed }
        val allowedPackages = entries(packageAllowlist)
        val crowded = sources.files.groupBy { it.parentFile.relativeTo(root).invariantSeparatorsPath }
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
    val sources = fileTree("src") { include("*/java/**/*.java") }
    inputs.files(sources)
    doLast {
        val found = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().withIndex().filter { divider.containsMatchIn(it.value) }
                .map { "  ${file.relativeTo(root).invariantSeparatorsPath}:${it.index + 1}: ${it.value.trim()}" }
        }
        if (found.isNotEmpty()) {
            throw GradleException("Section-divider comments (CLAUDE.md § 3), remove them or split the class:\n" +
                found.joinToString("\n"))
        }
    }
}

// CLAUDE.md § 3: 120 columns, comments included. palantir-java-format wraps code but leaves Javadoc and comments as
// written, so spotlessCheck alone lets a long comment through.
val maxLineLength = 120
val checkLineLength by tasks.registering {
    group = "verification"
    description = "Fails when a Java source line is longer than $maxLineLength columns"
    val sources = fileTree("src") { include("*/java/**/*.java") }
    inputs.files(sources)
    doLast {
        val found = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().withIndex().filter { it.value.length > maxLineLength }
                .map { "  ${file.relativeTo(root).invariantSeparatorsPath}:${it.index + 1} (${it.value.length})" }
        }
        if (found.isNotEmpty()) {
            throw GradleException("Lines over $maxLineLength columns (CLAUDE.md § 3), wrap them:\n" +
                found.joinToString("\n"))
        }
    }
}

// CLAUDE.md § 1 (split spec, mods' APIs): a mod reaches another mod only through that mod's api packages. A fully
// qualified name written without an import escapes this check; the style's explicit imports make that rare. A mod
// is known by its projects' Maven group, so a source file must sit in its project's group: a file of HyLens written
// in a dev.hycolony package would otherwise pass as HyColony's own.
val modApis = mapOf(
    "dev.hyblockui." to listOf("dev.hyblockui.api."),
    "dev.hydomum." to listOf("dev.hydomum.api.", "dev.hydomum.plugin.api."),
    "dev.hyvanilla." to listOf("dev.hyvanilla.api.", "dev.hyvanilla.plugin.api."),
    "dev.hycolony." to listOf("dev.hycolony.api.", "dev.hycolony.plugin.api."),
    "dev.hylens." to emptyList(),
    "dev.hyangler." to listOf("dev.hyangler.api.", "dev.hyangler.plugin.api."),
)
// The mod this project belongs to: its Maven group (dev.hycolony, dev.hydomum, dev.hyblockui, dev.hyvanilla,
// dev.hylens or dev.hyangler), read when the task runs, since a module's build script sets its group after this
// convention is applied.
val ownMod = provider { "${project.group}." }
val checkModApis by tasks.registering {
    group = "verification"
    description = "Fails when a source file imports another mod outside that mod's api packages, or leaves its group"
    val sources = fileTree("src") { include("*/java/**/*.java") }
    inputs.files(sources)
    doLast {
        val own = ownMod.get()
        val import = Regex("""^\s*import\s+(?:static\s+)?([\w.]+)""")
        val pkg = Regex("""^\s*package\s+([\w.]+)\s*;""")
        val found = sources.files.sortedBy { it.path }.flatMap { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            val lines = file.readLines()
            val declared = lines.firstNotNullOfOrNull { pkg.find(it)?.groupValues?.get(1) }
            // A module-info.java has no package.
            val outside = if (file.name == "module-info.java" || declared != null && "$declared.".startsWith(own))
                emptyList()
                else listOf("  $path: package ${declared ?: "(none)"} is outside the group ${own.dropLast(1)}")
            outside + lines.withIndex().mapNotNull { (i, line) ->
                val name = import.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                val mod = modApis.keys.firstOrNull { name.startsWith(it) } ?: return@mapNotNull null
                if (mod == own || modApis.getValue(mod).any { name.startsWith(it) }) null
                else "  $path:${i + 1}: $name"
            }
        }
        if (found.isNotEmpty()) {
            throw GradleException("Imports of another mod outside its api packages, or files outside their project's " +
                "group (CLAUDE.md § 1):\n" + found.joinToString("\n"))
        }
    }
}

// CLAUDE.md § 1 (api spec § 4.3): the public signatures of HyColony's and HyAngler's apis, stable ones only, are kept
// in api.txt next to the project's build script. apiCheck (part of check) fails when the compiled classes differ from
// it; apiDump rewrites it, so every change of the api shows in the diff. What is @Experimental is left out
// (ApiSignatures.kt).
// The signatures are read by reflection in the Gradle daemon, whose JDK formats them: it must be the workspace's.
// Each project's api package, and the @Experimental annotation of its mod's api.
val apiPackages = mapOf(
    ":api" to ("dev.hycolony.api" to "dev.hycolony.api.Experimental"),
    ":plugin" to ("dev.hycolony.plugin.api" to "dev.hycolony.api.Experimental"),
    ":angler-api" to ("dev.hyangler.api" to "dev.hyangler.api.Experimental"),
    ":angler-plugin" to ("dev.hyangler.plugin.api" to "dev.hyangler.api.Experimental"),
)
apiPackages[path]?.let { (apiPackage, experimental) ->
    val apiFile = layout.projectDirectory.file("api.txt").asFile
    val classesDirs = the<SourceSetContainer>().named("main").map { it.output.classesDirs }
    val compileClasspath = configurations.named("compileClasspath")
    val projectPath = path
    val javaVersion = providers.gradleProperty("java_version").get().toInt()
    fun signatures(): List<String> {
        if (Runtime.version().feature() != javaVersion) {
            throw GradleException("The api's signatures are read with the Gradle daemon's JDK " +
                "${Runtime.version().feature()}; run Gradle on JDK $javaVersion (org.gradle.java.home).")
        }
        return apiSignatures(classesDirs.get().files, compileClasspath.get().files, apiPackage, experimental)
    }
    tasks.register("apiDump") {
        group = "api"
        description = "Writes the public signatures of $apiPackage to api.txt"
        dependsOn("classes")
        outputs.file(apiFile)
        // Its input is the compiled api and its whole classpath: rewriting the file every time is simpler and cheap.
        outputs.upToDateWhen { false }
        doLast { apiFile.writeText(signatures().joinToString("\n", postfix = "\n")) }
    }
    val apiCheck = tasks.register("apiCheck") {
        group = "verification"
        description = "Fails when the public signatures of $apiPackage differ from api.txt"
        dependsOn("classes")
        inputs.files(classesDirs, apiFile)
        doLast {
            val expected = if (apiFile.isFile) apiFile.readLines().filter { it.isNotEmpty() } else emptyList()
            val actual = signatures()
            if (expected != actual) {
                val gone = expected - actual.toSet()
                val added = actual - expected.toSet()
                throw GradleException("The api of $apiPackage changed (CLAUDE.md § 1). Check it keeps the api's " +
                    "version policy, then run ./gradlew $projectPath:apiDump and commit api.txt:\n" +
                    (gone.map { "  - $it" } + added.map { "  + $it" }).joinToString("\n"))
            }
        }
    }
    tasks.named("check") { dependsOn(apiCheck) }
}

tasks.named("check") { dependsOn(checkFileSizes, checkSectionDividers, checkLineLength, checkModApis) }

// CLAUDE.md § 3: formatting is checked by spotlessCheck (part of check). JSON, .ui and .lang are left alone.
spotless {
    java {
        palantirJavaFormat("2.99.0")
        removeUnusedImports()
        importOrder("""\#""", "") // Google Java Style: statics, blank line, the rest.
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// PMD on main sources. PMD has no baseline: config/pmd/known-violations.txt lists "Rule path" pairs whose violations
// are tolerated. pmdMain reads its XML report and fails on any other violation, and on a pair that no longer matches
// a violation, so the list is forced to shrink (CLAUDE.md § 8).
val pmdRuleset = rootProject.file("config/pmd/ruleset.xml")
val pmdKnownViolations = rootProject.file("config/pmd/known-violations.txt")
val pmdProjectPaths = rootProject.subprojects.map { it.projectDir.relativeTo(root).invariantSeparatorsPath }

fun elements(doc: org.w3c.dom.Document, tag: String): List<org.w3c.dom.Element> =
    doc.getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element } }

// PMD logs a ruleset it cannot load and then writes an empty report, which would pass: load it here and fail instead.
fun checkPmdRulesetLoads(classpath: Set<File>) {
    // PMD alone, but on Gradle's SLF4J API: the pmd configuration relies on Gradle for it.
    val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
    object : java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> =
            if (name.startsWith("org.slf4j.")) Project::class.java.classLoader.loadClass(name)
            else super.loadClass(name, resolve)
    }.use { loader ->
        val rulesetLoader = loader.loadClass("net.sourceforge.pmd.lang.rule.RuleSetLoader")
        try {
            rulesetLoader.getMethod("loadFromResource", String::class.java)
                .invoke(rulesetLoader.getConstructor().newInstance(), pmdRuleset.absolutePath)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw GradleException("PMD cannot load $pmdRuleset: ${e.cause?.message}", e.cause)
        }
    }
}

fun checkPmdBaseline(report: File, projectPath: String) {
    val entries = pmdKnownViolations.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.split(Regex("""\s+"""), 2) }.map { it[0] to it.getOrElse(1) { "" } }
    val orphans = entries.filter { (_, path) -> pmdProjectPaths.none { path.startsWith("$it/") } }
    if (orphans.isNotEmpty()) {
        throw GradleException("$pmdKnownViolations lists files outside every subproject, remove the lines:\n" +
            orphans.joinToString("\n") { "  ${it.first} ${it.second}" })
    }
    val known = entries.filter { it.second.startsWith("$projectPath/") }.toSet()
    val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report)
    // A file PMD could not analyse, or a broken rule, reports nothing: fail rather than pass (or call a line stale).
    val errors = elements(doc, "error").map { "  ${it.getAttribute("filename")}: ${it.getAttribute("msg")}" } +
        elements(doc, "configerror").map { "  rule ${it.getAttribute("rule")}: ${it.getAttribute("msg")}" }
    if (errors.isNotEmpty()) {
        throw GradleException("PMD could not run every rule on every file:\n" + errors.joinToString("\n"))
    }
    val found = elements(doc, "file").flatMap { file ->
        val path = File(file.getAttribute("name")).relativeTo(root).invariantSeparatorsPath
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

pmd {
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
    val projectPath = projectDir.relativeTo(root).invariantSeparatorsPath
    val pmdClasspath = configurations.named("pmd")
    doLast { checkPmdRulesetLoads(pmdClasspath.get().files) }
    doLast { checkPmdBaseline(report.get().asFile, projectPath) }
}
