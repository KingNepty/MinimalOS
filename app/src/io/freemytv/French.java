package io.freemytv;

import java.util.HashMap;
import java.util.Map;

/**
 * French on-screen text. Ui.tr("pt", "en") looks the English text up here when the language is French; anything
 * missing from this table shows in English. Strings that are put together from numbers (like "5 min ago") are
 * written out in the code that builds them.
 */
final class French {
    private French() {}

    private static final Map<String, String> T = new HashMap<>();

    static {
        T.put("prices from ",
                "prix de ");
        T.put("Free My TV's home screen ran into a problem.\nGoing back to the TV's original home screen…",
                "L'écran d'accueil de Free My TV a rencontré un problème.\nRetour à l'écran d'accueil d'origine de la TV…");
        T.put("Live TV",
                "TV en direct");
        T.put("All Apps",
                "Toutes les applis");
        T.put("Settings",
                "Réglages");
        T.put("Home Settings",
                "Réglages de l'accueil");
        T.put("TV Settings",
                "Réglages de la TV");
        T.put("Dollar",
                "Dollar");
        T.put("One-time step, from a computer: ",
                "Étape à faire une fois, depuis un ordinateur : ");
        T.put("Free My TV is on: the Home button opens Free My TV.",
                "Free My TV est activé : le bouton Accueil ouvre Free My TV.");
        T.put("Couldn't turn on Free My TV: ",
                "Impossible d'activer Free My TV : ");
        T.put("Original mode: Amazon's home screen is back.",
                "Mode d'origine : l'écran d'accueil d'Amazon est de retour.");
        T.put("Couldn't go back to the original: ",
                "Impossible de revenir à l'original : ");
        T.put("Free My TV's home screen is off on the TV. From a computer: ",
                "L'accueil de Free My TV est désactivé sur la TV. Depuis un ordinateur : ");
        T.put("Measuring the original home screen…",
                "Mesure de l'écran d'accueil d'origine…");
        T.put("Switching the home screen and screensaver…",
                "Changement de l'écran d'accueil et de l'économiseur d'écran…");
        T.put("Android didn't accept the new home screen",
                "Android n'a pas accepté le nouvel écran d'accueil");
        T.put("Turning off apps (",
                "Désactivation des applis (");
        T.put("Measuring Free My TV…",
                "Mesure de Free My TV…");
        T.put("Turning the apps back on…",
                "Réactivation des applis…");
        T.put("Turning apps back on (",
                "Réactivation des applis (");
        T.put("Restoring the original home screen…",
                "Restauration de l'écran d'accueil d'origine…");
        T.put("Original mode: the factory home screen, screensaver and apps are back.",
                "Mode d'origine : l'écran d'accueil, l'économiseur d'écran et les applis d'usine sont de retour.");
        T.put("the original home screen",
                "l'écran d'accueil d'origine");
        T.put("Original mode, but I couldn't turn back on: ",
                "Mode d'origine, mais je n'ai pas pu réactiver : ");
        T.put("Free My TV's home screen crashed 3 times in a row; I switched back to Amazon's.",
                "L'accueil de Free My TV a planté 3 fois de suite ; je suis revenu à celui d'Amazon.");
        T.put("Free My TV's home screen crashed 3 times in a row; I switched back to the original one.",
                "L'accueil de Free My TV a planté 3 fois de suite ; je suis revenu à celui d'origine.");
        T.put("the home screen",
                "l'écran d'accueil");
        T.put("the screensaver",
                "l'économiseur d'écran");
        T.put("Couldn't check Free My TV: ",
                "Impossible de vérifier Free My TV : ");
        T.put("The TV update turned back on ",
                "La mise à jour de la TV a réactivé ");
        T.put("Turned off what was missing: ",
                "J'ai désactivé ce qui manquait : ");
        T.put("The TV was updated; Free My TV is still on.",
                "La TV a été mise à jour ; Free My TV est toujours activé.");
        T.put("Connecting…",
                "Connexion…");
        T.put("Accept “Allow USB debugging?” on the TV (tick “Always allow”).",
                "Acceptez « Autoriser le débogage USB ? » sur la TV (cochez « Toujours autoriser »).");
        T.put("HDMI Inputs",
                "Entrées HDMI");
        T.put("Inputs",
                "Entrées");
        T.put("Shortcuts",
                "Raccourcis");
        T.put("Weather",
                "Météo");
        T.put("No weather data right now.",
                "Pas de données météo pour le moment.");
        T.put("Sunrise ",
                "Lever ");
        T.put("Sunset ",
                "Coucher ");
        T.put("Crypto",
                "Crypto");
        T.put("24h change",
                "Variation 24 h");
        T.put("Couldn't open ",
                "Impossible d'ouvrir ");
        T.put("For You",
                "Pour vous");
        T.put("Series",
                "Série");
        T.put("Your TV's home screen and screensaver",
                "Écran d'accueil et économiseur d'écran de votre TV");
        T.put("Original",
                "Original");
        T.put("Home Screen",
                "Écran d'accueil");
        T.put("Screensaver",
                "Économiseur d'écran");
        T.put("Preview",
                "Aperçu");
        T.put("Starting…",
                "Démarrage…");
        T.put("On the TV: tick “Always allow” and press OK.",
                "Sur la TV : cochez « Toujours autoriser » et appuyez sur OK.");
        T.put("RECOMMENDATIONS",
                "RECOMMANDATIONS");
        T.put("No TMDB key: see the README (config.properties).",
                "Pas de clé TMDB : voir le README (config.properties).");
        T.put("Error: ",
                "Erreur : ");
        T.put("Fetching titles…",
                "Récupération des titres…");
        T.put("This product uses the TMDB API but is not endorsed or certified by TMDB.",
                "Ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB.");
        T.put("COMPARISON",
                "COMPARAISON");
        T.put("Home button",
                "Bouton Accueil");
        T.put("Free memory",
                "Mémoire libre");
        T.put("Home screen memory",
                "Mémoire de l'accueil");
        T.put("NOTICES",
                "AVIS");
        T.put("None yet.",
                "Aucun pour le moment.");
        T.put("No ads, with the new screensaver",
                "Sans pubs, avec le nouvel économiseur d'écran");
        T.put("The TV's factory home screen",
                "L'écran d'accueil d'origine de la TV");
        T.put("On Fire TV, the Home button opens Free My TV.",
                "Sur Fire TV, le bouton Accueil ouvre Free My TV.");
        T.put("Switching takes a few seconds; the screen blinks while it measures.",
                "Le changement prend quelques secondes ; l'écran clignote pendant les mesures.");
        T.put("now",
                "à l'instant");
    }

    static {
        T.put("Shelf Apps", "Barre d'applis");
        T.put("Back to default", "Par défaut");
        T.put("OK: add or remove · ← →: reorder", "OK : ajouter ou retirer · ← → : ordre");
        T.put("Keep at least 1 app.", "Gardez au moins 1 appli.");
    }

    static {
        T.put("Apps", "Applis");
        T.put("Preferences", "Préférences");
        T.put("Hidden Apps", "Applis masquées");
        T.put("City", "Ville");
        T.put("Language", "Langue");
        T.put("Temperature", "Température");
        T.put("Automatic", "Automatique");
        T.put("Cancel", "Annuler");
        T.put("Search…", "Rechercher…");
        T.put("Show all", "Tout afficher");
        T.put("OK: hide or show", "OK : masquer ou afficher");
        T.put("Hidden apps stay installed.", "Les applis masquées restent installées.");
        T.put("OK or ← →: change", "OK ou ← → : changer");
        T.put("OK: search or choose", "OK : chercher ou choisir");
        T.put("Searching…", "Recherche…");
        T.put("No city found.", "Aucune ville trouvée.");
        T.put("Couldn't reach the city search.", "Recherche impossible.");
        T.put("Turn on Free My TV", "Activer Free My TV");
        T.put("On your computer, connect to this TV:", "Sur votre ordinateur, connectez-vous à cette TV :");
        T.put("Then run this, once:", "Puis lancez ceci, une seule fois :");
        T.put("This screen goes on by itself when it works.", "Cet écran continue tout seul quand ça marche.");
        T.put("Waiting for the service…", "En attente du service…");
    }

    static String of(String english) {
        String s = T.get(english);
        return s != null ? s : english;
    }
}
