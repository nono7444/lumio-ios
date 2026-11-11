# Lumio iOS

Lumio iOS est un fork de **Nuvio Mobile** (https://github.com/NuvioMedia/NuvioMobile), sous licence
**GNU GPL v3** (voir LICENSE). Le code source modifié est publié ici, dans ce dépôt.

Base : NuvioMedia/NuvioMobile, commit `de60c5a` (8 novembre 2026, version 0.6.10).

## Modifications apportées par Lumio
- Compilation par GitHub Actions (IPA non signée, pour FlareStore / SideStore / Sideloadly).
- Nom « Lumio », icône Lumio, identifiant `lol.wawanono.lumio.ios` (ne remplace ni Nuvio ni l'appli Lumio).
- Démarrage direct sans compte Nuvio (profil local, `LumioMode`).
- Profil allégé : plus de compte Nuvio, de Watch party, d'intégrations de suivi (Trakt, Simkl, MDBList…) ni de réglages avancés ; le serveur Jellyfin est en haut du Profil.
- Streaming des titres absents du serveur : les pages « Debrid » (clé TorBox, Real-Debrid, Premiumize) et « Addons » de Nuvio sont de nouveau visibles dans les paramètres.
- Source FlareStore / SideStore publiée avec chaque version (altstore.json).
- Demandes au serveur Lumio depuis la fiche (« Demander », choix de la ou des saisons à demander pour une série, suivi de la demande et du téléchargement), avec le compte Jellyfin connecté (`LumioClient`, `LumioRequestBar`).
- Position et « vu » de Jellyfin toujours importés (reprise synchronisée avec l'appli Lumio et Lumio TV).
- Bibliothèque : ouvre directement les bibliothèques du serveur (Films, Séries, 4K…) et relit leur liste à chaque ouverture.
- Recherche : en plus du serveur, une rangée « Films et séries » TMDB (clé TMDB fournie par le serveur Lumio, gardée en mémoire) pour ouvrir un titre absent et le demander (`LumioTmdb`).
- Après une demande, la fiche suit le téléchargement et « Lire » trouve le titre dès son arrivée dans Jellyfin (cache des recherches serveur vidé).
- Bibliothèque en grandes cartes (image, icône, nom, nombre de titres) ; la grille d'une bibliothèque se trie (Nouveautés, Ajout récent, A-Z, Note) (`LumioLibraryCard`, `LumioLibrarySort`).
- Le nom du serveur n'apparaît plus dans les rangées (« Films », « Séries »…).
- « Regarder » lance directement la meilleure source (lecture auto par défaut, modifiable dans Lecture).
- Gestion des addons et des plugins retirée des réglages.
- Accueil et bibliothèques triés par date de sortie, la plus récente en premier (« Nouveautés »), comme sur Lumio TV.
- Accueil : barre des plateformes (Netflix, Disney+, Prime Video…) servie par le serveur Lumio ; chaque logo ouvre une page avec les dernières sorties films et séries (`LumioHome`, `LumioScreens`).
- Accueil : loupe de recherche et pastille indiquant si le serveur Lumio est en ligne, en haut à droite.
- Barre d'onglets : Accueil, Bibliothèque, Demandes, Calendrier, Profil ; la recherche passe dans la loupe de l'accueil (`AppScreenTab`, `ContentView.swift`).
- Onglet « Demandes » : demandes en cours / terminées, avec la progression du téléchargement (`LumioTabs`, `LumioScreens`).
- Onglet « Calendrier » : sorties numériques TMDB (films et nouvelles saisons) jour par jour, déjà sorties ou à venir (`LumioReleases`, `LumioScreens`).
- Icône : celle de Nuvio, avec un « L » à la place du bouton lecture.
- Accueil : une seule rangée « Continuer à regarder ». Celle du serveur est masquée et celle de l'appli suit Jellyfin (titres commencés sur la TV ou un autre appareil, mis à jour à l'ouverture de l'accueil puis toutes les minutes) (`LumioResume`, `ServerCatalog`, `MediaBrowserProvider`).
- Favoris partagés avec Lumio TV : en mode Lumio, les favoris de l'appli sont recopiés depuis et vers la liste « À voir » du serveur Lumio (`LumioFavorites`, `/api/watchlist`), à l'accueil puis toutes les minutes.
