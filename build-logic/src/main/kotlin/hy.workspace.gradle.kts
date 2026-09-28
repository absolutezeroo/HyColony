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
