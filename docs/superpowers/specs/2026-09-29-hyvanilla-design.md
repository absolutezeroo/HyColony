# HyVanilla : quatrième mod, les blocs vanilla de Minecraft absents de Hytale

Validé avec l'utilisateur le 2026-09-29 (nom, dépendance obligatoire). Modèle suivi : `2026-09-28-hycolony-split-hydomum-hyblockui-design.md`.

## Objectif

Les tapis et les pots de fleurs sont des blocs vanilla de Minecraft, pas des ajouts de MineColonies. Ils vivent aujourd'hui dans le sous-pack `Decorations` de HyColony. Les lits suivront. Ces blocs forment un mod à part, **HyVanilla**, comme Domum Ornamentum est devenu HyDomum.

## Dépendances

```
HyBlockUI ← HyDomum ← HyColony → HyVanilla
```

(`A ← B` : B dépend de A.)

- HyVanilla ne dépend d'aucun des trois autres mods : il n'a pas de fenêtre, donc pas besoin de HyBlockUI.
- HyColony **dépend obligatoirement** de HyVanilla (`"HyColony:hyvanilla"` dans `Dependencies`), comme de HyDomum.
- HyColony ne voit que `dev.hyvanilla.api` (et `dev.hyvanilla.plugin.api` si besoin), en `compileOnly` **[build : `checkModApis`]**.

## Modules

| Module | Contenu |
|---|---|
| `vanilla/core` (`:vanilla-core`, `dev.hyvanilla.core`) | Les règles pures : `FlowerPot` et `FlowerPotBlocks` quittent `core/.../decoration`, avec leurs tests. `hy.java-core`. |
| `vanilla/plugin` (`:vanilla-plugin`, `dev.hyvanilla.plugin`) | `FlowerPotSystem` et `FlowerPotUse` quittent `plugin/block`, plus un `HyVanillaPlugin` minimal. Le pack d'assets : le contenu actuel de `plugin/src/subplugins/Decorations/` (`Common/`, `Server/`). Il a ses propres `hyvanilla.lang` et `hyvanilla/id-map.json`. `hy.hytale-mod`. |
| `tools/decorations` → `tools/vanilla` | Le générateur écrit dans le pack de `vanilla/plugin`. |

## Ce qui change dans HyColony

- **Le sous-pack `Decorations` disparaît** dans le même plan. `subplugins/Styles_*` restent.
- `IdMap.flowerPots()`, sa vérification et l'enregistrement conditionnel dans `BlockSystems` partent avec le système : la table des pots ne concerne plus HyColony.
- **Renommages complets, sans migration**, comme pour HyDomum (plan split-4) :
  - identifiants : `HyColony_Carpet_<C>` → `HyVanilla_Carpet_<C>` et `HyColony_Flower_Pot_<C>` → `HyVanilla_Flower_Pot_<C>` ;
  - dossiers d'assets : `…/HyColony/` → `…/HyVanilla/`. `CheckPackAssets` vérifie alors le pack avec l'espace de noms `HyVanilla` ;
  - langue : le préfixe d'une clé vient du nom du fichier `.lang`. Les clés `hycolony.item.carpet.*` et `hycolony.item.flower_pot.*` deviennent donc `hyvanilla.item.*`, dans `hyvanilla.lang` (en-US et fr-FR).

  Le générateur écrit ces noms, puis on le relance. Les pots et tapis déjà posés dans un monde de test deviennent « inconnu », comme les blocs DO l'ont été.
- `settings.gradle.kts` inclut `:vanilla-core` et `:vanilla-plugin`, et `runAllMods` charge les quatre mods.
- **Protection de colonie avant le pot** : `FlowerPotSystem` passe aujourd'hui après `BlockUseProtectionSystem` et ignore un usage déjà annulé. HyVanilla ne connaît pas HyColony, donc l'ordre s'inverse de côté. HyVanilla expose `HyVanillaSystems.flowerPotUse()` dans `dev.hyvanilla.plugin.api`, et `BlockUseProtectionSystem` déclare `Order.BEFORE` sur lui. C'est le même motif que `HyDomumSystems.cutterUse()`. Le pot garde son test `isCancelled()`.

## Garde-fous à modifier (accord explicite de l'utilisateur, session déverrouillée)

- `build-logic/.../hy.java-checks.gradle.kts` : ajouter `"dev.hyvanilla." to listOf("dev.hyvanilla.api.", "dev.hyvanilla.plugin.api.")` à `modApis`, et `dev.hyvanilla` à `NullAway:AnnotatedPackages`.
- `CLAUDE.md` § 1 et § 7, et `AGENTS.md` : passer de trois à quatre mods, avec `hyvanilla.lang` et `hyvanilla/id-map.json`.

## Lits (sous-projet suivant)

Ce sont des lits 1×2 comme ceux de Minecraft, dans HyVanilla : un par couleur de laine, comme les tapis. Ils ont leur propre recherche et leur propre spec, une fois HyVanilla en place. La recherche d'un lit multi-cellules existe déjà (`docs/research/sp4-sleep-home.md` § B.2) : un bloc modèle avec des cellules `filler`, un tableau `Beds` et la racine `Block_Bed`.

## À vérifier

- `./gradlew build` vert : les tests de `FlowerPot` passent dans `vanilla/core`, `checkModApis` et `checkFileSizes` aussi.
- En jeu (`runAllMods`) : les tapis et les pots s'affichent et se craftent comme avant, sous leurs nouveaux noms, et poser ou reprendre une plante marche comme avant. Un étranger ne peut pas toucher un pot dans une colonie (ordre des systèmes).
- Avec les jars de production dans `mods/` : les quatre ensemble démarrent. HyColony sans HyVanilla s'arrête en nommant la dépendance manquante (`plugin-b-api.md` § 28.4).
