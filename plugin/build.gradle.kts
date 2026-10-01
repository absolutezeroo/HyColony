plugins { id("hy.hytale-mod") }

dependencies {
    // The pure core and the api ship inside the plugin's jar: one jar to deploy, and the only copy of the api that
    // addons see (they compile against it, never ship it).
    bundled(project(":api"))
    bundled(project(":core"))
    // Another mod: compiled against, never shipped (plugin-b-api.md § 28.2).
    compileOnly(project(":blockui"))
    compileOnly(project(":domum-plugin"))
    compileOnly(project(":vanilla-plugin"))
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
val checkSubpluginAssets by tasks.registering(CheckPackAssets::class) {
    packs.from(subpluginNames.map { subpluginsSrc.dir(it) })
    namespace.set("HyColony")
    stamp.set(layout.buildDirectory.file("tmp/checkSubpluginAssets.stamp"))
}
subpluginResources { dependsOn(checkSubpluginAssets) }

// The food tooltips (Hytalor patches and hycolony_food.lang) are written by tools/food/generate.py from the id-map's
// food table: fail when the table changed without them (docs/research/food-tooltips.md).
val checkFoodTooltips by tasks.registering {
    val resources = layout.projectDirectory.dir("src/main/resources").asFile
    val patchDir = resources.resolve("Server/Patch/HyColony/Food")
    // The languages tools/food/generate.py writes (its TEXTS table).
    val langFiles = listOf("en-US", "fr-FR").map { resources.resolve("Server/Languages/$it/hycolony_food.lang") }
    inputs.files(fileTree(patchDir))
    inputs.file(resources.resolve("hycolony/id-map.json"))
    inputs.files(langFiles)
    val stamp = layout.buildDirectory.file("tmp/checkFoodTooltips.stamp")
    outputs.file(stamp)
    doLast {
        val idMap = groovy.json.JsonSlurper().parse(resources.resolve("hycolony/id-map.json")) as Map<*, *>
        val food = idMap["food"] as Map<*, *>
        val foods = (food["foods"] as Map<*, *>).mapKeys { it.key.toString() }
        // Each food's header, then its description line; the bench decides which foods are raw.
        val expected = listOf("# cookingBench=${food["cookingBench"]}") + foods.keys.sorted().flatMap { id ->
            val f = foods.getValue(id) as Map<*, *>
            listOf("# $id nutrition=${f["nutrition"]} tier=${f["tier"]} poisonous=${f["poisonous"]}", "$id.description")
        }
        val patches = patchDir.listFiles().orEmpty().map { it.name.removeSuffix(".json") }.toSortedSet()
        val problems = mutableListOf<String>()
        if (patches != foods.keys.toSortedSet()) problems += "patches $patches != foods ${foods.keys.sorted()}"
        langFiles.forEach { file ->
            val lines = file.takeIf { it.isFile }?.readLines().orEmpty().drop(1)
                .map { if (it.startsWith("#")) it else it.substringBefore(" = ") }
            if (lines != expected) problems += "${file.parentFile.name}/hycolony_food.lang differs from the id-map"
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Food tooltips out of date, run python tools/food/generate.py:\n" + problems.joinToString("\n"))
        }
        stamp.get().asFile.apply { parentFile.mkdirs(); writeText("ok\n") }
    }
}
tasks.named("processResources") { dependsOn(checkFoodTooltips) }

sourceSets.main { resources.srcDir(subpluginResources) }
// runServer puts the resources' source dirs on its classpath without building them.
tasks.matching { it.name == "prepareRunServer" }.configureEach { dependsOn(subpluginResources) }
// runAllMods puts every resource dir of the mod on its classpath, the generated sub-plugins included; its staging
// only links src/main/resources. processResources already builds them; this keeps the staging explicit about it.
rootProject.tasks.matching { it.name == "stageAllModAssets" }.configureEach { dependsOn(subpluginResources) }
