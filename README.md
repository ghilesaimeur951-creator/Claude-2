# Street Blocks 🧱💪

Application Android de **street workout** : tu construis tes séances en **empilant des blocs** (comme un langage de programmation par blocs), puis l'appli **exécute la séance toute seule** — minuteur, séries, repos, annonces vocales et signaux sonores puissants pour l'extérieur.

100 % hors ligne · Kotlin · Jetpack Compose · MVVM · Room.

## Télécharger l'APK

➡️ Onglet **Releases** du dépôt → dernier `StreetBlocks.apk`.
Sur le téléphone : ouvrir le fichier, autoriser « installer des applis inconnues » si demandé.
Chaque push sur `main` recompile l'APK via GitHub Actions et publie une nouvelle release.

## Fonctionnalités

**Créateur de séance par blocs**
- Blocs emboîtables colorés : Échauffement, Tractions, Dips, Pompes, Statique, Exercice libre, Repos, Fin de séance
- Glisser-déposer (poignée ⠿), monter/descendre, dupliquer, supprimer, configurer
- Bibliothèque de blocs, durée totale estimée, sauvegarde automatique
- « Ajouter un repos entre chaque exercice » en un geste

**Paramètres des blocs**
- Tractions : 9 variantes (classiques, australiennes, négatives, sautées, explosives, archer, L-sit, muscle-up, scapulaires), 5 hauteurs de barre, 6 prises
- Dips : parallèles serrées / moyennes / larges, barre droite, Russian dips…
- Pompes : sol, pieds surélevés sur plateforme, mains sur barre, variantes
- Charge : poids du corps, lesté (gilet 1–20 kg + ceinture avec tes poids 21 / 24 kg), assisté élastique
- Séries, répétitions ou durée, repos entre séries, tempo / temps sous tension, notes

**Mon matériel**
- Élastiques type Decathlon (base modifiable : couleur, résistance, exercices compatibles)
- Gilet lesté, poids blancs 21 kg / noirs 24 kg, ceinture lestée
- Parc : 5 hauteurs de barres, parallèles 3 écartements, barre droite basse, sol, plateformes 30 cm
- Le créateur ne propose que le matériel que tu possèdes

**Exécution automatique**
- Plein écran, écran toujours allumé, textes géants, couleurs par phase (effort / repos / préparation)
- Exercice, série X/Y, répétitions, charge, équipement, minuteur géant, « À suivre » pendant les repos
- Bouton « Série terminée » pour finir une série plus tôt, pause, précédent/suivant, +15 s
- Continue écran verrouillé (service de premier plan) ; notification avec progression et bouton Arrêter

**Système audio**
- Bips synthétisés sur le **flux alarme** (audibles même si le volume média est bas)
- Sons distincts : début d'exercice, fin de série, fin de repos, changement d'exercice, décompte, fin de séance
- **Mode entraînement extérieur** : volume alarme au maximum, sons doublés et amplifiés, vibrations renforcées
- Annonces vocales en français (« Tractions. Série 2 sur 5. 10 répétitions, plus 15 kilos. C'est parti ! »)
- La musique est baissée automatiquement pendant les annonces

**Historique & progression**
- Séances réalisées (complètes ou partielles), détail par exercice
- Courbe de progression par exercice, record, suggestion « 5×6 ou 5×5 +12 kg »
- En fin de séance : saisie des répétitions réelles et application de la progression en un geste

**Modèles** : Force, Hypertrophie, Endurance, Muscle-up, Spécial tractions, Spécial dips.

## Compiler soi-même

Prérequis : Android Studio (ou JDK 17 + SDK Android 35).

```bash
./gradlew assembleRelease
# APK : app/build/outputs/apk/release/app-release.apk
```

La clé de signature `keystore/streetblocks.jks` est incluse pour que chaque nouvelle version s'installe par-dessus l'ancienne (usage personnel). Pour une publication sur le Play Store, remplace-la par ta propre clé.

## Architecture

```
app/src/main/java/com/streetblocks/app/
├── data/
│   ├── model/        Block, Catalog (exercices, barres, prises), Equipment, Templates, Progression
│   ├── db/           Room : séances, historique, élastiques
│   └── Repositories  Séances, historique, réglages, matériel
├── engine/           StepBuilder (blocs → étapes minutées), WorkoutEngine (exécution automatique)
├── audio/            AudioCues : bips synthétisés, voix, vibrations, volume
├── service/          WorkoutService (premier plan, wake lock, notifications plein écran)
└── ui/               Compose : accueil, créateur, éditeur de bloc, matériel, séance, historique, réglages
```
