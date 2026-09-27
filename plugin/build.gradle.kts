plugins {
    java
    id("com.azuredoom.hytale-tools") version "1.+"
}

// Les classes du core sont copiées dans le jar du plugin : un seul jar à déployer.
val bundled: Configuration by configurations.creating

dependencies {
    implementation(project(":core"))
    bundled(project(":core"))
    compileOnly("com.google.code.gson:gson:2.11.0")
}

hytaleTools {
    javaVersion = property("java_version").toString().toInt()
    hytaleVersion = property("hytale_version").toString()
    manifestServerVersion = property("manifestServerVersion").toString()
    manifestGroup = property("manifest_group").toString()
    modId = property("mod_id").toString()
    modDescription = property("mod_description").toString()
    modUrl = property("mod_url").toString()
    mainClass = property("main_class").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
    curseforgeId = property("curseforgeID").toString()
    disabledByDefault = property("disabled_by_default").toString().toBoolean()
    includesPack = property("includes_pack").toString().toBoolean()
    patchline = property("patchline").toString()
    injectServerJavadocsIntoSources = property("injectServerJavadocsIntoSources").toString().toBoolean()
    generateAssetsBinary = property("generateAssetsBinary").toString().toBoolean()
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(project.property("mod_name").toString())
    archiveVersion.set(project.property("version").toString())
    dependsOn(bundled)
    from({ bundled.filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Optional sub-plugins (spec 2026-09-27 § 7): each src/subplugins/<Name>/ becomes, on the classpath,
// subplugins/<Name>.zip (its Common/ and Server/, an asset pack registered at setup when enabled),
// subplugins/<Name>/subplugin.json and its hycolony/*.json fragments, plus subplugins/index.txt listing the names.
val subpluginsSrc = layout.projectDirectory.dir("src/subplugins")
val subpluginNames = subpluginsSrc.asFile.listFiles { f -> f.isDirectory }.orEmpty().map { it.name }.sorted()
val subpluginsOut = layout.buildDirectory.dir("generated/subplugins")

val subpluginZips = subpluginNames.map { name ->
    tasks.register<Zip>("zipSubplugin_$name") {
        from(subpluginsSrc.dir(name)) { include("Common/**", "Server/**") }
        archiveFileName.set("$name.zip")
        destinationDirectory.set(layout.buildDirectory.dir("tmp/subplugin-zips"))
        // Byte-identical zips: setup() rewrites the extracted copy only when its content changed.
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

val subpluginResources by tasks.registering(Sync::class) {
    into(subpluginsOut)
    into("subplugins") {
        from(subpluginZips)
        from(subpluginsSrc) { include("*/subplugin.json", "*/hycolony/*.json") }
    }
    inputs.property("names", subpluginNames)
    val names = subpluginNames
    val src = subpluginsSrc.asFile
    doLast {
        names.forEach { name ->
            val manifest = groovy.json.JsonSlurper().parse(File(src, "$name/subplugin.json")) as Map<*, *>
            if (manifest["Name"] != name) {
                throw GradleException("src/subplugins/$name/subplugin.json: Name must be \"$name\"")
            }
        }
        File(destinationDir, "subplugins/index.txt").writeText(names.joinToString("\n", postfix = "\n"))
    }
}

// Hytale shuts the whole server down when a zip pack holds an invalid asset (docs/research/plugin-b-api.md § 23): check
// the items' Common paths against CommonAssetValidator's roots, and that the pack's own (HyColony) files exist.
val checkSubpluginAssets by tasks.registering {
    val src = subpluginsSrc.asFile
    val stamp = layout.buildDirectory.file("tmp/checkSubpluginAssets.stamp")
    inputs.dir(src)
    outputs.file(stamp)
    doLast {
        val model = ".blockymodel" to listOf("Blocks/", "Items/", "Resources/", "NPC/", "VFX/", "Consumable/")
        val texture = ".png" to listOf("Blocks/", "BlockTextures/", "Items/", "NPC/", "Resources/", "VFX/")
        // Item.Icon, Model, Texture; BlockType.CustomModel, CustomModelTexture[].Texture, Textures[].<face>.
        val rules = mapOf("Icon" to (".png" to listOf("Icons/ItemsGenerated/", "Icons/Items/")), "Model" to model,
            "CustomModel" to model, "Texture" to texture) +
            listOf("All", "Sides", "Top", "Bottom", "UpDown", "North", "South", "East", "West").associateWith { texture }
        val errors = mutableListOf<String>()
        fun walk(pack: File, item: String, node: Any?) {
            when (node) {
                is Map<*, *> -> node.forEach { (key, value) ->
                    val rule = rules[key]
                    if (rule != null && value is String) {
                        val (extension, allowed) = rule
                        if (allowed.none { value.startsWith(it) } || !value.endsWith(extension)) {
                            errors += "$item: $key $value is not a $extension under $allowed"
                        }
                        if (value.contains("/HyColony/") && !File(pack, "Common/$value").isFile) {
                            errors += "$item: $key $value does not exist"
                        }
                    } else {
                        walk(pack, item, value)
                    }
                }
                is List<*> -> node.forEach { walk(pack, item, it) }
            }
        }
        src.listFiles { f -> f.isDirectory }.orEmpty().forEach { pack ->
            File(pack, "Server/Item/Items").walkTopDown().filter { it.extension == "json" }.forEach {
                walk(pack, it.name, groovy.json.JsonSlurper().parse(it))
            }
        }
        if (errors.isNotEmpty()) throw GradleException(errors.joinToString("\n"))
        stamp.get().asFile.writeText("ok\n")
    }
}
subpluginResources { dependsOn(checkSubpluginAssets) }

sourceSets.main { resources.srcDir(subpluginResources) }
// runServer puts the resources' source dirs on its classpath without building them.
tasks.matching { it.name == "prepareRunServer" }.configureEach { dependsOn(subpluginResources) }
// In dev, the plugin data directory is src/main/resources (linked as the run asset pack): keep the pack zips that
// setup() extracts there out of the jar.
tasks.processResources { exclude("packs/**") }
