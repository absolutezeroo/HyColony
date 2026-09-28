import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Hytale shuts the whole server down when a pack holds an invalid asset (docs/research/plugin-b-api.md § 23): checks
 * the items' Common paths against CommonAssetValidator's roots, and that the pack's own (namespace) files exist. Each
 * of [packs] is a pack root holding Common/ and Server/.
 */
abstract class CheckPackAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packs: ConfigurableFileCollection

    /** The pack's own folder name under Common (HyColony, HyDomum): its paths must point at existing files. */
    @get:Input
    abstract val namespace: Property<String>

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun check() {
        val model = ".blockymodel" to listOf("Blocks/", "Items/", "Resources/", "NPC/", "VFX/", "Consumable/")
        val texture = ".png" to listOf("Blocks/", "BlockTextures/", "Items/", "NPC/", "Resources/", "VFX/")
        // Item.Icon, Model, Texture; BlockType.CustomModel, CustomModelTexture[].Texture, Textures[].<face>.
        val rules = mapOf("Icon" to (".png" to listOf("Icons/ItemsGenerated/", "Icons/Items/")), "Model" to model,
            "CustomModel" to model, "Texture" to texture) +
            listOf("All", "Sides", "Top", "Bottom", "UpDown", "North", "South", "East", "West").associateWith { texture }
        val own = "/${namespace.get()}/"
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
                        if (value.contains(own) && !File(pack, "Common/$value").isFile) {
                            errors += "$item: $key $value does not exist"
                        }
                    } else {
                        walk(pack, item, value)
                    }
                }
                is List<*> -> node.forEach { walk(pack, item, it) }
            }
        }
        packs.files.filter { it.isDirectory }.forEach { pack ->
            File(pack, "Server/Item/Items").walkTopDown().filter { it.extension == "json" }.forEach {
                walk(pack, it.name, JsonSlurper().parse(it))
            }
        }
        if (errors.isNotEmpty()) throw GradleException(errors.joinToString("\n"))
        stamp.get().asFile.writeText("ok\n")
    }
}
