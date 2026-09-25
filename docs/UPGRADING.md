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
