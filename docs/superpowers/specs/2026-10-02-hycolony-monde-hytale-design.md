# Systèmes de MineColonies, monde de Hytale

Date : 2026-10-02. Demandé par l'utilisateur : « faudrait aussi revoir et adapter MC à Hytale car Hytale fonctionne pas comme Minecraft ». Choix de l'utilisateur :
- « systèmes MC, monde Hytale » (et non un audit seul ni un rééquilibrage libre) ;
- sans équivalent dans Hytale, on prend « l'équivalent Hytale le plus proche » ;
- démarche 1 : la règle, puis un audit, puis une correction par domaine.

## 1. Objectif

Jusqu'ici, HyColony porte MineColonies « à l'identique » (CLAUDE.md, intro et § 6) : un écart n'est permis que s'il est forcé. Résultat : des règles de MC qui supposent le monde Minecraft (paliers d'outils, établi, temps, culture, mobs…) sont recopiées telles quelles, ou bien HyVanilla ajoute la chose de Minecraft qui manque.

La nouvelle règle :
- les **systèmes** de MineColonies restent portés à l'identique ;
- dès qu'une règle s'appuie sur le **monde** Minecraft, elle suit Hytale.

Succès :
1. la règle est écrite dans CLAUDE.md et dans les garde-fous qui la répètent ;
2. un audit recense tout ce qui est déjà porté et suppose le monde Minecraft ;
3. chaque domaine relevé par l'audit est corrigé, testé et relu.

## 2. La frontière

| Système : on garde MC | Monde : on suit Hytale |
|---|---|
| Huttes, niveaux, plans et logique de construction (Structurize) | Blocs, objets, matériaux et leurs paliers |
| Métiers, IA, machines à états, délais en ticks | Outils : paliers, durabilité, blocs qu'ils cassent |
| Système de requêtes, entrepôt, livraisons | Artisanat : bancs, catégories, recettes, carburant |
| Colonie, territoire, permissions, commandes | Culture : croissance, eau, engrais, essence |
| Formules des citoyens : compétences, XP, bonheur, faim | Nourriture : objets, effets, cuisson |
| Fenêtres et leur contenu | Mobs, factions, raids, combat |
| Configuration (`config.json`, sections MC) | Jour et nuit, météo, taille du monde, chunks, navigation et physique des PNJ, sons, particules, animations |

Les règles de la zone grise :
- **Le temps.** Une durée de MC exprimée par rapport au monde (« la nuit », « au lever du jour », « une journée = 24 000 ticks ») se recalcule sur le cycle de Hytale, comme fraction de sa journée. Un délai propre au système (« travaille toutes les X ticks », « réessaie après 1 200 ticks ») reste celui de MC, en ticks.
- **Ce qui manque dans Hytale.** Quand une règle de MC dépend d'une chose absente de Hytale, on prend l'équivalent Hytale le plus proche : palier d'outil, banc, ingrédient, groupe de PNJ. La chose de Minecraft n'est ajoutée (HyVanilla) que s'il n'existe rien de comparable **et** que le système ne peut pas s'en passer. L'équivalent choisi est vérifié dans les sources décompilées ou les assets, jamais supposé.
- **Ce qui n'existe que dans Hytale** (magie, montures…). Rien n'est ajouté tant qu'aucun système de MC n'y touche. Ce n'est pas une source de nouvelles fonctionnalités.
- **Les objets propres à MC** (outil de construction, blocs de huttes, Domum Ornamentum…) font partie du système : ils restent. Leurs recettes passent toutefois par les bancs de Hytale (monde).
- **Constantes et formules d'équilibrage.** Elles restent celles de MC, sauf si leur valeur mesure le monde Minecraft : une durée de croissance, un palier, une durabilité.

## 3. Le marquage dans le code

Un écart dû au monde s'écrit `Deviation from MC (Hytale world): <règle de MC> → <équivalent Hytale, source>`. Exemple : `Deviation from MC (Hytale world): MC's tool levels (wood..netherite) → Hytale's gather power per material (Server/Item/...)`.

Le commentaire garde le préfixe `Deviation from MC`, ce qui permet toujours de le retrouver. Il figure dans la spec du sous-projet, comme tout écart (§ 6). La Javadoc continue de citer la source MC du système (`MC EntityAIWorkLumberjack.chopTree`).

## 4. Garde-fous à modifier (accord explicite de l'utilisateur, session `HYCOLONY_GUARDRAILS_UNLOCKED=1`)

### `CLAUDE.md`

L'intro devient :

> HyColony porte MineColonies sur Hytale 0.7.0-pre.4 (Update 7, épinglé dans `gradle.properties`). **Les systèmes de MC sont portés à l'identique** : huttes, métiers, IA, requêtes, colonie, formules des citoyens, fenêtres, avec leurs règles, constantes et formules. **Le monde est celui de Hytale** : blocs, objets, outils, artisanat, culture, nourriture, mobs, temps. Une règle de MC qui s'appuie sur le monde Minecraft suit l'équivalent Hytale le plus proche (voir § 6).

Le § 6 reçoit en tête la frontière (le tableau du § 2, en court) et les règles de la zone grise. La puce « Un écart… » devient :

> Un écart porte un commentaire `Deviation from MC: …` et figure dans la spec du sous-projet. Un écart forcé par le monde Hytale s'écrit `Deviation from MC (Hytale world): <règle MC> → <équivalent Hytale, source>`. L'équivalent est vérifié dans `build/vineflower/hytale-server` ou les assets, jamais supposé. HyVanilla n'ajoute une chose de Minecraft que s'il n'existe aucun équivalent et que le système ne peut pas s'en passer.

### Les autres fichiers

- **`AGENTS.md`** : rien à changer, il renvoie à CLAUDE.md sans dupliquer la règle. On le vérifie quand même à l'écriture.
- **`.claude/skills/port-mc/SKILL.md`** :
  - la description dit « systèmes fidèles, monde Hytale » ;
  - l'étape 3 (« Map to Hytale ») commence par trier chaque règle lue en système ou monde (CLAUDE.md § 6), puis demande de chercher l'équivalent Hytale de chaque règle du monde (skill `hytale-api`, assets) ;
  - l'étape 5 ajoute le marquage `(Hytale world)`.
- **`.claude/agents/mc-fidelity-checker.md`** :
  - l'étape 3 distingue les deux cas. Pour une règle du système, l'agent compare avec MC comme aujourd'hui. Pour une règle du monde, il vérifie qu'elle suit Hytale et que l'équivalent est cité avec sa source ;
  - une règle du monde recopiée de MC alors que Hytale a un équivalent est une constatation (finding).
- **`.claude/agents/hycolony-implementer.md`** et **`.claude/agents/hycolony-reviewer.md`** : la puce « Fidelity » reprend la même distinction en une phrase.
- **`docs/research/pieges-portage.md`** (pas un garde-fou) : une section « 4. Pièges du monde » renvoie au § 6 et liste les règles du monde déjà rencontrées (chunks de 32 blocs, hauteur, culture SP3b, nourriture SP4b, lumière et monstres).

## 5. L'audit

Livrable : `docs/research/audit-monde-hytale.md`, en français.

**Périmètre.**
- `core/`, `plugin/` et leurs ressources : `hycolony/id-map.json`, `styles.json`, recettes, défauts de `config.json`.
- HyVanilla : pour chaque bloc, l'audit cherche s'il a désormais un équivalent Hytale qui le rendrait inutile.
- HyDomum, HyBlockUI et HyLens sont hors périmètre : le premier est un objet de MC (système), les deux autres n'ont pas de règle du monde.

**Une entrée par règle**, regroupée par domaine (outils, artisanat, culture, nourriture, temps, mobs et raids, matériaux et plans, navigation, autres). Chaque entrée donne :
1. la règle de MC, avec `fichier:ligne` dans `sources/minecolonies/` ;
2. le code HyColony qui la porte (`fichier:ligne`), et ce qu'il fait aujourd'hui (copie de MC, écart déjà marqué, ou chose ajoutée par HyVanilla) ;
3. le fait Hytale, avec sa source (décompilé ou asset) ;
4. le verdict : *conforme* (suit déjà Hytale), *à adapter* ou *à retirer de HyVanilla* ;
5. la proposition, avec son effet sur les sauvegardes (migration ?) et sur la configuration.

**Comment.** Un agent `hycolony-researcher` par groupe de domaines (au plus 4, en parallèle). Chacun n'écrit que sa section, sous `docs/research/`. On les fusionne ensuite en un seul fichier, trié par domaine, avec en tête une table des domaines à adapter dans l'ordre proposé (d'abord ce qui casse le jeu, puis ce qui le rend incohérent, puis le cosmétique).

L'audit est présenté à l'utilisateur, qui choisit l'ordre des corrections.

## 6. Les corrections

Une modification par domaine, dans l'ordre choisi :
- **Petit domaine** (quelques constantes, un équivalent d'outil) : courte conception validée dans la conversation (CLAUDE.md § 9.2).
- **Domaine qui restructure** (raids, artisanat) : spec et plan propres (§ 9.1).
- Pour chacune :
  - TDD dans le cœur. L'attendu d'un test du monde vient du fait Hytale cité (asset, décompilé), celui d'un test du système de MC ;
  - migration (skill `add-migration`) si une valeur persistée change de sens ;
  - clés de traduction si un texte change ;
  - relecture par `hycolony-reviewer` et `mc-fidelity-checker` (avec la nouvelle règle) ;
  - mise à jour de `docs/TESTING.md` pour ce que l'utilisateur vérifie en jeu.

Un bloc de HyVanilla jugé inutile n'est retiré qu'après le passage des plans (`styles.json`) à l'équivalent Hytale, et avec une migration des colonies qui l'utilisent.

## 7. Hors périmètre

- Les fonctionnalités propres à Hytale qu'aucun système de MC ne touche.
- Le rééquilibrage des constantes et formules du système (option non retenue).
- HyDomum, HyBlockUI et HyLens.

## 8. À vérifier

- Après l'étape 4 : CLAUDE.md, la skill `port-mc` et les trois agents disent la même chose ; `node .claude/hooks/test/run.js` passe toujours (les fichiers modifiés restent des garde-fous).
- Après l'étape 5 : chaque entrée de l'audit a ses trois sources (MC, HyColony, Hytale) ; aucune affirmation sans `fichier:ligne`.
- Après chaque correction : `./gradlew build` vert, relectures faites, test en jeu par l'utilisateur.

## 9. Écarts par domaine

### Domaine 1 : ressources du chantier (2026-10-02, audit A-15, A-16)

Choix de l'utilisateur : « faut faire comme MC, l'objet qui pose le bloc » ; pour le tonneau, « on rajoute le craft, dans l'atelier de furniture », « comme le coffre de taverne ».

- **L'objet qui pose un bloc** (`EntryCost.placingItem`), `Deviation from MC (Hytale world)`. MC demande l'objet du bloc (`BlockUtils.getItemStackFromBlockState`). Ici, on prend dans l'ordre :
  1. l'objet du bloc, s'il a une source en survie : une recette, la scie de l'architecte de HyDomum, la casse ou la récolte d'un bloc (outils compris, comme les cisailles), une liste de butin ;
  2. sinon, l'objet dont la variante de pose fait le bloc : la torche pour la torche murale, la lanterne pour la lanterne au plafond ;
  3. sinon, ce que la casse du bloc rend : 2 petits coffres pour un grand coffre, un tronc pour un tronc plein, du pavé (`Rock_Stone_Cobble`) pour `Rock_Stone`. Ce peut être plusieurs objets là où MC en demande un ;
  4. sinon, l'objet du bloc quand même.

  L'herbe et les chemins demandent leur propre objet, que Hytale fabrique, là où les gestionnaires de pose de Structurize demandent de la terre.
- **La redéfinition de `checkIfNeedsItem`** de `AbstractEntityAIStructureWithWorkOrder` est portée (`BuilderAI.needsItem`, avec son drapeau `recalculated`) : avant le premier chargement d'une structure, une requête en attente n'envoie pas le bâtisseur attendre.
- **Réparation des sauvegardes** (`BuilderRequests.cancelUnneeded`), `Deviation from MC`. Au chargement d'un ordre, les requêtes de la hutte pour un objet que plus aucune case ne demande sont annulées. Ce sont celles d'une sauvegarde écrite avant ce changement. Après un redémarrage, cela annule aussi une requête de lot dont toutes les cases ont été posées entre-temps, que MC livrerait encore. MC ne revoit jamais ces requêtes.
- **Le tonneau de taverne** (ajout demandé) :
  - une recette au banc de mobilier, catégorie rangement : 3 `Wood_Darkwood_Planks` et 2 `Ingredient_Bar_Iron` ;
  - un patch Hytalor `UseDefaultDropWhenPlaced`, pour qu'un tonneau posé se rende lui-même à la casse ;
  - les poses du bâtisseur marquées comme celles d'un joueur (`BlockPhysics.markDeco`).

  Recherche : `docs/research/barrel-recipe.md`.

### Domaine 1, suite (2026-10-03, recherche `docs/research/domaine1-suite.md`)

- **Correspondances portées** (`StructurePlan.satisfied`) :
  - une case d'herbe ou de terre est faite par tout bloc du tag `dirt` de MC (Structurize `GrassPlacementHandler`). Ses équivalents Hytale sont listés dans la section `construction` de l'id-map : terres, herbes, aiguilles et litière (podzol), mousses, boue. Les cases concernées sont toutes les variantes Hytale d'herbe et de terre (`dirtCells`), la terre sèche (`coarse_dirt`) exceptée ;
  - une case de mur, de clôture, de barreaux ou de portillon est faite par toute forme de sa famille (MC `GeneralBlockPlacementHandler` et `DoBlockPlacementHandler`). La famille est celle du gabarit de connexion, si l'id-map le range parmi les formes libres (`WallConnectedBlockTemplate` et les clôtures et vitres de HyDomum) ;
  - une case de fluide est faite par une source, un bloc solide, ou un bloc debout dans une source (Structurize `FluidSubstitutionPlacementHandler` : `isSource`, bloc noyé, `isAnySolid`). Le bloc noyé de MC devient, dans Hytale, un bloc qui partage sa case avec un fluide (`FluidSection`, port `WorldBlocks.fluidAt`). Une plante sèche est remplacée par l'eau, comme MC remplace un bloc qu'il ne peut pas noyer. `Deviation from MC (Hytale world)` : MC garde aussi un bloc sec qui pourrait être noyé (`WATERLOGGED`) ; les blocs Hytale n'ont pas cette propriété, donc seul un bloc déjà dans une source compte. L'ancien écart « tout fluide compte » disparaît.
  - CLEAR et les restes d'une amélioration jugent une case de la même façon (`StructurePlan.matchesAt`), comme l'itérateur de Structurize saute hors retrait toute case déjà conforme (`AbstractBlueprintIterator`).
- **Coût en terre** (`EntryCost`, Structurize `GrassPlacementHandler` et `BlockGrassPathPlacementHandler`) : une case d'herbe, de terre ou de chemin demande de la terre (`plainDirt` de l'id-map, le `Blocks.DIRT` de MC ; l'herbe de Hytale se casse aussi en terre), et un chemin posé sur de la terre ne coûte rien.
- **Feuilles gratuites** (`EntryCost`, MC `BuildingStructureHandler.isStackFree`) : un bloc du groupe Hytale `Leaves` ne coûte rien.
- **Bon sol**, `Deviation from MC (Hytale world)` : la forme de collision pleine de Structurize devient le test de bloc plein de Hytale (`FluidTicker.isFullySolid`) avec la hitbox pleine (`Full`), feuilles exclues ; la boue (7/8 de haut, comme celle de MC) n'en est pas un.
- **HyDomum** : un bloc de forme se rend lui-même à la casse, comme un bloc DO. Le générateur recopiait la casse du matériau par défaut (`Wood_Stripped_Deco`) ; il ne garde plus que sa façon de récolter (`tools/domum/blocks/common.py`, contrôle `every_template_breaks_into_itself`).
