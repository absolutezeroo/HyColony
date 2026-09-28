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
