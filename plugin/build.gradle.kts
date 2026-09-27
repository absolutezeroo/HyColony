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

sourceSets.main { resources.srcDir(subpluginResources) }
// runServer puts the resources' source dirs on its classpath without building them.
tasks.matching { it.name == "prepareRunServer" }.configureEach { dependsOn(subpluginResources) }
// In dev, the plugin data directory is src/main/resources (linked as the run asset pack): keep the pack zips that
// setup() extracts there out of the jar.
tasks.processResources { exclude("packs/**") }
