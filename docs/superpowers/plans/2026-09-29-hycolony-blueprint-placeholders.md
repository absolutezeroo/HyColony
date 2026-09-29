# Blocs de dev des plans MineColonies : plan d'implémentation

> Sous-skill : superpowers:executing-plans. Étapes à cocher (`- [ ]`).

**Spec :** `docs/superpowers/specs/2026-09-29-hycolony-blueprint-placeholders-design.md` (lis-la en entier, avec `CLAUDE.md` et `docs/research/structurize-placeholders.md`).

## Contraintes globales

- `core/` sans `com.hypixel` ; paquets pleins (`colony`, `building`, `kernel/port`) : aucun fichier ajouté.
- 400 lignes par fichier, 40 par méthode, 5 paramètres ; Javadoc courte avec la source MC ; `Deviation from MC:` sur chaque écart.
- TDD ; `./gradlew :core:build :plugin:build` vert avant chaque commit (`:blockui` peut être en cours dans une autre session) ; `git add <chemins>` explicites ; jamais le serveur Hytale.

## Tâches

### Task 1 : modèle du cœur
- [ ] `BlueprintMarkers` (record) et `Blueprint.markers` (`Optional`, constructeur à 4 paramètres gardé).
- [ ] Tests `StructurePlanTest` : un plan sans marqueurs garde la CLEAR de toute la boîte ; en mode MC, CLEAR = air explicite + blocs + remplissage, de haut en bas, sans case absente ni fluide ; remplissage en SOLID (bas en haut) avec le bloc donné ; fluide en DECORATE.
- [ ] `StructurePlan.build(bp, hut, catalog, fillBlock)` + `isFill(pos)` / `isFluidFill(pos)` ; appels mis à jour (BuilderAI, WandPaste).

### Task 2 : scan et coût
- [ ] `ItemCatalog.isGoodFloor` (+ `FakeItemCatalog`).
- [ ] Tests `StructureScan` via le builder : case absente jamais minée ; bon sol sous remplissage gardé ; mauvais bloc sous remplissage miné puis remplacé ; fluide posé si ni source ni plein.
- [ ] Coût : une case de remplissage qui correspond ne coûte rien ; sinon 1 bloc de remplissage ; fluide gratuit.

### Task 3 : réglage « bloc de remplissage »
- [ ] `BuilderSettingsModule.fillBlock` persisté (absent = `BlueprintSource.defaultFillBlock()`), dans `BuilderSettingsView`.
- [ ] Action `setFillBlock` (permission, bon sol, a un objet) ; le plan de l'ordre utilise le réglage de la hutte du bâtisseur.

### Task 4 : plugin, lecture des prefabs
- [ ] Vérifier dans les sources que `Empty` explicite reste dans le buffer (`blockId == 0`).
- [ ] `PrefabStyles.Level.minecolonies` ; `HytaleBlueprintSource` : toutes les couches, hutte à l'ancre, marqueurs ; ids `blueprint.placeholder.solid|fluid`, `blueprint.placeholderFluid`, `blueprint.fillBlock` dans l'id-map.
- [ ] `HytaleItemCatalog.isGoodFloor` vérifié dans les sources ; notes dans `docs/research/plugin-b-api.md`.

### Task 5 : plugin, blocs et fenêtre
- [ ] Assets `HyColony_Placeholder_Solid` / `_Fluid` (textures, onglet créatif, noms en-US/fr-FR).
- [ ] Ligne « Bloc de remplissage » de l'onglet Réglages et sélecteur avec recherche (généraliser celui des graines).

### Task 6 : convertisseur
- [ ] Vérifications d'abord (`check_placeholders.py`), puis `editor_block` : solide / fluide / tag ; eau et lave dans `fluids` ; ancre = `primary_offset`.
- [ ] Passe complète sur `medievaloak` : 0 erreur, compte des nouveaux blocs.

### Task 7 : relecture et essai
- [ ] `hycolony-reviewer` + `mc-fidelity-checker` sur la branche ; corrections relues.
- [ ] `docs/TESTING.md` : un plan converti (fondations, substitutions, pêcheur) à essayer en jeu.
