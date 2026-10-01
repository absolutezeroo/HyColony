# Client Hytale : balisage des textes d'infobulle (analyse du binaire)

Question : le balisage des descriptions d'objet accepte-t-il une image ou une icône en ligne, pour afficher de petits pilons dans l'infobulle d'un aliment (`food-tooltips.md`) à la place du mot ? Réponse : **non**. L'analyseur qui sert à l'infobulle d'objet ne connaît aucune balise d'image, et aucune police du jeu n'a de glyphe de nourriture.

Analyse faite le 2026-10-01, à but d'interopérabilité. Lecture seule : rien n'est modifié ni redistribué. On reprend la méthode de `client-block-atlas.md` (chaînes UTF-16 figées, `lea [rip+…]`, table `.pdata` pour les bornes de fonction, noms dans les métadonnées de réflexion).

## Binaire étudié

- `%APPDATA%/Hytale/install/pre-release/package/game/latest/Client/HytaleClient.exe` : 72 274 432 octets, daté du 2026-10-01, SHA-256 `006f34c19617f03a…`. Il contient le littéral `0.7.0-pre.5` (offset de fichier `0x3d83bc4`).
- Sections : `.text` RVA `0x1000` (fichier `0x400`), `.data` RVA `0x3d55000` (fichier `0x3d53400`). Une chaîne figée commence 12 octets avant ses caractères (table des méthodes, puis longueur).
- Les adresses ci-dessous sont des RVA de ce binaire.

## 1. Deux analyseurs de balisage

Le client a **deux** analyseurs distincts. Chacun teste, à la position courante, `Substring(pos, n) == "<balise…"`, d'où les littéraux complets (`<b>`, `</b>`, `<color is="#`…).

### a. Analyseur de l'interface `.ui` (`0x371400`, fin `0x372f51`)

C'est celui des `Label` de l'interface du jeu, donc de l'infobulle d'objet (voir § 3).

- Il remplace d'abord `\r\n` par `\n` (`0x3714e1`).
- Un `\` échappe le caractère suivant (`0x3716c0`, puis `0x372dd7`).
- Balises reconnues, dans l'ordre des tests :

| Balise | Littéraux | Effet |
|---|---|---|
| `<b>` `</b>` | `0x371612`, `0x37179d` | gras |
| `<i>` `</i>` | `0x37186d`, `0x371992` | italique |
| `<u>` `</u>` | `0x371a62`, `0x371b87` | souligné |
| `<s>` `</s>` | `0x371c57`, `0x371d7c` | barré |
| `<color is="#…">` ou `<color is=#…>`, `</color>` | `0x371e54`, `0x371f13`, `0x3723bf` | couleur ; les guillemets sont facultatifs |
| `<a href="…">` `</a>` | `0x372494`, `0x372676` | lien |
| `<item is="ID"/>`, avec `displayName="…"` en option | `0x372725`, `0x372868`, `0x3729a9` | nom d'objet (§ 2) |
| `<msg key="…"/>` | `0x372b44`, `0x372c2b` | insère une autre clé de traduction |

- Une fermeture compare son nom au sommet de la pile des balises ouvertes (`"b"`, `"i"`, `"u"`, `"s"`, `"color"`, `"a"`).
- **Une balise inconnue** (`<img …>` par exemple) fait sortir de la boucle (`0x372dea`) : le reste de la chaîne, depuis le `<`, est ajouté tel quel comme texte (`Substring(pos)`, `0x372e11`, puis `0x373da0`). Le balisage qui suit n'est plus lu. Déduit du désassemblage : **[in-game]**.
- `<number>`, `<weight>`, `<uuid>`, `<username>`, `<type>` ne sont **pas** des balises. Dans `server.lang`, ce sont des textes d'aide de commandes (`/auth select <number>`, l. 4015-4052 du zip pre.5). Les balises réellement employées par `Server/Languages/en-US/server.lang` (zip `pre-release-0.7.0-pre.5-Assets.zip`) sont `color` (968), `i` (136), `item` (70), `msg` (11), `b` (6) et `s` (2). `client.lang` (`Client/Data/Shared/Language/en-US/`) ajoute `<u>` et `<a>`. Aucun `.lang` n'emploie `<img>`.

### b. Analyseur des vues Noesis (`0x2844a0`, fin `0x286052`)

Il sert aux pages XAML (Noesis : éditeur, navigateur de mods, serveurs, chapitres… ; `Client/Data/Shared/UI/**.xaml`), pas à l'interface `.ui` du jeu.

- Balises : `<b>`, `<i>`, `<u>`, `<s>`, `<color is="#…">` (guillemets obligatoires), `<a href="…">`, `<msg key="…"/>` et **`<img src="…" width="…" height="…"/>`** (`0x285792`, `0x28599e`, `0x285a94`). Il n'a pas `<item>`.
- Ses seuls appelants (`0x2832c0`, `0x283b60`, `0x284400`) sont voisins des messages « FormattedMessage attached property can only be used on TextBlock elements. » (`0x283925`) et « Invalid image URI in FormattedMessage: {0} » (`0x283e99`). Les métadonnées nomment ce code `RenderFormattedMessage`, `AppendMessage`, `AppendImageInline`, `AppendTextInline` et `ParseMarkupInlines` (offset de fichier `0x225d918`).
- Ce que vaut `src` (URI Noesis, fichier du client, asset `Common/` d'un pack serveur ?) n'est pas étudié : **[in-game]**. Sans objet pour l'infobulle d'objet.

## 2. Ce que rend `<item is="…"/>`

- L'analyseur construit un objet `{ tagType: "Item", id, brackets, displayName }` (`0x372a01` à `0x372aa5`), puis le rend par `0x372f80` :
  - il cherche l'objet par son id (`0x4ef210`, `0x3739e0`) ;
  - le texte est `displayName` s'il est donné, sinon le nom traduit de l'objet (`0x7d3380`, puis `0x370fe0`) ;
  - la couleur est celle de la **qualité** de l'objet (index de qualité lu en `+0x130`, couleur en `+0x44`, `0x373a4e`-`0x373a74`) ;
  - le texte est encadré de `[` `]`, sauf si `brackets` vaut faux (`0x373a87`-`0x373ad8`) ;
  - il est ajouté comme **span de texte** (`0x373da0`, la même fonction que pour le texte simple).
- Un autre type de balise donne `[Unrecognized tag type: …]` (`0x3738bb`).
- Donc `<item is="X"/>` affiche **« [Nom de l'objet] » en texte, à la couleur de sa qualité, sans icône**. L'analyseur n'offre aucune façon de mettre `brackets` à faux. Rendu exact (crochets compris) : **[in-game]**.

## 3. L'infobulle d'objet passe par l'analyseur `.ui`

- L'infobulle est `Client/Data/Game/Interface/InGame/Tooltips/ItemTooltip.ui`. La description y est un `Label #Label` dans `Group #Description`, avec `Wrap: true`, `FontSize: 14` et `TextColor: #696969`.
- `0x400a20` charge ce `.ui` (`"InGame/Tooltips/ItemTooltip.ui"`, `0x400ad2`) et lit ses éléments (`Description`, `Stats`, `StatsContent`, `StatHealthValue`…).
- `0x401030` remplit l'infobulle (`client.itemTooltip.id`, `client.itemTooltip.consumable`, `server.ui.slotName.`…). Il appelle l'analyseur `.ui` `0x371400` (`0x402416`, `0x402b26`) et son moteur de rendu `0x372f80` (`0x402149`, `0x40249f`). Il n'appelle jamais l'analyseur Noesis.
- Les spans d'un `Label` `.ui` sont du texte (`LabelSpan`, `LabelSpanPortion`, `TextParserSpan`, `GetFontTypeForSpan` dans les métadonnées). Aucun type de span image n'existe de ce côté. Les images en ligne n'existent que côté Noesis (`AppendImageInline`, `InlineUIContainer`).

## 4. Polices : pas d'émoji

- Polices chargées par le jeu (`0x84c4c0`) : `NunitoSans-Medium.ttf`, `NunitoSans-ExtraBold.ttf`, `Lexend-Bold.ttf`, `NotoSans-Bold.ttf`, `NotoMono-Regular.ttf` et `ChironHeiHK-Medium.ttf`. Il y a aussi les polices CJK `NotoSans{JP,KR,SC,TC}-Medium.ttf`. Toutes sont dans `Client/Data/Shared/UI/Fonts/`. Les `.ui` les désignent par `FontName: "Default"` ou `"Secondary"`.
- Couverture lue avec fontTools :
  - **aucune** police n'a U+1F357 (🍗) ni U+1F356 (🍖), ni aucun émoji de nourriture (U+1F345-U+1F37E) ;
  - `NunitoSans-Medium` (939 glyphes) n'a, au-delà du latin, que des exposants, `™`, `№`, `Ω`, `∞`, `≈`, `≤`, `≥`, `√`, `◊`, `•` (U+2022) et quelques signes mathématiques. Il n'a ni `♥`, ni `★`, ni `●`.
  - les polices CJK ont `♥` (U+2665), `★` (U+2605) et `●` (U+25CF). Leurs glyphes ≥ U+1F000 sont surtout des lettres encerclées (U+1F100-U+1F1AC) ;
  - le client se replie-t-il sur une police CJK pour un texte en-US ? **[in-game]**
- Le client ne charge pas de police fournie par un pack serveur : seuls ces fichiers sont référencés.
- `Client/Data/Shared/NunitoSans-VariableFontGlyphs.json` (atlas de 448 glyphes : ASCII, Latin-1, cyrillique U+0400-U+04FF) n'a pas non plus d'émoji.

## 5. Le message formaté porte une image, mais pas pour l'infobulle

- Le protocole a `FormattedMessage.image` (`build/vineflower/hytale-server/com/hypixel/hytale/protocol/FormattedMessage.java:47-48`), de type `FormattedMessageImage { filePath, width, height }` (`protocol/FormattedMessageImage.java:17-20`).
- `Message` n'a aucune méthode pour la remplir, mais `new Message(FormattedMessage)` est public (`server/core/Message.java:67`).
- Côté client, seul le rendu Noesis l'affiche (`AppendImageInline`). La description d'objet n'est pas un `FormattedMessage` envoyé par le serveur : c'est une clé (`ItemTranslationProperties`), résolue par le client puis passée à l'analyseur `.ui`. `DescriptionArguments` sont des `FormattedMessage`, mais leur rendu dans l'infobulle passe aussi par `0x372f80`, sans span image. Image ignorée ou non : non vérifié, **[in-game]**.

## 6. Conclusion : des pilons dans l'infobulle ?

**Pas possible en ligne dans la description**, pour trois raisons :

- l'analyseur `.ui` de l'infobulle n'a pas de balise image (`<img>` n'existe que dans l'analyseur Noesis) ;
- `<item>` rend un nom, pas une icône ;
- aucune police n'a de glyphe de nourriture.

Pire, une balise inconnue afficherait la suite de la description en texte brut.

Voies possibles, de la plus simple à la moins recommandable :

1. **Texte**, ce qu'on fait déjà : un nombre et un mot (« Nourrit un citoyen : 6 »), avec `<color>` et `<b>`. Un symbole présent dans `NunitoSans-Medium` (`•`, `◊`) pourrait servir de jauge (`•••••• `), sans être un pilon. Rendu : **[in-game]**.
2. **Dans nos propres fenêtres `.ui`** (hutte du restaurant, etc.) : une icône est un `Group` avec `Background: (TexturePath: …)`, comme les lignes de stats de `ItemTooltip.ui` (`CharacterPanelStatIconHealth.png`…). On peut y mettre de vraies icônes de pilon, mais pas dans l'infobulle d'objet.
3. **Non recommandé** :
   - `ItemQuality.itemTooltipTexture` / `itemTooltipArrowTexture` (`protocol/ItemQuality.java:21-23`) change le **fond** étiré (9-slice, `Border: 24`) de l'infobulle pour toute une qualité. Ce n'est pas une icône en ligne, cela change aussi la qualité affichée, et il faudrait une qualité par valeur ;
   - les lignes `#StatHealth`, `#StatDefense`, `#StatMana`, `#StatStamina` et `#StatAttack` de l'infobulle ont des icônes fixes, remplies par le client depuis les données de l'objet. Leur source n'est pas étudiée ici, et les détourner changerait le jeu.

## 7. Séparateur : un trait de caractères

Ajouté le 2026-10-01 pour les infobulles des aliments (`tools/food/generate.py`). Le balisage n'a pas de balise de séparateur (§ 1).

- **Le trait du jeu** est `@Separator` de `Client/Data/Game/Interface/InGame/Tooltips/ItemTooltip.ui` : un `Group` de 1 px de haut, `Background: (Color: #25262c)`, placé au-dessus de la description (`Group #Description`).
- **La largeur du contenu** : `@MinWidth = 320`, `Padding: (Full: 24, Top: 21)`, donc au moins 320 − 2 × 24 = 272 px ; l'infobulle s'élargit jusqu'à `@MaxWidth = 480` selon son texte.
- **La police de la description** : `Label #Label`, `FontSize: 14`, sans `FontName`, donc la police par défaut, Nunito Sans. Le client dessine depuis l'atlas MSDF `Client/Data/Shared/UI/Fonts/NunitoSans-Medium.json` / `.png`, pas depuis le TTF.
- **U+2015** (barre horizontale) est dans cet atlas, avec `advance: 1` em et une encre de 0 à 1 em une fois retirée la marge du champ de distance : les barres se touchent bord à bord, et chacune fait 14 px à la taille 14. Leur épaisseur est d'environ 0,054 em, soit à peu près 1 px. L'atlas n'a pas de crénage (`kerning` vide). U+2015 est aussi dans les atlas des autres polices de l'interface.
- **Choix** : 18 barres (252 px) dans la couleur `#25262C`. 19 (266 px) tiendraient aussi à l'échelle 1, mais si le client arrondit l'avance de chaque glyphe au pixel à une échelle d'interface non entière, 19 barres dépasseraient (à ×1,25 : 19 × 18 = 342 px pour 340 px de contenu). Rendu et arrondi : **[in-game]**.

## Adresses (pour refaire l'analyse sur une autre version)

| Rôle | RVA |
|---|---|
| Analyseur `.ui` (balisage → spans) | `0x371400` |
| Rendu d'une balise de type (`Item`) et des paramètres | `0x372f80` |
| Ajout d'un span de texte | `0x373da0` |
| Analyseur Noesis (avec `<img>`) | `0x2844a0` |
| Chargement de `ItemTooltip.ui` | `0x400a20` |
| Remplissage de l'infobulle d'objet | `0x401030` |
| Liste des polices du jeu | `0x84c4c0` |
| Formatage des paramètres (`number`, `plural`, `select`, `date`…) | `0x337720` |
