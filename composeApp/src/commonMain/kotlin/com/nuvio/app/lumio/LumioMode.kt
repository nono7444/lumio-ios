package com.nuvio.app.lumio

/**
 * Lumio iOS : Nuvio réduit à l'essentiel pour la famille (comme Lumio TV).
 * - démarrage direct, sans compte Nuvio (profil local) ;
 * - Profil allégé : apparence, lecture, serveurs (Jellyfin), notifications, à propos ;
 * - pas de Watch party, de Trakt / Simkl / MDBList ni de P2P ;
 * - streaming des titres absents du serveur : Paramètres → Debrid (clé TorBox…) et Addons (sources).
 */
object LumioMode {
    const val ENABLED = true
}
