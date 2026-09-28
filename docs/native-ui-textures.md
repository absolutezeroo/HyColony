# Textures d'interface copiées du client Hytale

Le dossier `blockui/src/main/resources/Common/UI/Custom/Pages/HyBlockUI/Native/`, dans le pack du mod HyBlockUI, contient des textures **copiées du client Hytale 0.6.8**, depuis `Client/Data/Game/Interface/InGame/Pages/Inventory/`. Elles sont © Hypixel Studios et **ne sont pas couvertes par la licence GPL du projet**.

Pourquoi : une page personnalisée ne peut charger que les textures du pack d'assets envoyé par le serveur. Elle n'a pas accès aux fichiers d'interface du client (vérifié en jeu le 2026-09-28 avec 4 chemins différents, voir « Textures d'une page personnalisée » dans `docs/research/plugin-b-api.md` § 7). Pour que l'établi et les futures fenêtres d'inventaire ressemblent exactement à l'inventaire natif, on réutilise donc ces images. Le mod ne tourne que dans Hytale, où chaque joueur a déjà ces fichiers.

Les `.ui` des autres mods (HyColony, HyDomum) les citent par `../HyBlockUI/Native/<nom>.png`.

Ce dossier ne contient que ces copies, rien d'autre. On n'ajoute une texture que si une fenêtre s'en sert, et on la note ci-dessous. Avant toute publication du mod, vérifier la politique de Hytale sur les mods.

| Fichier | Sert à |
|---|---|
| `Slot` | cases d'inventaire |
| `CharacterBackground` | fond du personnage |
| `ArmorSlotIconHead`, `Chest`, `Hands`, `Legs` | silhouettes des cases d'armure vides |
| `SlotInputBindingBackground` | badges 1 à 9 de la barre rapide |
| `IngredientSlot`, `IngredientSlotValid` | emplacements de l'établi : gris, puis vert avec coche une fois bien remplis |
