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
        T.put("MinimalOS's home screen ran into a problem.\nGoing back to the TV's original home screen…",
                "L'écran d'accueil de MinimalOS a rencontré un problème.\nRetour à l'écran d'accueil d'origine de la TV…");
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
        T.put("MinimalOS is on: the Home button opens MinimalOS.",
                "MinimalOS est activé : le bouton Accueil ouvre MinimalOS.");
        T.put("Couldn't turn on MinimalOS: ",
                "Impossible d'activer MinimalOS : ");
        T.put("Original mode: Amazon's home screen is back.",
                "Mode d'origine : l'écran d'accueil d'Amazon est de retour.");
        T.put("Couldn't go back to the original: ",
                "Impossible de revenir à l'original : ");
        T.put("MinimalOS's home screen is off on the TV. From a computer: ",
                "L'accueil de MinimalOS est désactivé sur la TV. Depuis un ordinateur : ");
        T.put("Measuring the original home screen…",
                "Mesure de l'écran d'accueil d'origine…");
        T.put("Switching the home screen and screensaver…",
                "Changement de l'écran d'accueil et de l'économiseur d'écran…");
        T.put("Android didn't accept the new home screen",
                "Android n'a pas accepté le nouvel écran d'accueil");
        T.put("Turning off apps (",
                "Désactivation des applis (");
        T.put("Measuring MinimalOS…",
                "Mesure de MinimalOS…");
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
        T.put("MinimalOS's home screen crashed 3 times in a row; I switched back to Amazon's.",
                "L'accueil de MinimalOS a planté 3 fois de suite ; je suis revenu à celui d'Amazon.");
        T.put("MinimalOS's home screen crashed 3 times in a row; I switched back to the original one.",
                "L'accueil de MinimalOS a planté 3 fois de suite ; je suis revenu à celui d'origine.");
        T.put("the home screen",
                "l'écran d'accueil");
        T.put("the screensaver",
                "l'économiseur d'écran");
        T.put("Couldn't check MinimalOS: ",
                "Impossible de vérifier MinimalOS : ");
        T.put("The TV update turned back on ",
                "La mise à jour de la TV a réactivé ");
        T.put("Turned off what was missing: ",
                "J'ai désactivé ce qui manquait : ");
        T.put("The TV was updated; MinimalOS is still on.",
                "La TV a été mise à jour ; MinimalOS est toujours activé.");
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
        T.put("On Fire TV, the Home button opens MinimalOS.",
                "Sur Fire TV, le bouton Accueil ouvre MinimalOS.");
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
        T.put("Turn on MinimalOS", "Activer MinimalOS");
        T.put("On your computer, connect to this TV:", "Sur votre ordinateur, connectez-vous à cette TV :");
        T.put("Then run this, once:", "Puis lancez ceci, une seule fois :");
        T.put("This screen goes on by itself when it works.", "Cet écran continue tout seul quand ça marche.");
        T.put("Waiting for the service…", "En attente du service…");
    }

    static {
        T.put("Search", "Rechercher");
        T.put("See which of your apps has it", "Voyez quelle appli l'a");
        T.put("Results", "Résultats");
        T.put("New search…", "Nouvelle recherche…");
        T.put("Not on your apps", "Pas sur vos applis");
        T.put("None of your apps has this title.", "Aucune de vos applis n'a ce titre.");
        T.put("No results.", "Aucun résultat.");
        T.put("Couldn't reach the search.", "Recherche impossible.");
        T.put("No TMDB key (see the README).", "Pas de clé TMDB (voir le README).");
        T.put("Movie or show", "Film ou série");
    }

    static {
        T.put("Press OK to open", "Appuyez sur OK pour ouvrir");
    }

    static {
        T.put("GOOD MORNING", "BONJOUR");
        T.put("GOOD AFTERNOON", "BON APRÈS-MIDI");
        T.put("GOOD EVENING", "BONSOIR");
        T.put("Home Style", "Style d'accueil");
        T.put("ALL APPS", "TOUTES LES APPLIS");
        T.put("Updates", "Mises à jour");
        T.put("Update available", "Mise à jour disponible");
        T.put("Check for a new version and install it.", "Cherchez une nouvelle version et installez-la.");
        T.put("Installed version", "Version installée");
        T.put("Update source", "Source des mises à jour");
        T.put("Not set", "Non définie");
        T.put("Set update_repo=owner/repository in config.properties and build again.", "Définissez update_repo=propriétaire/dépôt dans config.properties puis recompilez.");
        T.put("Check for updates", "Rechercher des mises à jour");
        T.put("Asks GitHub if there is a newer version.", "Demande à GitHub s'il existe une version plus récente.");
        T.put("Install version ", "Installer la version ");
        T.put("Checking…", "Recherche…");
        T.put("New version ", "Nouvelle version ");
        T.put("You're up to date", "Vous êtes à jour");
        T.put("Couldn't reach GitHub", "GitHub injoignable");
        T.put("Downloading… 0%", "Téléchargement… 0 %");
        T.put("Downloading… ", "Téléchargement… ");
        T.put("Confirm on the TV", "Confirmez sur la TV");
        T.put("Failed: ", "Échec : ");
        T.put("This release has no .apk file.", "Cette version n'a pas de fichier .apk.");
        T.put("Home Layout", "Disposition de l'accueil");
        T.put("Region & Language", "Région et langue");
        T.put("The home style and how many apps per row.", "Le style d'accueil et le nombre d'applis par ligne.");
        T.put("Dynamic color, one fixed color, or pictures (including ones from your phone).", "Couleur dynamique, couleur fixe ou images (dont celles de votre téléphone).");
        T.put("City (weather), language and temperature.", "Ville (météo), langue et température.");
        T.put("Apps: the app grid. For You: movie and show picks.", "Applis : la grille d'applis. Pour vous : films et séries suggérés.");
        T.put("How many apps fit in each row (4 to 12).", "Combien d'applis par ligne (de 4 à 12).");
        T.put("Dynamic: the focused app's color. Color: one fixed color. Images: rotating pictures.", "Dynamique : la couleur de l'appli ciblée. Couleur : une couleur fixe. Images : des photos qui défilent.");
        T.put("The fixed background color.", "La couleur fixe de l'arrière-plan.");
        T.put("Your photos, movie and show art, or both.", "Vos photos, les affiches de films et séries, ou les deux.");
        T.put("Send photos from your phone over Wi-Fi, with a code.", "Envoyez des photos depuis votre téléphone en Wi-Fi, avec un code.");
        T.put("For the weather on the home screen.", "Pour la météo de l'écran d'accueil.");
        T.put("The language of the home screen and these menus.", "La langue de l'accueil et de ces menus.");
        T.put("Celsius or Fahrenheit.", "Celsius ou Fahrenheit.");
        T.put("OK: open", "OK : ouvrir");
        T.put("Image Source", "Source des images");
        T.put("My photos", "Mes photos");
        T.put("Show art", "Affiches");
        T.put("Both", "Les deux");
        T.put("Pictures from phone", "Photos depuis le téléphone");
        T.put("Add pictures from your phone", "Ajouter des photos depuis votre téléphone");
        T.put("1. On your phone (same Wi-Fi), open:", "1. Sur votre téléphone (même Wi-Fi), ouvrez :");
        T.put("2. Enter the code:", "2. Entrez le code :");
        T.put("3. Choose your photos. They show up on the TV right away.", "3. Choisissez vos photos. Elles apparaissent aussitôt sur la TV.");
        T.put("pictures on this TV", "photos sur cette TV");
        T.put("pictures on the TV", "photos sur la TV");
        T.put("Back: close", "Retour : fermer");
        T.put("Connect the TV to Wi-Fi.", "Connectez la TV au Wi-Fi.");
        T.put("Couldn't start.", "Impossible de démarrer.");
        T.put("Enter the code shown on the TV:", "Entrez le code affiché sur la TV :");
        T.put("Choose photos", "Choisir des photos");
        T.put("Delete all", "Tout supprimer");
        T.put("Wrong code.", "Code incorrect.");
        T.put("Sending", "Envoi");
        T.put("Couldn't send.", "Envoi impossible.");
        T.put("Open", "Ouvrir");
        T.put("Remove from favorites", "Retirer des favoris");
        T.put("Move earlier", "Déplacer avant");
        T.put("Move later", "Déplacer après");
        T.put("Add to favorites", "Ajouter aux favoris");
        T.put("Favorites are full", "Favoris complets");
        T.put("Hide app", "Masquer l'appli");
        T.put("App info", "Infos de l'appli");
        T.put("Uninstall", "Désinstaller");
        T.put("Good morning", "Bonjour");
        T.put("Good afternoon", "Bon après-midi");
        T.put("Good evening", "Bonsoir");
        T.put("All apps", "Toutes les applis");
        T.put("Recent", "Récentes");
        T.put("RECENT", "RÉCENTES");
        T.put("All Apps", "Toutes les applis");
        T.put("Background", "Arrière-plan");
        T.put("Background Color", "Couleur d'arrière-plan");
        T.put("Dynamic", "Dynamique");
        T.put("Color", "Couleur");
        T.put("Images", "Images");
        T.put("Blue", "Bleu");
        T.put("Purple", "Violet");
        T.put("Red", "Rouge");
        T.put("Green", "Vert");
        T.put("Teal", "Turquoise");
        T.put("Orange", "Orange");
        T.put("Pink", "Rose");
        T.put("Graphite", "Graphite");
        T.put("Shelf Size", "Applis par ligne");
        T.put("No inputs found.", "Aucune entrée trouvée.");
    }

    static String of(String english) {
        String s = T.get(english);
        return s != null ? s : english;
    }
}
