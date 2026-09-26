# Mettre à jour Hytale

1. Lire les notes de patch (sections plugins/API, NPC, blocs, UI).
2. Changer `hytale_version`, `server_version` et `manifestServerVersion` dans `gradle.properties`.
3. `./gradlew build --refresh-dependencies`, puis `./gradlew decompileServerJar injectServerJavadocsIntoDecompiledSources`.
4. Corriger les erreurs de compilation **dans `plugin/` uniquement**. Le core ne doit pas bouger.
5. `./gradlew :core:test`.
6. `./gradlew :plugin:runServer`. Les logs doivent contenir `HyColony runtime ready for world` et aucune ligne `missing asset id`.
7. En jeu : `/hycolony selftest` doit afficher uniquement des lignes `[OK]`.
8. Charger une sauvegarde de la version précédente (copier un monde de test existant).
9. Dérouler `docs/TESTING.md`.
10. Commiter avec le message `build: bump Hytale to X.Y.Z`.

Notes :
- Les colonies sont dans `<sauvegarde du monde>/hycolony/colony-<id>.json` (avec `.bak`, `archive/` et `corrupt/`). Elles ne dépendent pas du format de sauvegarde de Hytale.
- Les identifiants d'assets Hytale sont tous dans `plugin/src/main/resources/hycolony/id-map.json`.
- Les plans de bâtiments (chemins de prefabs vanilla et position de la hutte) sont dans `plugin/src/main/resources/hycolony/styles.json`. Si Hytale déplace ou renomme un prefab, `/hycolony selftest` affiche `[KO] blueprint`.
- API instables à surveiller (voir `docs/research/plugin-b-api.md`) : `BlockOperations.setBlock` (« Not yet stable », l'Update 7 passe aux sections), `PrefabBufferCall` / `IPrefabBuffer.forEach`, `ContainerBlockWindow` et `PageManager.setPageWithWindows`, `AnimationUtils.playAnimation`. Elles ne sont utilisées que dans `plugin/.../adapter/` et `plugin/.../ui/`.
