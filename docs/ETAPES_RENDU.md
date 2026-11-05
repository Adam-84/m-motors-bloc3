# Étapes pour rendre le Bloc 3 (à faire de ton côté)

État vérifié le 5 nov. 2026 : app locale complète, 32 tests JUnit verts, couverture lignes 91,46 %, 2 tests Cypress verts, PDF de validation généré.
Ce qui manque est ce que l'énoncé déclare **bloquant** : liens Git publics, application déployée, login/mot de passe visibles, dépôt 1 puis dépôt final.

## A. Bloquant (sans ça le dossier n'est pas valide)

1. **Créer le dépôt public GitHub** `m-motors-bloc3` sous ton compte (Public, pas Private).
   ```bash
   cd m-motors
   git remote add origin https://github.com/Adam-84/m-motors-bloc3.git
   git push -u origin main
   git push -u origin feat/mvp
   ```
   Vérifier en navigation privée que le dépôt s'ouvre sans connexion.
2. **Montrer un vrai principe de branches** : l'historique n'a que 2 commits. Ouvre une PR `feat/mvp` -> `main` sur GitHub, fusionne-la, puis fais au moins 2 petites évolutions réelles (ex. `feat/US-legal-pages`, `fix/...`) chacune via PR. Ne fabrique pas d'historique antidaté.
3. **Déployer** (Render via `render.yaml`, voir `docs/DEPLOIEMENT.md`). Renseigner `ADMIN_PASSWORD` et `USER_PASSWORD`. Contrôler `https://<app>/actuator/health` = UP.
4. **Preuve de la 1re US DONE déployée** (énoncé C3.1 point 3) : capture du déploiement + hash du commit.
5. **Remplir le tableau de début de dossier** (format exigé : Description / Valeur) : lien git front, lien git back (monorepo : deux liens `.../tree/main/frontend` et `.../tree/main/backend`), lien application HTTPS, login, mot de passe. Remplacer les lignes « localhost » et « Non publié ».
6. **Régénérer le PDF** : `python scripts/build_dossier.py`, puis relire chaque page (supprimer toute phrase du type « non publié / à définir »).

## B. Conformité à l'énoncé (cocher dans le dossier)

| Question de l'énoncé | Où c'est traité | À vérifier |
|---|---|---|
| C3.1-1 repos Git + branches | §3 | liens réels + PR visibles |
| C3.1-2 démarche US en bullet points | §4 | vérifier que ce sont bien des puces |
| C3.1-3 US développées + 1re déployée | §5, §11 | preuve de déploiement (A.4) |
| C3.1-4 tests + % couverture | §7 | capture JaCoCo à insérer |
| C3.3-1 toutes les US déployées | §11 | tester chaque parcours sur l'URL publique |
| C3.3-2 mesures de sécurité justifiées | §8 | OK |
| C3.3-3 surveillance (logs, erreurs) | §10 | capture d'un log + `/actuator/health` |
| C3.2 outils / frameworks cohérents | §6 | OK |

Rappels de forme de l'énoncé : milliers séparés par une espace (`1 000`), toutes les réponses justifiées, fichier nommé `BachelorHETIC_Examen_B3Java_copiearendre_GHAJDAOUI_Adam`. Dépôt 1 (première correction) puis dépôt final : seul le final est noté.

## C. Audit anti-vibecode (scan du 5 nov.)

4/43 signes détectés par le scan, 2 sont de vrais manques :

- **38 Pas de politique de confidentialité** (réel) : l'app collecte des données personnelles (inscription, pièces d'identité fictives). Ajouter deux pages `mentions-legales` et `confidentialite` avec les champs à compléter marqués `TODO` (ne rien inventer). Cohérent avec le RGPD vu au Bloc 1/2.
- **41 Pas d'image Open Graph** (mineur) : ajouter `og:image`, `og:title` dans `frontend/index.html` (réutiliser `output/apercu-catalogue.png`).
- 29 « AI words » et 31 « badge New » : détectés dans le bundle compilé `static/assets/index-*.js`, probables faux positifs. Ils disparaissent si on ne scanne que `frontend/src`. À vérifier dans `main.jsx` : « Une nouvelle route » est un slogan, pas un badge.
- À contrôler à l'oeil : hero centré avec titre + lien (08), cartes de fonctionnalités (09), espacements entre sections (16), temps de chargement après réveil du plan gratuit Render (43).

## D. Soutenance

1. **Dépôt 1** : dépose le dossier dès que A est fait, pour avoir la première correction.
2. Lis les retours de l'évaluateur, corrige (branche `fix/...` + PR + test de non-régression : c'est le C3.3).
3. **Vidéo 5 à 10 min** : webcam + pièce d'identité visibles. Plan : tableau d'accès, démo parcours client (recherche, inscription, dossier, suivi), parcours admin (validation, bascule achat/location), branches/PR sur GitHub, couverture, sécurité, logs, réponse point par point aux retours. Utiliser les termes métier (user story, backlog, DoD, RPO/RTO, LOA).
4. **Dépôt final** : PDF final + liens + vidéo. Vérifier une dernière fois que l'URL répond et que les comptes fonctionnent le jour de la correction (Render gratuit : veille après 15 min, base expirée après 30 jours).
