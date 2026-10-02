# Blockymodel Viewer : aperçu Blockbench des `.blockymodel` dans IntelliJ IDEA

Date : 2026-10-02. Statut : design validé dans la conversation, spec à relire.

Outil de développement, hors des cinq mods : il vit dans son propre dépôt
(`C:\Users\Ctuto\Desktop\BlockymodelViewer`). Les règles de `CLAUDE.md` propres aux mods (cœurs Java purs,
`checkModApis`, fidélité à MineColonies…) ne s'y appliquent pas. Cette spec est rangée ici parce que l'outil sert au
travail sur les modèles de HyColony, HyVanilla et HyDomum.

## 1. But

Ouvrir un `.blockymodel` dans IntelliJ IDEA et le voir rendu **par le moteur de Blockbench installé**, avec le
plugin Hytale officiel, à côté du JSON, et suivre les modifications en direct. Le rendu doit être celui de
Blockbench : même code, mêmes textures, même ombrage. Ce n'est pas une réimplémentation.

Succès : en ouvrant `plugin/src/main/resources/Common/Blocks/HyColony/Huts/Builder.blockymodel`, on voit le banc du
bâtisseur texturé comme dans Blockbench ; une modification du JSON ou de `Builder.png` apparaît sans rien relancer.

## 2. Étude de faisabilité (2026-10-02)

Faite sur Blockbench 5.2.1 (`%LOCALAPPDATA%\Programs\Blockbench`), son plugin `hytale_plugin.js`
(`%APPDATA%\Blockbench\plugins`) et Edge headless (Chromium, comme JCEF), avec du code jetable.

- `resources/app.asar` contient `index.html` et `dist/bundle.js` (9 Mo). Ce bundle est compilé pour Electron :
  `isApp` vaut `true` en dur, et le chargement appelle `require` pour `node:fs`, `node:path`, `os`, `electron`,
  `buffer`, `zlib`, `child_process`, `https`, et `@electron/remote` (`getCurrentWindow()`, `app.getPath()`).
- Dans un navigateur, il démarre (`Blockbench.setup_successful`, aucune erreur) si l'on injecte avant lui une couche
  « faux Node » :
  - `window.require` renvoie des modules factices (un `Proxy` qui renvoie un factice pour toute propriété ou tout
    appel) ; `os` donne de vraies chaînes (`version()`, `platform()`…) ;
  - `electron.ipcRenderer.sendSync` renvoie `{type: "value", value: <factice>}`, que `@electron/remote` accepte ;
  - `process` (avec `versions.electron` ≥ 13 et un `contextId`), `global = window`, `Buffer` minimal ;
  - un `fs` virtuel : lectures par requête **synchrone** vers l'hôte (`readFileSync`, `statSync`, `readdirSync`,
    `existsSync`, et leurs formes à rappel ou `promises`), écritures gardées en mémoire ;
  - le setter de `HTMLImageElement.src` réécrit un chemin `C:/…` en URL de l'hôte (le plugin Hytale donne le chemin
    disque de la texture, que le navigateur lirait comme un schéma `c:`).
- Le dossier des plugins vaut `/plugins/` (`app.getPath` est factice) : l'hôte le fait pointer sur le vrai dossier.
- `new Plugin("hytale_plugin").loadFromURL(…)` charge le plugin ; `Codecs.blockymodel.load(json, {path})` charge le
  modèle au format `hytale_prop` (le chemin contient `Blocks`) ; le plugin trouve seul la texture (`Builder.png`,
  256×96) par le `fs` virtuel.
- Interface masquée : `BarItems.toggle_sidebars.click()` puis `display: none` sur `header`, `#title_bar`, `#tab_bar`,
  `#main_toolbar`, `.toolbar_wrapper`, `#status_bar`, `#panel_selector_bar`, puis `resizeWindow()`.
- Mesures : démarrage du moteur sous la seconde ; `Project.close(true)` puis rechargement d'un autre modèle en 191 ms.

Risque accepté : la couche « faux Node » suit les appels Node/Electron du bundle. Si une version de Blockbench en
ajoute un, on ajoute une ligne à la couche ; l'aperçu affiche l'erreur JS en attendant.

## 3. Expérience

- Un `.blockymodel` s'ouvre dans un éditeur partagé (`TextEditorWithPreview`) : JSON à gauche, rendu à droite, avec
  les trois modes d'IntelliJ (texte, partagé, aperçu).
- Le rendu est le viewport de Blockbench seul. Souris comme dans Blockbench (tourner, zoomer, déplacer). La caméra est
  conservée d'un rechargement à l'autre.
- L'aperçu suit le **texte du document**, 300 ms après la dernière frappe, sans attendre la sauvegarde. Un JSON
  invalide garde le dernier rendu valide.
- Il suit les **textures** : un `.png` modifié dans le dossier du modèle (ou son dossier `<Modèle>_Textures`)
  recharge l'aperçu.
- Action « Ouvrir dans Blockbench » (menu de l'éditeur et clic droit sur le fichier) : lance `Blockbench.exe <fichier>`.
- Blockbench introuvable, `app.asar` ou `hytale_plugin.js` absent, ou échec du démarrage : l'aperçu affiche un message
  (raison, et l'erreur JS s'il y en a une) à la place du rendu. L'éditeur texte reste normal.

Hors périmètre : modifier le modèle depuis l'aperçu, les animations (`.blockyanim`), macOS et Linux (chemins Windows
seulement pour l'instant).

## 4. Architecture

Plugin IntelliJ en Kotlin, IntelliJ Platform Gradle Plugin 2.x, cible IDEA 2026.2 et suivantes. JCEF est fourni par
l'IDE (`JBCefApp.isSupported()` vérifié ; sinon, message dans l'aperçu).

### 4.1 Côté IntelliJ

| Classe | Rôle |
|---|---|
| `BlockbenchInstall` | Trouve l'installation (réglage, sinon `%LOCALAPPDATA%\Programs\Blockbench`), `resources/app.asar`, la version (`package.json` de l'asar) et le dossier `%APPDATA%\Blockbench\plugins`. Renvoie l'installation ou la raison de son absence. |
| `AsarArchive` | Lit l'en-tête JSON de l'asar (taille à l'octet 12, données après `8 + uint32@4`) et renvoie les octets d'une entrée. Sans dépendance. |
| `BlockbenchRequests` | Gestionnaire de requêtes du navigateur (`CefRequestHandler` → `CefResourceRequestHandler`). Sert l'hôte fictif `http://blockbench.localhost/` et bloque toute autre requête. |
| `BlockymodelPreview` | `FileEditor` contenant un `JBCefBrowser`. Envoie le texte au JS (300 ms d'attente), écoute le VFS pour les `.png`, affiche les erreurs reçues par `JBCefJSQuery`. |
| `BlockymodelEditorProvider` | `FileEditorProvider` pour l'extension `blockymodel` : `TextEditorWithPreview(texte, aperçu)`. |
| `OpenInBlockbenchAction` | Lance `Blockbench.exe` sur le fichier. |
| `BlockbenchSettings` | `PersistentStateComponent` et page de Settings : chemin de Blockbench. |

Routes de `BlockbenchRequests` :

| Route | Contenu |
|---|---|
| `/bb/index.html` | `index.html` de l'asar, avec `<script src="/viewer/node-shim.js">` avant le bundle et `<script src="/viewer/viewer.js" type="module">` après. |
| `/bb/…` | Entrée de l'asar. |
| `/viewer/…` | Ressources JS du plugin IntelliJ. |
| `/fs/read`, `/fs/stat`, `/fs/list` (`?p=`) | `fs` virtuel en **lecture seule**. `/plugins/…` désigne le dossier des plugins de Blockbench. |
| `/C:/…` (chemin disque) | Fichier (textures). |

Accès disque limité aux racines de contenu du projet ouvert et au dossier des plugins de Blockbench ; hors de ces
limites : 404. Aucune requête ne sort de la machine : pas de vérification de mise à jour ni de magasin de plugins.

### 4.2 Côté navigateur (ressources du plugin)

- `node-shim.js` : la couche « faux Node » du § 2, nettoyée.
- `viewer.js` : attend `Blockbench.setup_successful`, charge `hytale_plugin.js`, masque l'interface, expose
  `showModel(path, jsonText)` (ferme le projet courant, charge le nouveau, rétablit la caméra) et
  `reloadTextures()`. Remonte toute erreur (`window.ErrorLog`, exceptions de `showModel`) au Kotlin.

### 4.3 Flux

1. Ouverture du fichier : `BlockymodelPreview` crée le navigateur sur `http://blockbench.localhost/bb/index.html`.
2. Démarrage : `viewer.js` signale « prêt » ; Kotlin appelle `showModel(chemin, texte du document)`.
3. Frappe : chaque changement du document relance une attente de 300 ms, puis `showModel`.
4. Texture : un événement VFS sur un `.png` du dossier du modèle appelle `reloadTextures()`.
5. Fermeture de l'onglet : le navigateur est libéré (`Disposer`).

Chaque onglet d'aperçu démarre son propre Blockbench : moins d'une seconde, environ 100 à 150 Mo de mémoire.

## 5. Tests

- JUnit : `AsarArchive` (sur une petite archive de test du dépôt) ; routes et limites de `BlockbenchRequests` (chemin
  hors limites refusé, hôte étranger bloqué, `index.html` modifié) ; `BlockbenchInstall` (réglage, défaut, absence).
- Test de démarrage sur le vrai `app.asar` (ignoré si Blockbench n'est pas installé) : le bundle démarre sans erreur
  et charge un modèle de test, comme dans l'étude.
- `buildPlugin` et `verifyPlugin` verts.
- Essai en IDE par l'utilisateur (`runIde` ou zip installé) : Builder et TownHall de HyColony, un pot de HyVanilla,
  modification du JSON en direct, `bake.py` puis rechargement de la texture, Blockbench absent (chemin faux).
