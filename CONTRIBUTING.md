# Principe de branches et contribution

Approche : trunk-based simplifié avec branches courtes. `main` est toujours stable et déployable.

| Branche | Rôle | Règles |
|---|---|---|
| `main` | Version stable, déployée | Aucun commit direct. Reçoit uniquement des pull requests dont la CI est verte. |
| `feat/US-xx-nom` | Une user story du backlog | Part de `main`, courte durée, supprimée après fusion. |
| `fix/BUG-xx-nom` | Correction d'un bug | Part de `main`. Contient un test de non-régression. |
| `docs/nom` | Documentation seule | Même circuit de pull request. |
| `refactor/nom` | Lisibilité sans changement de comportement | Les tests existants restent verts, sans modification. |
| `test/nom` | Ajout ou réécriture de tests | Aucun changement du code de production. |

## Cycle d'une user story

1. Créer la branche depuis `main` à jour : `git checkout main && git pull && git checkout -b feat/US-xx-nom`.
2. Écrire d'abord le test de la règle métier, constater l'échec, puis implémenter.
3. Commits courts, au format `feat:`, `fix:`, `docs:` ou `test:`.
4. Pousser la branche et ouvrir une pull request (le modèle de PR s'affiche automatiquement).
5. Fusionner seulement si la CI est verte (build React, tests JUnit, couverture JaCoCo, Cypress) et après relecture du diff.
6. Supprimer la branche. Déployer depuis `main`. Marquer la story DONE quand la DoD est satisfaite.
7. Poser un tag `vX.Y.Z` pour chaque version livrée.

## Protection de `main` (réglage GitHub)

Settings > Branches > règle sur `main` : exiger une pull request et exiger la réussite des contrôles de statut avant fusion.
