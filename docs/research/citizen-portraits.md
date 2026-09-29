# Portraits de citoyens dans les fenêtres (vérifié le 2026-09-28)

Question : afficher la tête ou le visage réel de chaque citoyen dans les fenêtres (fenêtre du citoyen, liste des citoyens de l'hôtel de ville, travailleurs d'une hutte), pas seulement son nom.

Abréviations :
- `hs/` : `build/vineflower/hytale-server/com/hypixel/hytale/` ;
- `zip:` : `release-0.6.8-Assets.zip` ;
- `ed/` : `C:\Users\Ctuto\Desktop\Hytale UI Editor\reference\official-docs\` ;
- `MC/` : `github.com/ldtteam/minecolonies`, branche `version/main`, sous `src/main/`.

Rappel : un aperçu 3D d'une entité donnée dans une fenêtre est impossible en 0.6.8 (`citizen-inventory-window.md` § 8.3).

## 1. L'apparence des citoyens aujourd'hui

- **Tous les citoyens sont identiques.** Le rôle `plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json` l. 3 donne `"Appearance": "PlayerTestModel_V"`. Ce modèle (`zip:Server/Models/Human/PlayerTestModel_V.json`) a `"Parent": "Player"` et des `DefaultAttachments` figés : coupe `Morning` `BrownDark`, visage `Face.png` teinte de peau `03`, yeux `Greyscale.png`, jean bleu, t-shirt bleu, bottes, bouche `Mouth1`, oreilles `Ears1`, sourcils `Medium` `BrownDark`.
- `HytaleCitizenBodies.spawn` (`plugin/.../adapter/HytaleCitizenBodies.java` l. 102-120) appelle `spawnNPCWithColumnProbe` sans modèle ni skin. Il n'ajoute que `CitizenTag`, `MoveTarget` et le nom affiché.
- `CitizenData` (`core/.../citizen/CitizenData.java` l. 16-28) ne stocke **aucune apparence**. Seul `gender` existe (tiré dans `CitizenManager` l. 115-127), et il ne sert qu'au nom et à `CitizenRow` (`core/.../app/ui/CitizenRow.java` l. 6). Il n'a aucun effet visuel.

### 1.1 MineColonies varie l'apparence

`MC/java/com/minecolonies/core/colony/CitizenData.java` :
- l. 519-524 (`initForNewCivilian`) : `female = random.nextBoolean()`, `textureSuffix` tiré dans `SUFFIXES = ("_b", "_d", "_a", "_w")` (l. 98), `textureId = random.nextInt(255)` ;
- l. 1322 et 1328 : ces valeurs sont sauvegardées (`TAG_SUFFIX`, `TAG_TEXTURE`) ;
- l. 342 et 1592-1594 : `textureUUID` optionnel (skin de joueur).

Donner à chaque citoyen une apparence aléatoire persistante est donc **un portage de MC**, indépendamment des portraits.

### 1.2 Le mécanisme Hytale : skin de joueur sur un PNJ

- `CosmeticsModule.generateRandomSkin(Random)` (`hs/server/core/cosmetics/CosmeticsModule.java` l. 311-373) renvoie un `protocol.PlayerSkin` :
  - toujours : corps (teinte de peau), sous-vêtement, visage, oreilles, bouche, yeux ;
  - barbe une fois sur deux (`nextInt(10) > 4`) ;
  - coupe, sourcils, pantalon, haut, surhaut, chaussures : chacun optionnel (`selectRandomOrNull`) ;
  - accessoires de tête, de visage, d'oreille et particularité de peau : une fois sur dix ;
  - jamais de gants ni de cape.
- Format d'une pièce : `"id.couleur"` ou `"id.couleur.variante"` (l. 386-415). Champs de `protocol/PlayerSkin.java` l. 19-57 : `bodyCharacteristic, underwear, face, eyes, ears, mouth, facialHair, haircut, eyebrows, pants, overpants, undertop, overtop, shoes, headAccessory, faceAccessory, earAccessory, skinFeature, gloves, cape`.
- `createModel(skin)` (l. 134-145) valide le skin, puis renvoie le modèle `"Player"` sans attachements : c'est le client qui habille le modèle d'après le skin.
- **Précédent vanilla sur un PNJ** : `/npc spawn --randomModel` (`hs/server/npc/commands/NPCSpawnCommand.java` l. 102, 194-202). La commande génère un skin, passe `createModel(skin)` à `spawnEntity`, puis pose `PlayerSkinComponent(skin)` et `ApplyRandomSkinPersistedComponent` dans le rappel `postSpawn`.
- Envoi aux clients : `EntityTrackerSystems.EntitySkin` (`hs/server/core/modules/entity/tracker/EntityTrackerSystems.java` l. 686-750) interroge **toute entité** visible qui a un `PlayerSkinComponent`, pas seulement les joueurs, et envoie `PlayerSkinUpdate`.
- **Pas persisté** : `PlayerSkinComponent` est enregistré sans codec (`hs/server/core/modules/entity/EntityModule.java` l. 466-468). `ModelSystems.ApplyRandomSkin` (`hs/server/core/modules/entity/system/ModelSystems.java` l. 117-140) tire un **nouveau** skin à chaque ajout d'une entité qui porte `ApplyRandomSkinPersisted`. Pour nous : le skin doit vivre dans `CitizenData` (cœur, JSON), et le plugin le repose à chaque apparition du corps, **sans** `ApplyRandomSkinPersisted`, sinon le visage changerait à chaque chargement.
- `spawnNPCWithColumnProbe(..., postSpawn)` (`hs/server/npc/NPCPlugin.java` l. 1241-1250) n'a pas de paramètre de modèle, contrairement à `spawnEntity(store, roleIndex, pos, rot, Model, postSpawn)` (l. 1596-1605). Il faudrait donc remplacer le `ModelComponent` dans `postSpawn`, ou passer par `spawnEntity`.
- **[in-game]** Plusieurs points restent à voir en jeu :
  - le rendu d'un `PlayerSkinComponent` posé sur notre rôle (dont l'`Appearance` est `PlayerTestModel_V` et ses `DefaultAttachments`), si l'on ne remplace pas le modèle par `"Player"` ;
  - les animations d'objet déjà utilisées (`Block/Build`, `Pickaxe/Mine`) sur ce modèle.

### 1.3 Taille du catalogue (fini, mais énorme)

Le catalogue est lu par `CosmeticRegistry` dans `Cosmetics/CharacterCreator/*.json` (`hs/server/core/cosmetics/CosmeticRegistry.java` l. 69-99). Comptes relevés dans `zip:Cosmetics/CharacterCreator/`, pour les pièces visibles sur un portrait :

| Pièce | Entrées | Couleurs |
|---|---|---|
| `BodyCharacteristics` (teinte du crâne et du cou) | 2 (`Default`, `Muscular`) | `Skin` : 47 |
| `Faces` | 18 | teinte de peau (visage tiré **sans** couleur, l. 314 : il suit le corps) |
| `Eyes` | 10 | `Eyes_Gradient` : 18 |
| `Eyebrows` | 14 | `Hair` : 30 |
| `Ears` | 6 | peau |
| `Mouths` | 8 | peau |
| `FacialHair` | 28 (+ aucune) | `Hair` : 30 |
| `Haircuts` | 112 (+ aucune) | `Hair` : 30 |
| `HeadAccessory` | 60 | selon la pièce |
| `FaceAccessory` | 21 | selon la pièce |
| `EarAccessory` | 6 | avec variantes |

Nombre de combinaisons de têtes : plus de 10¹² (47 × 18 × 10 × 18 × 14 × 30 × 8 × 29 × 113 × 30…). Il est **impossible** d'en pré-rendre toutes les combinaisons.

Teinte : les textures sont en niveaux de gris (`GreyscaleTexture`), et chaque couleur est une texture de dégradé 256×16 (`GradientSets.json`, par exemple `TintGradients/Skin_Tones/03.png`, `TintGradients/Hair/Black.png`). La teinte est donc une **table de dégradé** (gradient map), pas une multiplication. Un script peut reproduire ce calcul : l'essai fait (visage `Face.png` 32×32 teinté par le rouge du gris → abscisse du dégradé `03`) donne un visage plausible. La règle exacte de lecture (canal, ligne du dégradé) reste **[in-game]**, à comparer à une capture.

Géométrie : le visage est un **quad plat** 30×28 à l'avant de la tête (`zip:Common/Characters/Body_Attachments/Faces/Player_Face_Detached.blockymodel`, nœud `Face`, `"type": "quad"`, `"normal": "+Z"`). Les yeux sont un atlas 32×32 (blanc + iris) posé par `Eyes.blockymodel`. Les coupes sont de vrais volumes (18 nœuds pour `Morning.blockymodel`). Un portrait de face demande donc une **projection orthographique** des boîtes et quads des `.blockymodel` : orientation, `stretch`, `textureLayout` par face, miroir et angle.

## 2. Ce qu'un `.ui` sait afficher

- **`AssetImage`** (`ed/type-documentation/elements/assetimage.md` l. 18-37) : propriété `AssetPath` (String), accepte des enfants, a `MaskTexturePath` et `Background`. Il n'a **pas** de propriété de couleur ou de teinte.
  - Usage vanilla : `zip:Common/UI/Custom/Pages/Memories/Memory.ui` l. 61-64 (`AssetImage #Icon { Anchor: (Width: 128, Height: 128); FallbackTexturePath: "UI/Custom/Pages/Memories/MissingIcon.png"; }`) et `MemoriesPanel.ui` l. 133-136 (`#MemoryIcon`, 200×200). `FallbackTexturePath` n'est pas dans le schéma de l'éditeur, mais le vanilla s'en sert.
- **Chemin dynamique posé par le serveur : oui.** `MemoriesPage` fait `commandBuilder.set(selector + "#Icon.AssetPath", iconPath)` (`hs/builtin/adventure/memories/page/MemoriesPage.java` l. 111, 115, 179, 388) et `setNull("#MemoryIcon.AssetPath")` (l. 390). Les chemins sont **relatifs à `Common/`** : `"UI/Custom/Pages/Memories/categories/…png"`, `"Icons/ModelsGenerated/" + rôle + ".png"`.
- **Précédent de portrait de PNJ** : `NPCMemory.getIconPath()` (`hs/builtin/adventure/memories/memories/npc/NPCMemory.java` l. 123-125) renvoie `"Icons/ModelsGenerated/<rôle>.png"`. Le zip contient 255 images `Common/Icons/ModelsGenerated/*.png`, 128×128 RGBA. Ce sont des **rendus pré-calculés du corps entier** (vu : `Outlander_Peon.png`), produits par l'outillage de Hypixel. Il n'y a **aucune** image de type `Human`, `Player` ou villageois : les rendus vanilla ne couvrent pas les skins de joueur.
- **`Background` dynamique** :
  - chaîne : `ShopElement` fait `set(selector + " #Icon.Background", iconPath)` (`hs/builtin/adventure/shop/ShopElement.java` l. 32) ;
  - objet : `setObject(sel + ".Background", PatchStyle)`, par exemple `WorldEventInspectorPage` l. 273. `PatchStyle` a `TexturePath`, `Color`, `Border` et `Area` (`hs/server/core/ui/PatchStyle.java` l. 8-20, `setColor` l. 60), et figure dans `UICommandBuilder.CODEC_MAP` (`hs/server/core/ui/builder/UICommandBuilder.java` l. 187).
  - **[in-game]** Racine d'une chaîne posée dans `Background` (UI Path relatif au `.ui` selon `ed/markup.mdx` l. 197, ou relatif à `Common/` comme `AssetPath` ?).
- **Teinte** : `PatchStyle.Color` avec `TexturePath` n'apparaît en vanilla qu'en blanc avec alpha (`Common.ui` l. 509, 714-716 ; `ToolsLegendsCommon.ui` l. 45-49). **[in-game]** Une couleur non blanche multiplie-t-elle la texture ? Même si c'est le cas, une multiplication ne reproduit pas une table de dégradé : le résultat ne serait qu'approché.
- **Superposition** : un `Group` peut empiler des enfants ancrés `Anchor: (Full: 0)`, et `AssetImage` accepte des enfants. Empiler 6 à 8 calques PNG transparents de même cadre est donc possible dans la syntaxe. **[in-game]** Ordre de dessin (vraisemblablement celui des enfants) et coût dans une liste de 50 lignes.
- **Masque** : `MaskTexturePath` (vanilla : `Common.ui` l. 939, `"Common/CircularProgressBarMask.png"`) permettrait un portrait rond.
- **Élément natif d'avatar : aucun.**
  - Pas d'élément `Avatar`, `Portrait` ou `PlayerHead` dans `ed/type-documentation/elements/` (48 éléments listés) ni dans `ed/../src/core/schema.json`.
  - Aucun asset d'UI nommé avatar ou portrait dans le zip (seules les langues `avatarCustomization/*.lang`).
  - `CharacterPreviewComponent` ne peut pas viser une entité (`citizen-inventory-window.md` § 8.3).
- **Livraison des images** : elles doivent être dans le pack du plugin (`plugin/src/main/resources/Common/UI/Custom/...`), envoyé à la connexion. En créer à l'exécution (`CommonAssetModule.addCommonAsset`) n'est pas fiable (`plugin-b-api.md` § 17, vu en jeu : affichage seulement après reconnexion). Il faut donc des **images générées au build**.
- **Validation** : les racines de `CommonAssetValidator` (`plugin-b-api.md` § 23) valent pour les chemins cités **dans des assets** (`Item.Icon`, `BlockType`…). Les chaînes envoyées par `UICommandBuilder` ne passent pas par ce validateur. **[in-game]** Chemin absent : image de repli (`FallbackTexturePath`) ou vide.
- MCP `hytale-docs` : non consulté ici. Tout ce qui précède repose sur les sources décompilées, le zip et la doc de l'éditeur.

## 3. Options

| Option | Faisabilité | Coût | Fidélité au visage réel |
|---|---|---|---|
| **(a) Portrait en calques** : le build rend, pour chaque pièce et chaque couleur, sa vue de face dans un cadre commun (PNG transparents) ; la fenêtre empile 6 à 8 `AssetImage` dont le serveur pose les `AssetPath` d'après le skin du citoyen | Oui (API vérifiée § 2) ; ordre de superposition **[in-game]** | Script de projection de `.blockymodel` avec table de dégradé : quelques centaines de lignes. Volume : coupes 112 × 30 = 3 360, barbes 840, sourcils 420, visages 18 × 47 = 846, yeux 180, bouches 376… soit environ 6 000 petits PNG (quelques Mo). À réduire en limitant le catalogue des citoyens | Exacte si la projection est juste, sauf les cheveux qui passent derrière les oreilles (l'ordre des calques ne fait pas de profondeur par pixel) |
| **(a') Calques gris teintés par l'UI** : un PNG gris par pièce, teinte par `PatchStyle.Color` | Teinte non blanche **[in-game]** | Environ 300 PNG | Approchée (multiplication ≠ table de dégradé) |
| **(b) Préréglages pré-rendus** : un catalogue fini de N têtes (par exemple 64, réparties par genre), un skin complet et **un** PNG par préréglage, rendus au build ; le citoyen stocke l'id de son préréglage | Oui, une seule `AssetImage` par ligne (exactement le motif de `Memory.ui`) | Le même moteur de projection que (a), mais N images seulement ; code d'exécution trivial | Exacte, avec la profondeur correcte (composition 3D au build) ; variété limitée à N |
| **(c) Caméra serveur en gros plan** : `SetServerCamera` avec `ServerCameraSettings.attachedToType = EntityId` et `attachedToEntityId` (`hs/protocol/ServerCameraSettings.java` l. 37-38 ; `AttachedToType { LocalPlayer, EntityId, None }`, `hs/protocol/AttachedToType.java` l. 6-8 ; envoi comme `PlayerCameraTopdownCommand` l. 69) | Pas dans une fenêtre : la page recouvre la vue, et c'est une scène plein écran (l'approche d'Aetherhaven, dont on ne reprend que l'idée) | Moyen, et fragile : restaurer la caméra à la fermeture, gérer la déconnexion | Réelle, en 3D, mais pas un portrait de liste |
| **(d) Élément natif d'avatar** | **N'existe pas** en 0.6.8 (§ 2) | — | — |

## 4. MineColonies

- **Liste des citoyens de l'hôtel de ville** : `MC/resources/assets/minecolonies/gui/townhall/layoutcitizens.xml` l. 23 déclare `<entityicon id="entity" size="25 50" pos="396 38" visible="false" />`. C'est un **rendu du corps entier** (25×50) du citoyen sélectionné, pas une tête.
  - `MC/java/com/minecolonies/core/client/gui/townhall/WindowCitizenPage.java` l. 146-151 : `entityIcon.setEntity(selectedEntity)` puis `show()`, **seulement si le citoyen dort** (`Pose.SLEEPING`).
  - l. 281-289 (`onUpdate`) : l'icône est cachée s'il dort, montrée sinon.
  - La logique paraît inversée : l'entité n'est affectée que lorsqu'elle dort, et n'est montrée que lorsqu'elle ne dort pas. Le rendu effectif en jeu n'est pas vérifié. `ENTITY_ICON = "entity"` : `MC/java/com/minecolonies/api/util/constant/WindowConstants.java` l. 312.
- **Inventaire du citoyen** : l'entité vivante est rendue, et suit la souris (`WindowCitizenInventory.renderBg`, voir `citizen-inventory-window.md` § 8.1).
- **Fenêtre du citoyen** (`gui/citizen/main.xml`, `nav.xml`, `family.xml`…) et **listes de travailleurs des huttes** : aucun `entityicon`. Les 94 `.xml` de `MC/resources/assets/minecolonies/gui/` ont été parcourus : seul `townhall/layoutcitizens.xml` en contient un. La fenêtre du citoyen n'a qu'une silhouette de genre, `colonist_wax_male_smaller.png` (`layoutcitizens.xml` l. 13).
- Conclusion :
  - un aperçu du citoyen sélectionné dans l'hôtel de ville et dans l'inventaire est **un portage de MC** (corps entier, un seul citoyen à la fois) ;
  - un visage **par ligne** dans les listes (hôtel de ville, travailleurs) et dans l'en-tête de la fenêtre du citoyen est **un ajout demandé par l'utilisateur** : `Deviation from MC`, à inscrire dans la spec ;
  - une apparence variée et persistante des citoyens est **un portage** (`textureId`, `textureSuffix`, § 1.1).

## 5. Recommandation

1. **D'abord, varier les corps** (portage de MC, prérequis de tout portrait) :
   - le cœur stocke l'apparence dans `CitizenData` (sérialisée, avec migration du schéma) ;
   - le plugin pose `PlayerSkinComponent` et un modèle `"Player"` à chaque apparition, sans `ApplyRandomSkinPersisted`.
   - **[in-game]** : rendu sur notre rôle.
2. **Portraits : option (b), préréglages pré-rendus.**
   - L'apparence d'un citoyen est un **id de préréglage** tiré dans un catalogue fini (par exemple 32 par genre), défini dans un fichier de données du plugin (skin complet + PNG de tête).
   - Un script de build (`tools/portraits/`, comme `tools/vanilla/`) projette les `.blockymodel` de face, applique les tables de dégradé et écrit un PNG par préréglage dans `Common/UI/Custom/HyColony/Portraits/`.
   - L'UI : une `AssetImage` par ligne, `ui.set(sel + " #Portrait.AssetPath", "UI/Custom/HyColony/Portraits/<id>.png")`, avec `FallbackTexturePath` sur une silhouette.
   - Même moteur que (a), dix fois moins d'images, la profondeur correcte, aucun empilement à l'exécution, et un seul `set` par ligne.
   - (a) reste possible plus tard si l'on veut des skins entièrement aléatoires.
3. Garder (c) comme idée pour un éventuel écran « voir le citoyen », pas pour les listes.

## 6. Questions ouvertes pour l'utilisateur

1. Un catalogue fini de préréglages (par exemple 64 têtes) vous suffit-il, ou voulez-vous des skins entièrement aléatoires comme `/npc spawn --randomModel` (option (a), environ 6 000 calques, fidélité des cheveux moindre) ?
2. Le genre de MC doit-il piloter l'apparence ? Hytale n'a pas de genre de corps (`BodyCharacteristics` : `Default`/`Muscular`). Proposition : des préréglages étiquetés par genre (coupe, barbe).
3. Où afficher le portrait : en-tête de la fenêtre du citoyen, chaque ligne de la liste de l'hôtel de ville, chaque ligne des travailleurs d'une hutte ? Taille (32, 48 ou 64 px) ? Tête seule ou buste ?
4. Accepte-t-on que les citoyens existants reçoivent un préréglage aléatoire à la migration (leur allure change une fois) ?

## 7. À vérifier en jeu

- **[in-game]** `PlayerSkinComponent` sur un PNJ de rôle `HyColony_Citizen` : rendu, et interaction avec l'`Appearance` `PlayerTestModel_V`.
- **[in-game]** `AssetImage.AssetPath` vers une PNG de notre pack (`UI/Custom/HyColony/...`) et comportement de `FallbackTexturePath` dans notre `.ui`.
- **[in-game]** Racine d'une chaîne posée dans `Background` (§ 2).
- **[in-game]** Teinte `PatchStyle.Color` non blanche (seulement pour l'option (a')).
- **[in-game]** Règle exacte de la table de dégradé (canal, ligne), à comparer à une capture d'un citoyen.
