plugins { id("hy.hytale-mod") }

// HyBlockUI, the UI library (split spec § Identité de chaque mod): a mod of its own, like ldtteam's BlockUI.
group = "dev.hyblockui"

hytaleTools {
    modId = "hyblockui"
    mainClass = "dev.hyblockui.HyBlockUIPlugin"
    modDescription = "Inventory windows for Hytale mods: native-looking grids, drag and drop, the player's panels."
    modCredits = project.property("mod_author").toString()
    manifestDependencies = "Hytale:AssetModule=*"
    manifestOptionalDependencies = ""
}

tasks.named<Jar>("jar") { archiveBaseName.set("HyBlockUI") }
