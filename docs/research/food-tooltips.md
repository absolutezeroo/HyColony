# Infobulle des aliments : afficher la nutrition des citoyens

> **Mise à jour du 2026-10-04.** La table `food.foods` de l'id-map n'existe plus : les valeurs des aliments sont les fichiers `plugin/src/main/resources/Server/HyColony/Foods/<id>.json`, et `food.cookingBench` est remplacé par la règle « tout banc `Processing` avec combustible qui cuit un aliment » (spec `docs/superpowers/specs/2026-10-04-hycolony-nourriture-ouverte-design.md` § 5.5 et § 7). `tools/food/generate.py` et `checkFoodTooltips` lisent ces fichiers. Le reste de cette recherche (patchs Hytalor, textes, séparateur) vaut toujours.

Recherche du 2026-10-01. But : ajouter à l'infobulle de chaque aliment Hytale une ligne comme « Nourrit un citoyen : 12 (2 gigots) · Palier 1 » (ou « Trop cru pour les citoyens », « Empoisonné »), tirée de la table `food.foods` de `plugin/src/main/resources/hycolony/id-map.json` (50 entrées), traduite en en-US et fr-FR.

Sources :
- Hytale **0.7.0-pre.5** : sources décompilées `build/vineflower/hytale-server/com/hypixel/hytale/` (abrégé `HY/`). Elles correspondent bien à pre.5 : la liste des classes `com/hypixel/hytale/**` de `Server/HytaleServer.jar` de `~/.gradle/caches/hytale-assets/pre-release-0.7.0-pre.5.jar` (manifeste `Implementation-Version: 0.7.0-pre.5`, révision `70c9872b`) est identique, à 0 différence près, à celle des `.java` décompilés ; la classe `server/core/modules/ui/DemoTypes`, nouvelle en pre.5, y est, et `server/core/universe/world/IWorldChunks`, retirée en pre.5, n'y est plus.
- Assets : `~/.gradle/caches/hytale-assets/pre-release-0.7.0-pre.5-Assets.zip` (abrégé `zip:`).
- Hytalor : dépôt `https://github.com/HypersonicSharkz/Hytalor`, HEAD `a74e60e` du 2026-02-21, manifeste en version 2.1 (abrégé `HT/`). La page CurseForge (`https://www.curseforge.com/hytale/mods/hytalor/files/all`) liste la 2.3 (2026-06-05) comme dernière version ; c'est elle qui a tourné le 2026-10-01 (`run/logs/2026-10-01_05-26-52_server.log`). Le jar 2.3 n'est plus sur le disque : son code n'a pas été lu, seul son comportement dans ce journal.

## Réponse courte

1. **Hytalor marche pour ça, avec un patch par aliment.** Un fichier `Server/Patch/**.json` du pack HyColony, avec `"_BaseAssetPath": "Server/Item/Items/Food/Food_Bread.json"` et `"TranslationProperties": {"Description": "hycolony_food.Food_Bread.description"}`, ne change que cette clé. Hytalor écrit une **copie complète** du JSON vanilla fusionné dans son propre pack, sans `"Parent": "super"` : le `BlockType` contenu reste intact. Les jokers existent, mais un même patch donnerait la même clé à tous les aliments : il faut donc un patch par aliment (50 fichiers, à générer).
2. **Sans fichier d'asset, une seule voie publique tient** : réécrire, à la sortie, le paquet `UpdateTranslations` que le serveur envoie au client (`PacketAdapters.registerOutbound`). On ajoute alors notre ligne au texte de la clé vanilla `server.items.<id>.description`. Le texte vanilla est gardé, dans toutes les langues. Mais 8 des 50 aliments n'ont pas de clé de description (oeuf, sucre d'orge, 6 champignons lumineux), et on ne sait pas si le client en cherche une par défaut **[in-game]**. `I18nModule` n'a aucune API publique pour ajouter ou remplacer un message. Recharger l'`Item` à chaud est possible, mais lourd.
3. **Une description vanilla accepte plusieurs lignes et du balisage** : `\n`, `<color is="#ffffff">…</color>`, `<i>`, `<b>`, `<item is="…"/>`, et surtout `<msg key="…"/>`, qui insère une autre clé. Les clés portails s'en servent dans leurs descriptions. Une description HyColony peut donc reprendre la description vanilla par `<msg key="server.items.Food_Bread.description"/>`, puis ajouter sa ligne.
4. **Recommandation** : la voie Hytalor (1), en dépendance **optionnelle**, avec des patchs et des clés générés depuis l'id-map, et une description de la forme `<msg key="server.items.X.description"/>\n\n<color …>ligne HyColony</color>`. La voie paquet (2) est l'alternative sans dépendance. (Mis en œuvre ensuite avec un trait de 18 `―` à la place de la ligne vide : `client-tooltip-markup.md` § 7.)

## 1. Hytalor

### 1.1 Format et emplacement d'un patch

- Les patchs sont lus dans `Server/Patch/` de **chaque** pack d'assets (`HT/src/main/java/com/hypersonicsharkz/HytalorPlugin.java:33`, `PatchManager.java:80-114`). Le parcours est récursif (`walkFileTree`, `PatchManager.java:99`), donc les sous-dossiers comptent aussi (`Server/Patch/HyColony/Food/…`). Un fichier dont le nom commence par `!` est ignoré (`PatchManager.java:265-267`). Le 2026-10-01, la 2.3 a bien lu `domum/plugin/src/main/resources/Server/Patch/HyDomum/*.json` (journal cité, lignes « Loading Patch: »).
- La cible est donnée par `_BaseAssetPath`, chemin relatif à la racine d'un pack. Cette valeur peut être :
  - un chemin exact ;
  - un joker (`QueryUtil.globToRegex`) ;
  - `regex:…` ;
  - un tableau de chemins (`PatchManager.java:160-171`, `64-78`).
- L'ancienne clé `BaseAssetPath` est dépréciée (`PatchManager.java:155-158`, `193-224`).
- `_priority` règle l'ordre d'application : plus grand = appliqué plus tôt, 0 par défaut (`PatchManager.java:318-323`).
- Fusion (`HT/…/util/JSONUtil.java:55-94`) :
  - les objets fusionnent clé par clé, récursivement (l. 80-87) ;
  - une clé absente de la cible est ajoutée (l. 67-75) : on peut donc ajouter un `Description` à un aliment qui n'en a pas ;
  - une valeur simple remplace celle de la cible (l. 90-91) ;
  - les tableaux passent par `_index`, `_find`, `_findAll` et `_op` (README).
- Patch minimal pour le pain :

```json
{
  "_BaseAssetPath": "Server/Item/Items/Food/Food_Bread.json",
  "TranslationProperties": {
    "Description": "hycolony_food.Food_Bread.description"
  }
}
```

  Le `Name` vanilla est gardé, puisque `TranslationProperties` est fusionné et non remplacé (`JSONUtil.java:81-87` ; test `HT/src/test/resources/nestedObjectPatch/`).
- La clé ne doit pas commencer par `server.`. Seules les clés `server.*` sont contrôlées contre l'en-US au chargement, avec l'avertissement « [LOC] Key … does not exist in server.lang! » (`HY/server/core/asset/type/item/config/ItemTranslationProperties.java:22-37`). Nos clés viennent de `hycolony_food.lang`, donc commencent par `hycolony_food.` (`HY/server/core/modules/i18n/I18nModule.java:374-384`).

### 1.2 Le `BlockType` contenu reste intact

- Hytalor lit le fichier de base, applique les patchs, puis écrit le **JSON complet** dans `mods/HytalorOverrides/<même chemin>` (`PatchManager.java:276-350`, écriture l. 347-349). Ce dossier est enregistré au `BootEvent` comme pack `com.hypersonicsharkz:Hytalor-Overrides` (`HytalorPlugin.java:98`, `108-115`, `178-201`).
- Le fichier écrit n'a **pas** de `"Parent": "super"` : c'est une copie. On le voit sur le reste de l'essai des clôtures, `run/mods/HytalorOverrides/Server/Item/Items/Metal/Iron/Metal_Iron_Fence.json` : 100 lignes, `BlockType` complet à la l. 18, ni `Parent` ni `_BaseAssetPath`. Les clés de contrôle sont retirées par `JSONUtil.java:57-58`.
- Un aliment garde son `Parent` vanilla (`Template_Food`, `Template_Fruit`, `Template_Crop_Item`, `Food_Pie_Apple`…). Son `BlockType` contenu hérite donc du `BlockType` du même nom, qui existe, comme en vanilla :
  - `INHERIT_ID_AND_PARENT` ne cherche que la clé parente (`HY/assetstore/AssetStore.java:1090-1100`) ;
  - le piège connu ne vient que du parent `"super"` (`AssetStore.java:793` ; `docs/research/connected-blocks.md:161`).
- Le pack chargé en dernier gagne : `DefaultAssetMap.putAll` ajoute le pack à la chaîne de la clé, puis met `chain[last]` dans `assetMap` (`HY/assetstore/map/DefaultAssetMap.java:272-301`).
- Essai du 2026-10-01 (pre.4) : le pack `HytalorOverrides` a été chargé (journal cité, l. 1151-1166), sans avertissement « inherited parent asset super ». L'utilisateur a abandonné les clôtures pour leur rendu, pas pour une casse du bloc (`docs/superpowers/specs/2026-10-01-hydomum-fence-connections-design.md:3`). Pour les aliments (objet posable, `BlockType` avec parent) : **[in-game]**.

### 1.3 Un patch par aliment

- Un patch peut viser plusieurs fichiers (joker, regex ou tableau, § 1.1), mais il applique **le même corps** à tous. La description doit être une clé par aliment, car le texte et les chiffres changent d'un aliment à l'autre. Il faut donc **un fichier par aliment**, soit 50 fichiers, générés depuis `food.foods` de l'id-map (même idée que `tools/domum`).
- `DescriptionArguments` (§ 3.3) permettrait une seule clé partagée, mais cela reste un patch par aliment, et aucun asset vanilla ne s'en sert.
- Chemins des 50 aliments, relevés dans `zip:Server/Item/Items/` :
  - `Food/Food_*.json` (21 aliments) ;
  - `Plant/Fruit/Plant_Fruit_*.json` (9) ;
  - `Plant/Crop/<Culture>/Plant_Crop_<Culture>_Item.json` (12) ;
  - `Plant/Crop/Plant_Crop_Mushroom_Glowing_*.json` (6).
- Description vanilla :
  - 42 aliments ont une clé `Description` ;
  - 8 n'en ont pas, avec un `TranslationProperties` qui ne contient que `Name` : `Food_Egg`, `Food_Candy_Cane` et les 6 `Plant_Crop_Mushroom_Glowing_*`. Le patch leur ajoute la clé (§ 1.1).
  - `TranslationProperties` s'hérite **en bloc** : un enfant qui en déclare un ne reprend rien de celui du parent (`HY/server/core/asset/type/item/config/Item.java:129-133`). Les enfants de `Food_Pie_Apple` (`Food_Salad_Berry`, `Food_Salad_Mushroom`, `Food_Pie_Meat`) ont leur propre `Description`, donc patcher la tarte ne les touche pas.

### 1.4 Déclarer la dépendance

- Hytalor lit les patchs de **tous** les packs, quel que soit l'ordre de chargement : ceux déjà chargés au `LoadAssetEvent` (`HytalorPlugin.java:68-84`), puis chaque pack enregistré ensuite (`AssetPackRegisterEvent`, l. 86-91). Aucune déclaration n'est donc nécessaire pour que les patchs s'appliquent.
- Identifiant : `com.hypersonicsharkz:Hytalor` (Group `com.hypersonicsharkz`, Name `Hytalor` : `HT/src/main/resources/manifest.json` ; journal : « com.hypersonicsharkz:Hytalor from path Hytalor-2.3.jar »). Le manifeste prend une table `PluginIdentifier → SemverRange` (`HY/common/plugin/PluginManifest.java:74-87`), comme nos `"HyColony:hydomum": "=0.1.0"` (`plugin/src/main/resources/manifest.json`).
- Pour que HyColony démarre sans Hytalor, on la déclare en optionnelle : `"OptionalDependencies": { "com.hypersonicsharkz:Hytalor": "*" }`. Sans Hytalor, les fichiers de `Server/Patch/` ne sont lus par personne, et l'infobulle reste vanilla. Un `Server/Patch/` est ignoré par Hytale : ses chargeurs ne lisent que les chemins de leurs stores (`AssetRegistryLoader.java:243-265`, `AssetModule.java:561-575`), aucun n'a `Patch`, et une dépendance optionnelle absente n'ajoute rien au tri (`Mod.java:69-74`) (vérifié dans les sources à la relecture, 2026-10-01).
- Version : la 2.3 annonce `ServerVersion` `>=0.5.3 <0.6.0`. Le serveur ne fait qu'avertir (WARN « targets server version range… », puis SEVERE « One or more plugins are targeting a different server version », journal cité l. 121), et elle a marché en pre.4.
  - Le code GitHub (2.1) appelle `AssetModule.registerPack(String, Path, PluginManifest)` (`HytalorPlugin.java:197-201`). Or pre.4 comme pre.5 n'ont que la version à 4 paramètres, avec `AssetPack.PackSource` (`HY/server/core/asset/AssetModule.java:388`, descripteurs relevés dans les deux jars). La 2.3 a donc été adaptée depuis, sans publier sa source.
  - Fonctionnement en pre.5 : **[in-game]** (`AssetModule.class` diffère entre pre.4 et pre.5).

### 1.5 Coût au démarrage

- Hytalor indexe chaque fichier JSON de chaque pack (`cacheAssetPaths`, `PatchManager.java:360-381`). La phase a pris 439 ms en pre.4 (journal : « Loading Hytalor Patch assets phase completed! Took 439ms »), et chaque patch environ 0,5 à 2,6 ms.
- Le pack des surcharges est rechargé au `BootEvent` (journal cité, l. 1151-1166 : « Took 318ms … to load all assets »).
- Hytalor laisse `mods/HytalorOverrides` en place s'il est retiré : les démarrages suivants avertissent « Skipping pack at HytalorOverrides: missing or invalid manifest.json » (`run/logs/2026-10-01_20-39-43_server.log`). C'est sans effet, mais on peut supprimer ce dossier dans `run/`.

## 2. Voies à l'exécution, sans fichier d'asset

### 2.1 `I18nModule` : aucune API d'écriture

- Méthodes publiques (`HY/server/core/modules/i18n/I18nModule.java`) :
  - `getUpdatePacketsForChanges` (l. 289-306) ;
  - `getMessages(String)` : la carte renvoyée est **non modifiable** (`Collections.unmodifiableMap`, l. 386-403) ;
  - `resolveLanguage` (l. 405-417) ;
  - `getMessages(Map, String)`, qui lit une carte fournie par l'appelant (l. 419-442) ;
  - `sendTranslations` (l. 444-448), `queueTranslations` (l. 450-454) et `getMessage` (l. 477-489).
- `addDefaultMessages` est privée (l. 308-330). Elle écraserait une clé en-US et diffuserait `UpdateTranslations` `AddOrUpdate`, mais elle n'est appelée que pour les libellés d'établis et les catégories d'artisanat de terrain (l. 105-155).
- Le champ `languages` est privé (l. 78). Une `.lang` de mod ne remplace pas une clé vanilla : la première définition gagne (l. 357-367 ; et le doublon dans un même fichier : `parser/LangFileParser.java:87-92`).
- `MessagesUpdated` (`event/MessagesUpdated.java`) n'est qu'une notification, émise après un rechargement de `.lang` par la surveillance de fichiers (l. 566-569) ou après `addDefaultMessages` (l. 323-328). Elle ne permet pas d'écrire.
- `GenerateDefaultLanguageEvent` sert à la commande `/i18n` qui génère les fichiers (`commands/GenerateI18nCommand.java:51-52`), pas au jeu.

### 2.2 Réécrire le paquet `UpdateTranslations` (voie possible)

- Le client reçoit **toute** sa table de traductions dans un `UpdateTranslations` `Init` :
  - à la connexion (`HY/server/core/io/handlers/SetupPacketHandler.java:227`) ;
  - à chaque changement de langue (`GamePacketHandler.java:598-606` → `queueTranslations` → `flushPendingLanguages`, `I18nModule.java:456-475`).
  - Ce paquet part par `writeNoCache` (`I18nModule.java:444-448`).
- Tout paquet sortant passe par `PacketAdapters.__handleOutbound` **avant** d'être sérialisé (`HY/server/core/io/PacketHandler.java:256-275`, et `279-286` pour les envois groupés).
  - Un plugin s'y branche avec `PacketAdapters.registerOutbound(PacketWatcher)` (`HY/server/core/io/adapter/PacketAdapters.java:30-38`). Cette version voit tous les `PacketHandler`, y compris celui de la connexion ; la version `PlayerPacketWatcher` ne voit que `GamePacketHandler` (l. 73-84).
  - La liste est statique : il faut se retirer à l'arrêt (`deregisterOutbound`, l. 92-96).
  - Aucun module vanilla ne s'en sert (grep `PacketAdapters.register`).
- `UpdateTranslations.translations` est un champ public et non `final` (`HY/protocol/packets/assets/UpdateTranslations.java:26-30`). Mais la carte `Init` est la carte en cache, partagée et non modifiable. Il faut donc **remplacer le champ par une copie** enrichie, jamais modifier la carte.
- La carte `Init` est déjà dans la langue du joueur, avec le repli en-US fusionné (l. 419-442), et elle contient nos clés `hycolony.*`. Le filtre peut donc, sans connaître la langue :
  1. lire `server.items.<id>.description` ;
  2. lire le modèle de ligne `hycolony.…` ;
  3. remplacer lui-même `{p0}`… ;
  4. écrire `vanilla + "\n\n" + ligne`.
- Limites :
  - les 8 aliments sans clé de description (§ 1.3) envoient `description = null` au client (`ItemTranslationProperties.java:146-149`). Le serveur, lui, se replie sur `server.items.<id>.description` (`Item.java:971-981`) ; que le client fasse de même : **[in-game]** ;
  - le filtre tourne hors du thread du monde (exécuteur planifié, `I18nModule.java:161-162`, ou futur de `SetupPacketHandler`). Il ne doit lire qu'une table immuable (CLAUDE.md § 4) ;
  - il voit **chaque** paquet sortant de chaque joueur. Le test `instanceof UpdateTranslations` est peu coûteux, et la copie d'environ 20 000 entrées n'a lieu qu'une fois par connexion ou changement de langue ;
  - un `AddOrUpdate` envoyé après un rechargement à chaud d'une `.lang` (l. 549-564) ne passe que les clés modifiées. Notre texte reste juste tant que la `server.lang` vanilla ne change pas (le pack de base est immuable, donc pas surveillé, l. 245) ;
  - c'est un branchement sur le réseau, pas une API de contenu : un changement du protocole le casse sans erreur de compilation visible. **[in-game]** : affichage effectif.

### 2.3 Recharger l'`Item` avec une autre clé (possible, déconseillé)

- `Item` n'a pas de mutateur pour `translationProperties` : le champ est `protected` (`Item.java:555`). `ItemTranslationProperties` a un constructeur public `(name, description)`, qui laisse les arguments à `null` (`ItemTranslationProperties.java:100-103`). `Item(Item other)` est public et copie tout, `blockId` et `hasBlockType` compris (`Item.java:654-709`).
- Une sous-classe pourrait donc poser la clé, puis appeler `Item.getAssetStore().loadAssets(...)` hors du thread du monde (`docs/research/plugin-b-api.md` § 17). Le serveur diffuse alors `UpdateItems` `AddOrUpdate` (`docs/research/domum-ornamentum.md:455`).
- Risques :
  - une sous-classe d'`Item` vit alors dans la carte des assets ;
  - tous les écouteurs de `LoadedAssetsEvent<Item>` se relancent ;
  - le verrou d'assets doit être évité.
- Le paquet `ItemBase` est mis en cache (`Item.java:712-902`). `invalidatePacketCache()` est public (l. 633-635), mais ne change pas la clé.

### 2.4 Métadonnées par pile

- `ItemStack` sait lire une métadonnée `ItemDisplay` qui porte un `Name` et une `Description` de type `Message` (`HY/server/core/asset/type/item/config/metadata/ItemDisplayMetadata.java:8-21` ; `ItemStack.getDisplayName`/`getDisplayDescription`, `HY/server/core/inventory/ItemStack.java:352-362`). Le client reçoit la métadonnée en JSON (`HY/protocol/ItemWithAllMetadata.java:26`).
- Aucun code serveur ne l'écrit, et aucun asset (`zip:Server/**/*.json`) ne l'utilise. Que le client l'affiche dans l'infobulle : **[in-game]**.
- Il faudrait marquer chaque pile d'aliment, et une métadonnée différente empêche deux piles de s'empiler (`ItemStack.isStackableWith` compare la métadonnée, `ItemStack.java:305-319`). C'est à écarter pour ce besoin.

## 3. Ce que montre l'infobulle d'un aliment

### 3.1 Contenu

- Le paquet d'objet (`ItemBase`) porte la clé du nom, la clé de la description et leurs arguments (`HY/protocol/ItemTranslationProperties.java:22-28`), la qualité (`Item.toPacket`, `Item.java:712-902`), et le reste de la définition. Le texte vient de la table de traductions du client.
- Les fonds d'infobulle changent avec la qualité (`zip:Common/UI/ItemQualities/Tooltips/ItemTooltip<Qualité>@2x.png`). La qualité est donc affichée par la couleur du cadre **[in-game]**.
- Les effets (`InteractionVars.Effect`, par exemple `Food_Instant_Heal_Bread`, `zip:Server/Item/Items/Food/Food_Bread.json`) ne semblent pas listés automatiquement : les descriptions vanilla les écrivent à la main. Exemple : `items.Food_Bread.description = [TMP] Instantly restores <color is="#ffffff">10%</color> health.` (`zip:Server/Languages/en-US/server.lang:10089` ; fr-FR l. 7732). **[in-game]**
- Les descriptions vanilla commencent souvent par `[TMP] `. Le serveur ne le retire que dans `getMessage` (`I18nModule.java:487-488`), alors que la table `Init` l'envoie brut. Le client le masque probablement selon son réglage **[in-game]**. Par `<msg key>`, notre texte le traitera comme le vanilla.

### 3.2 Plusieurs lignes, couleurs, balisage

- `\n` et `\t` dans une valeur `.lang` deviennent un vrai saut de ligne et une vraie tabulation (`HY/server/core/modules/i18n/parser/LangFileParser.java:22-25`). Une valeur peut aussi continuer sur la ligne suivante avec un `\` final (l. 54-75).
- La `server.lang` en-US contient 11 498 lignes avec `\n`. Balises relevées :
  - `<color is="#…">` (484 fois) ;
  - `<item is="…"/>` (70) ;
  - `<i>` (68) ;
  - `<msg key="…"/>` (11) ;
  - `<b>` (3).

  Exemples : `items.Plant_Fruit_Apple.description` (`server.lang:10041`, puces `•` et `\n\n`), `memories.general.chestLocked.tooltipText` (l. 10517, `<b>` dans `<color>`).
- `<msg key="…"/>` insère une autre clé **dans une description d'objet**. Exemple : `items.PortalKey_Taiga.description = <msg key="server.items.PortalKey.description"/>\n\n<i>…</i>` (`server.lang:8047`), et l'objet `zip:Server/Item/Items/Portal/PortalKey_Taiga.json` pointe sur cette clé. Aucun `<msg>` vanilla n'a d'autre attribut que `key` : rien ne prouve qu'il passe des paramètres.

### 3.3 Arguments de description

- `TranslationProperties.DescriptionArguments` existe (table nom → clé de traduction, `ItemTranslationProperties.java:73-81`). Il est envoyé en `FormattedMessage` (l. 158-164), comme `NameArguments`.
- `NameArguments` sert à 13 objets vanilla, par exemple `Recipe_Plant_Seeds_Health1.json` : `"Name": "server.items.RecipeItem.name"` = « Recipe: {itemName} » (`server.lang:8243`).
- **Aucun** objet vanilla n'utilise `DescriptionArguments` : son affichage par le client est **[in-game]**. Les valeurs sont des clés, pas des nombres (`wrapKeys`, l. 125-143).

## 4. Recommandation

**Voie retenue : Hytalor, en dépendance optionnelle.**

- Générer, depuis `food.foods` de l'id-map (un outil sous `tools/`, avec un contrôle de dérive au build) :
  1. `plugin/src/main/resources/Server/Patch/HyColony/Food/<id>.json` : le patch du § 1.1, avec le chemin vanilla exact (§ 1.3) ;
  2. dans `hycolony_food.lang` en-US et fr-FR (fichier généré à part), une clé `<id>.description` (soit `hycolony_food.<id>.description`) qui vaut :
     - pour les 42 aliments décrits : `<msg key="server.items.<id>.description"/>\n\n<color is="#…">Nourrit un citoyen : 6 · Palier 1</color>` ;
     - pour les 8 autres : la ligne seule.
- Le texte vanilla reste dans toutes les langues : `<msg>` le résout dans la langue du joueur, et nos clés absentes d'une langue retombent sur l'en-US (`I18nModule.java:419-442`).
- Les chiffres étant littéraux dans la `.lang`, ils doivent être générés et non écrits à la main. On peut aussi factoriser une clé de ligne par combinaison (nutrition, palier, cru, poison), insérée par un second `<msg key>` : la valeur par aliment ne contient alors que des balises.
- Coût : un générateur, 50 patchs, 50 × 2 lignes de `.lang`, une entrée `OptionalDependencies`. Aucun code Java dans le plugin. Pas de règle de jeu dans le plugin : les valeurs viennent de l'id-map.
- Risques :
  - **dépendance externe** : la source publique de Hytalor (2.1) ne compile plus contre pre.4/pre.5 (§ 1.4). La 2.3 marche en pre.4, mais annonce `<0.6.0`, et son code est fermé. Une mise à jour de Hytale peut la casser ; HyColony démarre alors quand même (dépendance optionnelle), sans la ligne ;
  - **autres mods** : un autre patch Hytalor sur le même aliment fusionne avec le nôtre. Seul un patch qui touche aussi `TranslationProperties.Description` entre en conflit, réglé par `_priority`. Un mod qui remplace le fichier entier dans son pack devient la base du patch : Hytalor prend le dernier pack qui fournit le chemin (`PatchManager.java:360-372`, `put` écrase). Notre clé s'applique dessus ;
  - **mises à jour du jeu** : Hytalor relit le JSON vanilla à chaque démarrage, donc les autres changements vanilla passent. Seuls un aliment renommé ou déplacé, ou une clé de description vanilla renommée, demandent de régénérer. Le patch d'un chemin disparu journalise « No base assets found » et ne fait rien (`PatchManager.java:173-178`) ;
  - **[in-game]** à vérifier : Hytalor en pre.5, le `BlockType` d'un aliment patché (posé au sol), `<msg key>` dans une valeur de `hycolony_food.lang`, et le rendu de `<color>` (l'absence d'effet de `Server/Patch/` sans Hytalor est vérifiée dans les sources, § 1.4).

**Alternative sans dépendance** : le filtre `UpdateTranslations` du § 2.2.

- Coût : environ une classe d'adaptateur.
- Avantages : rien à générer côté assets, le texte vanilla est gardé dans toutes les langues.
- Inconvénients : un branchement réseau non documenté, et 8 aliments sans ligne si le client ne cherche pas de clé de description par défaut.
