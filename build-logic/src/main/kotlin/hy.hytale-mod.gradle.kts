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

// CLAUDE.md § 7: each en-US .lang file and its fr-FR twin hold the same keys. Keys are read as Hytale's
// LangFileParser.parse reads them: a value ending in "\" goes on over the next line, a key with no value is skipped.
val checkLangParity by tasks.registering {
    group = "verification"
    description = "Fails when an en-US .lang file and its fr-FR twin do not hold the same keys"
    val languages = layout.projectDirectory.dir("src/main/resources/Server/Languages")
    val files = fileTree(languages) { include("en-US/*.lang", "fr-FR/*.lang") }
    inputs.files(files)
    doLast {
        fun langKeys(file: File): Set<String> {
            if (!file.isFile) return emptySet()
            val keys = mutableSetOf<String>()
            var continued = false
            file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
                if (continued) {
                    continued = line.endsWith("\\")
                    return@forEach
                }
                val key = line.substringBefore('=', "").trim()
                val value = line.substringAfter('=', "").trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    keys += key
                    continued = value.endsWith("\\")
                }
            }
            return keys
        }
        val found = files.files.map { it.name }.toSortedSet().flatMap { name ->
            val en = langKeys(languages.file("en-US/$name").asFile)
            val fr = langKeys(languages.file("fr-FR/$name").asFile)
            (en - fr).map { "  fr-FR/$name lacks $it" } + (fr - en).map { "  en-US/$name lacks $it" }
        }
        if (found.isNotEmpty()) {
            throw GradleException("Translation keys missing in one language (CLAUDE.md § 7):\n" +
                found.joinToString("\n"))
        }
    }
}
tasks.named("check") { dependsOn(checkLangParity) }
