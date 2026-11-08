# Lumio iOS

Lumio iOS est un fork de **Nuvio Mobile** (https://github.com/NuvioMedia/NuvioMobile), sous licence
**GNU GPL v3** (voir LICENSE). Le code source modifié est publié ici, dans ce dépôt.

Base : NuvioMedia/NuvioMobile, commit `de60c5a` (8 novembre 2026, version 0.6.10).

## Modifications apportées par Lumio
- Compilation par GitHub Actions (IPA non signée, pour FlareStore / SideStore / Sideloadly).
- Nom « Lumio », icône Lumio, identifiant `lol.wawanono.lumio.ios` (ne remplace ni Nuvio ni l'appli Lumio).
- Démarrage direct sans compte Nuvio (profil local, `LumioMode`).
- Profil allégé : plus de compte Nuvio, de Watch party, d'intégrations (Trakt, Simkl, MDBList, Debrid…) ni de réglages avancés ; le serveur Jellyfin est en haut du Profil.
- Source FlareStore / SideStore publiée avec chaque version (altstore.json).
- Demandes au serveur Lumio depuis la fiche (« Demander », saisons manquantes, suivi de la demande et du téléchargement), avec le compte Jellyfin connecté (`LumioClient`, `LumioRequestBar`).
- Position et « vu » de Jellyfin toujours importés (reprise synchronisée avec l'appli Lumio et Lumio TV).
