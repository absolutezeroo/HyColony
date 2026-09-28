# Séparation en trois mods, plan 2 : `build-logic`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** déplacer tous les contrôles du build dans un build inclus `build-logic` (conventions `hy.workspace`, `hy.java-core`, `hy.hytale-mod`), passer le serveur de dev à `runAllMods` dans `run/` à la racine, sans changer le comportement de `core` ni de `plugin`.

**Architecture :**
- `build-logic/` est un build inclus (`pluginManagement { includeBuild("build-logic") }`). Il contient des plugins de convention écrits en scripts Kotlin précompilés :
  - `hy.java-checks`, la base commune : Java 25, Error Prone et NullAway, Spotless, PMD, tailles, séparateurs, `checkModApis` ;
  - `hy.java-core`, pour un cœur Java pur ;
  - `hy.hytale-mod`, pour un mod ;
  - `hy.workspace`, pour la racine.
- Le `build.gradle.kts` racine n'applique plus que `hy.workspace`. Il garde un contrôle : chaque sous-projet applique `hy.java-core` ou `hy.hytale-mod`.
- Un catalogue `gradle/libs.versions.toml` porte les bibliothèques. Les versions des outils de contrôle restent dans `build-logic/` (protégé).

**Tech Stack :** Gradle 9.5.1 (`kotlin-dsl`), plugin AzureDoom 1.0.51, Spotless 8.10.3 (palantir 2.99.0), Error Prone 2.50.0, NullAway 0.14.2, PMD 7.28.0, Java 25.

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-split-hydomum-hyblockui-design.md` (§ « Organisation », « API des mods » : `checkModApis`, « Config et données en dev », « Garde-fous », « Plans » point 2). Résultats de l'essai : `docs/research/plugin-b-api.md` § 28.

## Contraintes globales

- Tâches 1 et 2 : **accord explicite de l'utilisateur** et session lancée avec `HYCOLONY_GUARDRAILS_UNLOCKED=1` (elles écrivent `guard.js`, `.githooks/`, `CLAUDE.md`, `AGENTS.md`, `.claude/agents/`, le `build.gradle.kts` racine et `build-logic/`). Sans ces deux conditions, s'arrêter et le demander.
- On ne lance **jamais** `runServer`, `runAllMods` ni `HytaleServer.jar` (CLAUDE.md § 9.4). `stageAllModAssets` est permis.
- Les contrôles vérifient **exactement** ce qu'ils vérifient aujourd'hui : mêmes limites (400 lignes, 15 fichiers par paquet), mêmes règles PMD, même formatage, mêmes options Error Prone/NullAway. Seul `NullAway:AnnotatedPackages` s'élargit à `dev.hycolony,dev.hydomum,dev.hyblockui` (spec).
- Les trois listes d'exceptions (`gradle/file-size-allowlist.txt`, `gradle/package-size-allowlist.txt`, `config/pmd/known-violations.txt`) ne changent pas.
- Plugin AzureDoom épinglé en **1.0.51**.
- `./gradlew build` vert avant chaque commit ; relecture indépendante de chaque tâche ; commits `type(scope): description` avec `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`, `git add` de chemins explicites, jamais `config.json`.
- Une autre session peut travailler dans le même dossier : `git status` avant chaque `git add`, et n'indexer que ses propres fichiers.

## Review Focus

- **Un contrôle qui ne mord plus après le déplacement** (tâche de taille, séparateurs, PMD, Error Prone) : tâche 2, étape 9, qui casse chacun exprès et vérifie l'échec.
- **Un sous-projet sans convention** doit faire échouer le build : tâche 2, étape 9, cas 5.
- **Un import d'un autre mod hors de son `api`** doit faire échouer `checkModApis` : tâche 2, étape 9, cas 6.
- **Le jar du plugin qui change de contenu** au-delà du retrait de `asseteditor` : tâche 2, étape 8, comparaison des listes.
- **`stageAllModAssets` sans les sous-packs générés** (dossier `build/generated/subplugins` absent) : tâche 3, étape 1.

## Écart à la spec, décidé en écrivant ce plan

La spec demandait de rediriger la sortie de `decompileServerJar` vers `build/vineflower` à la racine. Ce n'est pas faisable proprement : le plugin AzureDoom capture ce chemin dans une variable et la réutilise dans deux autres tâches (`HytaleIdeSourceConfigurer.groovy` l. 33, 64, 92-93 et 168). Changer seulement `outputDirectory` les désynchroniserait. Les sources décompilées de référence restent donc `build/vineflower/hytale-server` à la racine, qui existe déjà et ne change pas tant que Hytale reste épinglé en 0.6.8. Si ce dossier manque, on lance `./gradlew :plugin:decompileServerJar`, qui écrit `plugin/build/vineflower/hytale-server`. La tâche 4 écrit cet écart dans la spec.

## Fichiers

- Créer : `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts`, `build-logic/src/main/kotlin/hy.java-checks.gradle.kts`, `hy.java-core.gradle.kts`, `hy.hytale-mod.gradle.kts`, `hy.workspace.gradle.kts`, `gradle/libs.versions.toml`.
- Modifier : `settings.gradle.kts`, `build.gradle.kts` (racine, réécrit), `core/build.gradle.kts`, `plugin/build.gradle.kts` (l. 1-43 et 127-131).
- Garde-fous : `.claude/hooks/guard.js`, `.claude/hooks/test/commands.txt`, `.githooks/pre-commit`, `CLAUDE.md`, `AGENTS.md`, `.claude/agents/hycolony-implementer.md`.
- Docs : `.gitignore`, `docs/TESTING.md` (l. 3 et point 59), `README.md` (l. 24), `docs/UPGRADING.md` (l. 8), la spec.

---

### Tâche 1 : garde-fous pour `build-logic/` et les futures configs

**Préalable :** accord explicite et session déverrouillée.

**Files :**
- Modify : `.claude/hooks/guard.js:20-38` (`PROTECTED`)
- Modify : `.claude/hooks/test/commands.txt`
- Modify : `.githooks/pre-commit:4`
- Modify : `CLAUDE.md` § 10, `AGENTS.md`, `.claude/agents/hycolony-implementer.md:32`

**Interfaces :**
- Produit : `build-logic/` est un garde-fou (écriture refusée hors session déverrouillée) ; `blockui/src/main/resources/config.json`, `domum/plugin/src/main/resources/config.json` et leurs `.bak` sont des fichiers locaux (écriture et indexation refusées), comme `plugin/src/main/resources/config.json`.

- [ ] **Étape 1 : écrire les cas du banc**

Ajouter à la fin de `.claude/hooks/test/commands.txt` :

```
// build-logic/ holds the build checks (split plan 2): a guardrail like the root build.gradle.kts.
deny Bash echo x > build-logic/build.gradle.kts
deny Bash rm -rf build-logic
deny Bash sed -i s/a/b/ build-logic/src/main/kotlin/hy.java-checks.gradle.kts
allow unlocked Bash echo x > build-logic/build.gradle.kts
allow Bash cat build-logic/src/main/kotlin/hy.java-checks.gradle.kts
// Each mod's local dev config (split spec, dev config) is never written nor staged.
deny Bash echo x > blockui/src/main/resources/config.json
deny Bash echo x > domum/plugin/src/main/resources/config.json.bak
deny Bash git add domum/plugin/src/main/resources/config.json
deny unlocked Bash git add blockui/src/main/resources/config.json
allow Bash cat domum/plugin/src/main/resources/config.json
```

Et, dans le bloc des outils de fichiers de `.claude/hooks/test/run.js` s'il en existe un pour `plugin/src/main/resources/config.json` (chercher `resources/config.json` dans `run.js`), ajouter les mêmes cas `Write` pour `build-logic/build.gradle.kts` (refusé verrouillé, permis déverrouillé) et `blockui/src/main/resources/config.json` (refusé dans les deux cas), en copiant la forme des cas existants.

- [ ] **Étape 2 : vérifier l'échec**

Run : `node .claude/hooks/test/run.js`
Attendu : ÉCHEC sur les nouveaux `deny` (acceptés à tort), aucun autre.

- [ ] **Étape 3 : protéger les chemins**

Dans `PROTECTED` de `.claude/hooks/guard.js`, après `["plugin/src/main/resources/config.json.bak", LOCAL],` :

```js
    ["blockui/src/main/resources/config.json", LOCAL],
    ["blockui/src/main/resources/config.json.bak", LOCAL],
    ["domum/plugin/src/main/resources/config.json", LOCAL],
    ["domum/plugin/src/main/resources/config.json.bak", LOCAL],
```

et après `["config/pmd/ruleset.xml", GUARD],` :

```js
    ["build-logic/", GUARD],
```

- [ ] **Étape 4 : relancer le banc**

Run : `node .claude/hooks/test/run.js`
Attendu : ALL PASS.

- [ ] **Étape 5 : le hook git surveille `build-logic/`**

`.githooks/pre-commit`, ligne 4 : `grep -E '\.(java|kts)$|^gradle/|^config/|(^|/)gradle\.properties$'` devient `grep -E '\.(java|kts|kt)$|^gradle/|^config/|^build-logic/|(^|/)gradle\.properties$'`.

- [ ] **Étape 6 : les listes recopiées**

- `CLAUDE.md` § 10, phrase « Fichiers garde-fous (…) : … `config/pmd/ruleset.xml` et les contrôles du `build.gradle.kts` racine (à partir de son premier commentaire `// CLAUDE.md §`). » : ajouter `build-logic/` avant `config/pmd/ruleset.xml`.
- `CLAUDE.md` § 10, puce du hook `pre-commit` : « si un fichier `.java`, `.kts`, `gradle.properties`, `gradle/` ou `config/` est indexé » devient « si un fichier `.java`, `.kts`, `.kt`, `gradle.properties`, `gradle/`, `config/` ou `build-logic/` est indexé ».
- `CLAUDE.md` § 10, puce « l'écriture et l'indexation de `.mcp.json`, `config.json`, … » : inchangée (elle parle de `config.json` en général).
- `AGENTS.md` et `.claude/agents/hycolony-implementer.md:32` : ajouter `build-logic/` à la liste des garde-fous, à côté de `config/pmd/ruleset.xml`.

- [ ] **Étape 7 : relecture indépendante, puis commit**

`hycolony-reviewer` sur les changements non commités (intention : les deux paragraphes « Interfaces » ci-dessus ; `node .claude/hooks/test/run.js` passe). Corriger, faire relire les corrections.

```bash
git add .claude/hooks/guard.js .claude/hooks/test/commands.txt .claude/hooks/test/run.js .githooks/pre-commit CLAUDE.md AGENTS.md .claude/agents/hycolony-implementer.md
git diff --cached --stat
git commit -m "$(cat <<'EOF'
chore(hooks): guard build-logic and the future mods' local configs

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

(`run.js` seulement s'il a changé.)

---

### Tâche 2 : `build-logic` et les conventions

**Préalable :** session déverrouillée (le `build.gradle.kts` racine et `build-logic/` sont protégés).

**Files :**
- Create : `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts`, `build-logic/src/main/kotlin/hy.java-checks.gradle.kts`, `build-logic/src/main/kotlin/hy.java-core.gradle.kts`, `build-logic/src/main/kotlin/hy.hytale-mod.gradle.kts`, `build-logic/src/main/kotlin/hy.workspace.gradle.kts`, `gradle/libs.versions.toml`
- Modify : `settings.gradle.kts`, `build.gradle.kts`, `core/build.gradle.kts`, `plugin/build.gradle.kts`

**Interfaces :**
- Produit, pour les plans 3 et 4 :
  - `plugins { id("hy.java-core") }` : cœur Java pur (Gson et jspecify en `compileOnly`, JUnit et ArchUnit en test) ;
  - `plugins { id("hy.hytale-mod") }` : mod Hytale. Il fournit la configuration `bundled` : ce qui y est déclaré est compilé **et** copié dans le jar ; tout le reste d'un autre mod va en `compileOnly`. Chaque mod fixe `hytaleTools { modId, mainClass, modDescription, modCredits, manifestDependencies, manifestOptionalDependencies }` et `archiveBaseName` ;
  - `checkModApis` dans chaque projet, branché sur `check` ;
  - la racine échoue si un sous-projet n'applique ni `hy.java-core` ni `hy.hytale-mod`.

- [ ] **Étape 1 : noter l'état de départ**

```bash
./gradlew build --console=plain -q
unzip -l plugin/build/libs/HyColony-0.1.0.jar | awk '{print $4}' | grep -v '/$' | sort > /tmp/jar-before.txt
cat core/build/test-results/test/*.xml | grep -o 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}' > /tmp/tests-before.txt
cat /tmp/tests-before.txt
```

(Utiliser le dossier scratchpad de la session au lieu de `/tmp` si l'environnement en donne un.)

- [ ] **Étape 2 : le catalogue**

`gradle/libs.versions.toml` :

```toml
# Libraries only; the check tools' versions live in build-logic (a guardrail, CLAUDE.md § 10).
[versions]
gson = "2.11.0"
jspecify = "1.0.1"
junit = "6.1.3"
archunit = "1.5.1"

[libraries]
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
jspecify = { module = "org.jspecify:jspecify", version.ref = "jspecify" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
archunit = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }
```

- [ ] **Étape 3 : le build inclus**

`build-logic/settings.gradle.kts` :

```kotlin
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "AzureDoom Maven"
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}

rootProject.name = "build-logic"
```

`build-logic/build.gradle.kts` :

```kotlin
// CLAUDE.md § 10: the build checks and their tools' versions (a guardrail).
plugins { `kotlin-dsl` }

dependencies {
    // Workspace and mods must share this classpath: HytaleWorkspacePlugin looks up each mod's HytaleExtension.
    implementation("com.azuredoom.gradle:hytale-gradle-plugin:1.0.51")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.3")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.1")
}
```

Si `kotlin-dsl` refuse de compiler sous le JDK 25 qui fait tourner Gradle (message sur la cible JVM), ajouter `kotlin { jvmToolchain(21) }` n'est **pas** la bonne réponse (il faudrait un JDK 21) : noter l'erreur exacte, chercher la version de Kotlin embarquée par Gradle 9.5.1 et sa cible maximale (notes de version Gradle), et le signaler avant d'aller plus loin.

- [ ] **Étape 4 : la base commune, `hy.java-checks`**

`build-logic/src/main/kotlin/hy.java-checks.gradle.kts` reprend **mot pour mot** les contrôles du `build.gradle.kts` racine actuel, par projet au lieu de tous les projets :

```kotlin
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
        option("NullAway:AnnotatedPackages", "dev.hycolony,dev.hydomum,dev.hyblockui")
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

// CLAUDE.md § 1 (split spec, mods' APIs): a mod reaches another mod only through that mod's api packages. A fully
// qualified name written without an import escapes this check; the style's explicit imports make that rare.
val modApis = mapOf(
    "dev.hyblockui." to listOf("dev.hyblockui.api."),
    "dev.hydomum." to listOf("dev.hydomum.api.", "dev.hydomum.plugin.api."),
    "dev.hycolony." to emptyList(),
)
// The mod this project belongs to: its Maven group (dev.hycolony, dev.hydomum or dev.hyblockui).
val ownMod = "${project.group}."
val checkModApis by tasks.registering {
    group = "verification"
    description = "Fails when a source file imports another mod outside that mod's api packages"
    val own = ownMod
    val sources = fileTree("src") { include("*/java/**/*.java") }
    inputs.files(sources)
    doLast {
        val import = Regex("""^\s*import\s+(?:static\s+)?([\w.]+)""")
        val found = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().withIndex().mapNotNull { (i, line) ->
                val name = import.find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                val mod = modApis.keys.firstOrNull { name.startsWith(it) } ?: return@mapNotNull null
                if (mod == own || modApis.getValue(mod).any { name.startsWith(it) }) null
                else "  ${file.relativeTo(root).invariantSeparatorsPath}:${i + 1}: $name"
            }
        }
        if (found.isNotEmpty()) {
            throw GradleException("Imports of another mod outside its api packages (CLAUDE.md § 1):\n" +
                found.joinToString("\n"))
        }
    }
}

tasks.named("check") { dependsOn(checkFileSizes, checkSectionDividers, checkModApis) }

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
```

Avant d'écrire ce fichier, comparer ligne à ligne avec le `build.gradle.kts` racine actuel (`git show HEAD:build.gradle.kts`) : toute différence autre que « par projet » (chemins `src/…` relatifs au projet au lieu de `core/src/…` et `plugin/src/…`, `AnnotatedPackages`, `checkModApis`) est une erreur de recopie. Ce fichier dépasse 200 lignes : c'est un script Gradle, hors de `checkFileSizes` (qui ne lit que `src/main/java`).

- [ ] **Étape 5 : `hy.java-core`, `hy.hytale-mod`, `hy.workspace`**

`build-logic/src/main/kotlin/hy.java-core.gradle.kts` :

```kotlin
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
```

`build-logic/src/main/kotlin/hy.hytale-mod.gradle.kts` :

```kotlin
// A Hytale mod (split spec § Organisation): hytale-tools with the workspace's shared settings. Each mod sets its own
// identity (modId, mainClass, modDescription, modCredits, manifest dependencies) and its jar's base name.
plugins {
    id("hy.java-checks")
    id("com.azuredoom.hytale-tools")
}

// What a mod declares in bundled is compiled against and copied into its jar: its own pure core, nothing else. Other
// mods are compileOnly, since Hytale's class loaders would otherwise see two copies (plugin-b-api.md § 28.2).
val bundled: Configuration by configurations.creating
configurations.named("implementation") { extendsFrom(bundled) }

fun prop(name: String) = providers.gradleProperty(name).get()

hytaleTools {
    javaVersion = prop("java_version").toInt()
    hytaleVersion = prop("hytale_version")
    manifestServerVersion = prop("manifestServerVersion")
    manifestGroup = prop("manifest_group")
    modUrl = prop("mod_url")
    curseforgeId = prop("curseforgeID")
    disabledByDefault = prop("disabled_by_default").toBoolean()
    includesPack = prop("includes_pack").toBoolean()
    patchline = prop("patchline")
    injectServerJavadocsIntoSources = prop("injectServerJavadocsIntoSources").toBoolean()
    generateAssetsBinary = prop("generateAssetsBinary").toBoolean()
    // The Asset Editor runtime is for mods that drive the editor; none of ours does.
    bundleAssetEditorRuntime = false
}

tasks.named<Jar>("jar") {
    dependsOn(bundled)
    from({ bundled.filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Local files a dev server writes next to the resources never ship: a resources config.json would be packed as is
// (plugin-b-api.md § 28.1), and packs/ holds the sub-pack zips an older dev setup extracted there.
tasks.named<ProcessResources>("processResources") { exclude("config.json", "config.json.bak", "packs/**") }
```

`build-logic/src/main/kotlin/hy.workspace.gradle.kts` :

```kotlin
import com.azuredoom.gradle.hytale.HytaleWorkspaceExtension

// The root of the workspace (split spec § Organisation): one dev server for every mod (runAllMods, in run/), the
// shared manifest group, and :plugin as the host that supplies the server jar and assets.
plugins { id("com.azuredoom.hytale-workspace") }

configure<HytaleWorkspaceExtension> {
    manifestGroup.set(providers.gradleProperty("manifest_group"))
    hytaleVersion.set(providers.gradleProperty("hytale_version"))
    patchline.set(providers.gradleProperty("patchline"))
    hostProject.set(":plugin")
}
```

Si `plugins { id("com.azuredoom.hytale-workspace") }` n'est pas résolu dans un script précompilé (pas de marqueur de plugin pour cet id dans le jar : seul `META-INF/gradle-plugins/com.azuredoom.hytale-workspace.properties`), le remplacer par `apply(plugin = "com.azuredoom.hytale-workspace")`, la forme vérifiée par l'essai (`spike/build.gradle.kts`).

- [ ] **Étape 6 : brancher les projets**

`settings.gradle.kts` : ajouter en tête de `pluginManagement { … }` la ligne `includeBuild("build-logic")`. Le reste est inchangé.

`build.gradle.kts` racine, réécrit en entier :

```kotlin
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
```

`core/build.gradle.kts`, réécrit :

```kotlin
plugins { id("hy.java-core") }
```

`plugin/build.gradle.kts` : remplacer les lignes 1 à 43 (des `plugins` jusqu'à la fin du bloc `tasks.named<Jar>("jar")`) par :

```kotlin
plugins { id("hy.hytale-mod") }

dependencies {
    // The pure core ships inside the plugin's jar: one jar to deploy.
    bundled(project(":core"))
    compileOnly(libs.gson)
}

hytaleTools {
    modId = property("mod_id").toString()
    mainClass = property("main_class").toString()
    modDescription = property("mod_description").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
}

tasks.named<Jar>("jar") { archiveBaseName.set(project.property("mod_name").toString()) }
```

et supprimer, à la fin du fichier, les trois lignes devenues inutiles (l'exclusion est dans la convention) :

```kotlin
// In dev, the plugin data directory is src/main/resources (linked as the run asset pack): keep the pack zips that
// setup() extracts there out of the jar.
tasks.processResources { exclude("packs/**") }
```

Enfin, `runAllMods` recopie les dossiers de ressources du mod, dont `build/generated/subplugins` ; ajouter après `tasks.matching { it.name == "prepareRunServer" }…` :

```kotlin
// The workspace's staging links every resource dir of the mod, the generated sub-plugins included.
rootProject.tasks.matching { it.name == "stageAllModAssets" }.configureEach { dependsOn(subpluginResources) }
```

- [ ] **Étape 7 : build vert**

Run : `./gradlew spotlessApply && ./gradlew build --console=plain`
Attendu : BUILD SUCCESSFUL. En cas d'échec de compilation d'un script précompilé, lire le message (un accesseur `hytaleTools`, `spotless` ou `pmd` absent se contourne par `extensions.configure<…>` avec le type : `com.azuredoom.gradle.hytale.HytaleExtension`, `com.diffplug.gradle.spotless.SpotlessExtension`, `PmdExtension`).

- [ ] **Étape 8 : même comportement qu'avant**

```bash
unzip -l plugin/build/libs/HyColony-0.1.0.jar | awk '{print $4}' | grep -v '/$' | sort > /tmp/jar-after.txt
diff /tmp/jar-before.txt /tmp/jar-after.txt
cat core/build/test-results/test/*.xml | grep -o 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'
cat /tmp/tests-before.txt
cat plugin/src/main/resources/manifest.json
git diff plugin/src/main/resources/manifest.json
./gradlew tasks --all --console=plain | grep -E '^(runAllMods|stageAllModAssets|checkModApis|:?plugin:checkModApis)'
```

Attendu :
- `diff` des jars : **seules** des lignes `< com/azuredoom/hytale/asseteditor/…` (retirées) ;
- le même nombre de tests ;
- `manifest.json` inchangé (sinon, le commiter avec cette étape et expliquer l'écart dans le message) ;
- les tâches `runAllMods` et `stageAllModAssets` existent à la racine.

- [ ] **Étape 9 : chaque contrôle mord encore**

Chaque cas casse une chose, lance le contrôle, vérifie l'ÉCHEC avec le message indiqué, puis **remet en état** (`git checkout -- <fichier>` ou suppression du fichier créé) avant le cas suivant :

1. **Taille** : créer `core/src/main/java/dev/hycolony/core/TooLong.java` avec `package dev.hycolony.core; final class TooLong {}` suivi de 400 lignes vides ; `./gradlew :core:checkFileSizes` → « Files over 400 lines (CLAUDE.md § 2) ».
2. **Séparateur** : ajouter `// ---- section ----` dans une méthode de `core/src/main/java/dev/hycolony/core/kernel/BlockPos.java` ; `./gradlew :core:checkSectionDividers` → « Section-divider comments ».
3. **Formatage** : ajouter deux espaces en fin d'une ligne de `BlockPos.java` ; `./gradlew :core:spotlessCheck` → échec.
4. **PMD** : ajouter à `BlockPos` une méthode `void unused() { int x = 0; }` ; `./gradlew :core:pmdMain` → « PMD violations ».
5. **Convention absente** : remplacer `core/build.gradle.kts` par `plugins { \`java-library\` }` ; `./gradlew help` → « Projects without a hy.java-core or hy.hytale-mod convention » et `:core`.
6. **Import d'un autre mod** : créer `core/src/main/java/dev/hycolony/core/ModApiProbe.java` contenant `package dev.hycolony.core;` puis `import dev.hydomum.core.Internal;` puis `final class ModApiProbe {}` ; `./gradlew :core:checkModApis` (seul : la compilation échouerait, la classe n'existe pas) → « Imports of another mod outside its api packages » et la ligne 2. Puis remplacer l'import par `import dev.hydomum.api.VariantKey;` : `./gradlew :core:checkModApis` passe.
7. **NullAway** : ajouter à `BlockPos` `static String nothing() { return null; }` ; `./gradlew :core:compileJava` → erreur NullAway.

Pour finir : `git status` ne montre aucun fichier de ces cas, et `./gradlew build` est vert.

- [ ] **Étape 10 : relecture indépendante, puis commit**

`hycolony-reviewer` sur les changements non commités : « les contrôles du build.gradle.kts racine passent dans build-logic sans changer ce qu'ils vérifient (comparer avec `git show HEAD:build.gradle.kts`) ; conventions hy.java-core, hy.hytale-mod, hy.workspace ; checkModApis ; contrôle d'application des conventions ; jar identique sauf asseteditor ; même nombre de tests ». Corriger, faire relire les corrections.

```bash
git status --short
git add build-logic/settings.gradle.kts build-logic/build.gradle.kts build-logic/src gradle/libs.versions.toml settings.gradle.kts build.gradle.kts core/build.gradle.kts plugin/build.gradle.kts
git diff --cached --stat
git commit -m "$(cat <<'EOF'
build: move the build checks into build-logic conventions

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

(Ajouter `plugin/src/main/resources/manifest.json` s'il a changé à l'étape 8.)

La suite du plan peut tourner dans une session verrouillée.

---

### Tâche 3 : le serveur de dev passe à `runAllMods`, dans `run/`

**Files :**
- Modify : `.gitignore:15-19`, `docs/TESTING.md:3` et point 59, `README.md:24`, `docs/UPGRADING.md:8`
- Local, jamais commité : `run/` (copie de `plugin/run/`), suppression de `plugin/src/main/resources/packs/`

- [ ] **Étape 1 : le staging voit tout**

Run : `./gradlew stageAllModAssets --console=plain`
Puis : `ls -la run/mods/HyColony_hycolony/ run/mods/HyColony_hycolony/subplugins/ | head -30`
Attendu : BUILD SUCCESSFUL ; `run/mods/HyColony_hycolony/` contient `manifest.json`, `Common`, `Server`, `hycolony`, `config.json` (lien dur, nombre de liens 2), les classes `dev/hycolony/…` du plugin **et du cœur**, et `subplugins/` (les zips et `index.txt`). Si les classes du cœur manquent, le serveur de dev ne chargera pas : noter ce que le staging lie (« Hard-linked workspace classes »), chercher dans `StageAllModAssetsTask.groovy` (sources du plugin en cache) quels dossiers de classes il prend, et le signaler avant d'aller plus loin.

- [ ] **Étape 2 : reprendre les données du serveur de dev**

```bash
mkdir -p run
for f in universe prefabs permissions.json bans.json config.json auth.enc; do
  [ -e "plugin/run/$f" ] && cp -r "plugin/run/$f" run/
done
ls run
```

`plugin/run/` reste en place (l'utilisateur le supprimera). `run/config.json` est la config **du serveur** Hytale, pas celle de HyColony.

- [ ] **Étape 3 : retirer ce que l'ancien lien symbolique a écrit dans les ressources**

```bash
rm -rf plugin/src/main/resources/packs
```

Le `plugin/src/main/resources/config.json.bak` est un fichier local protégé : demander à l'utilisateur de le supprimer lui-même (`Remove-Item plugin\src\main\resources\config.json.bak`). Le `config.json` des ressources, lui, **reste** : c'est la config de dev de HyColony (spec, piste 1).

- [ ] **Étape 4 : `.gitignore`**

Remplacer :

```
# Dev server writes the plugin config into resources via the mods/ symlink
plugin/src/main/resources/config.json
plugin/src/main/resources/config.json.bak
# ... and the sub-plugin asset zips it extracts there
plugin/src/main/resources/packs/
```

par :

```
# Each mod's local dev config: runAllMods hard-links it into run/mods/ and reads it (split spec, dev config)
plugin/src/main/resources/config.json
plugin/src/main/resources/config.json.bak
blockui/src/main/resources/config.json
blockui/src/main/resources/config.json.bak
domum/plugin/src/main/resources/config.json
domum/plugin/src/main/resources/config.json.bak
```

- [ ] **Étape 5 : les docs**

- `docs/TESTING.md` l. 3 : `Serveur de dev : \`./gradlew :plugin:runServer\`.` devient `Serveur de dev : \`./gradlew runAllMods\` (dossier \`run/\` à la racine ; la config de HyColony est \`plugin/src/main/resources/config.json\`, relue à chaque lancement : ce que le serveur y réécrit est perdu au lancement suivant).`
- `docs/TESTING.md` point 59 : remplacer « Remplacer le contenu de `config.json` par `Le{`, puis relancer. » par « Remplacer le contenu de `plugin/src/main/resources/config.json` par `Le{`, puis relancer. » et « Supprimer ensuite les fichiers `.broken-*`. » par « Le `.broken-<date>` et le fichier neuf sont dans `run/mods/HyColony_hycolony/` ; le fichier des ressources reste cassé, et chaque lancement repart des valeurs par défaut tant qu'il n'est pas réparé. Remettre ensuite le `config.json` des ressources en état. »
- `README.md` l. 24 : `./gradlew :plugin:runServer  # local dev server` devient `./gradlew runAllMods  # local dev server (run/)`.
- `docs/UPGRADING.md` l. 8 : `./gradlew :plugin:runServer` devient `./gradlew runAllMods`.

- [ ] **Étape 6 : lancement par l'utilisateur**

Demander à l'utilisateur de lancer `./gradlew runAllMods` depuis la racine et de vérifier : « HyColony runtime ready for world » dans le journal, aucune ligne `missing asset id` ni SEVERE nouveau, son monde de test présent, une fenêtre de colonie qui s'ouvre, un sous-pack activé (Décorations) qui apparaît. Puis de noter si `plugin/src/main/resources/packs/` a été recréé (attendu : non ; les zips vont dans `run/mods/HyColony_hycolony/packs/`).

- [ ] **Étape 7 : relecture indépendante, puis commit**

`hycolony-reviewer` : « le serveur de dev passe à runAllMods dans run/ ; .gitignore et docs à jour ; aucun fichier local indexé ». Corriger, faire relire.

```bash
git status --short
git add .gitignore docs/TESTING.md README.md docs/UPGRADING.md
git diff --cached --stat
git commit -m "$(cat <<'EOF'
docs: the dev server is runAllMods in run/

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Tâche 4 : la spec suit, et la CI

**Files :** Modify : la spec.

- [ ] **Étape 1 : écrire l'écart sur les sources décompilées**

Dans la spec, § « Organisation », puce `hy.workspace`, remplacer la phrase qui commence par « Pour des sources décompilées uniques, aucun réglage du plugin ne suffit » et la suivante par :

« Sources décompilées : la redirection prévue n'est pas faite. Le plugin AzureDoom réutilise le chemin `build/vineflower/hytale-server` de chaque projet dans trois tâches (`HytaleIdeSourceConfigurer.groovy` l. 33, 64, 92-93, 168) ; n'en rediriger qu'une les désynchroniserait. La référence reste `build/vineflower/hytale-server` à la racine, déjà présente et figée tant que Hytale est épinglé en 0.6.8 ; à défaut, `./gradlew :plugin:decompileServerJar` l'écrit dans `plugin/build/vineflower/hytale-server`. »

- [ ] **Étape 2 : relecture et commit**

`hycolony-reviewer` sur ce changement de spec. Puis :

```bash
git add docs/superpowers/specs/2026-09-28-hycolony-split-hydomum-hyblockui-design.md
git commit -m "$(cat <<'EOF'
docs: split spec keeps the decompiled sources where they are

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Étape 3 : signaler la CI à l'utilisateur**

Lui dire : `.github/workflows/gradle.yml` ne se déclenche que sur `main`, une branche qui n'existe pas ; la CI ne vérifie donc rien aujourd'hui. C'est hors de ce projet (spec, « Hors de ce projet ») ; il décide s'il veut la brancher sur `sp0-foundations`.

Le plan 3 (HyBlockUI) peut ensuite s'écrire.
