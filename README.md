<div align="center">

<img src="docs/assets/icon-512.png" width="96" alt="">

# MinimalOS

**A fast, ad-free home screen for Google TV, Android TV, Fire TV, LG and Samsung TVs.**

Your apps, trending picks from them, the weather at a glance, your own photos as a background, and a look that follows the sunset.<br>
Open source. Built with Claude Code. One button to undo.

[Install](#install) · [Fire TV](#fire-tv) · [LG TVs](#lg-tvs-webos) · [Samsung TVs](#samsung-tvs-tizen) · [Any TV](#any-tv) · [Updates](#updates) · [Make it yours](#make-it-yours)

<img src=".github/preview.webp" width="880" alt="The MinimalOS home screen: a glass shelf of apps, trending picks, and a drawer with the weather and crypto prices">

</div>

## What you get

- **No ads.** No sponsored rows, banners or autoplaying promos. Not one.
- **Your apps, front and center.** The streaming apps you actually use, on a glass shelf. Pick how many it holds, reorder it, hide apps you never open, and find the rest in *All apps* (recent first or A to Z).
- **Shortcuts.** The TV's own settings screens, the Live TV button (pick the app it opens) and the app store, as glass buttons next to the shelf.
- **Trending, not sponsored.** Three fresh picks every hour from what's trending this week and the latest releases, only from apps you already have. Movies and Shows get their own tabs.
- **Weather and markets, one press away.** The hour-by-hour forecast, sunrise and sunset, and live crypto prices.
- **Your background.** Follow the focused app's color, pick one color, or show pictures: movie and show art, your own photos, or both. Go through them in order or at random, choose how long each one stays (15 s to 5 min) and how long the fade takes (1 to 8 s). Send photos from your phone over Wi-Fi with a 4-digit code, no cable.
- **A screensaver to match.** Full-screen photos with a slow zoom and a big glass clock. Show the movie and show art (TMDB), your own photos, or both mixed, in order or at random.
- **Lights.** Control a Bluetooth LED strip (ELK-BLEDOM controllers, like the "duoCo Strip" app) from the home screen: power, color, saturation and brightness.
- **Light by day, dark by night.** It follows your city's real sunset, the way your phone does.
- **Fast.** 60 fps, every frame in 16.6 ms, on the TV's own chip. The home screen uses 96 MB of memory instead of 242 MB (−60%).<br><sub>Measured on a 98″ TCL Google TV running Android 14.</sub>
- **Updates from the TV.** It checks for a new version and installs it for you.
- **Safe.** No root. Nothing is uninstalled. One button brings back the factory home screen.

Everything above is set on the TV itself, in the app's **Settings → Preferences**: city, language, temperature, home style, background, screensaver, lights, Live TV app and updates.

## Install

About ten minutes. You need a Google TV, Android TV or Fire TV (Android 11 or newer) and a computer on the same Wi-Fi. On a Fire TV, see [Fire TV](#fire-tv) for the extra step.

### The easy way: Claude Code

1. **On the TV**, turn on developer mode: Settings → System → About → press *Android TV OS build* 7 times. Then Settings → System → Developer options → turn on **USB debugging**. (On a Fire TV: Settings → My Fire TV → About → press the device name 7 times, then Developer options → **ADB debugging** and **Apps from unknown sources**.) Write down the TV's IP address (Settings → Network & Internet).
2. **On your computer**, get the code:
   ```bash
   git clone https://github.com/KingNepty/MinimalOS && cd MinimalOS
   ```
3. **Open [Claude Code](https://claude.com/claude-code) in that folder** and say:
   > Install MinimalOS on my TV at 192.168.1.50. I live in Lisbon.

   It installs the tools it needs, builds the app and puts it on your TV. Swap in your TV's IP and your city.
4. **On the TV**, open *MinimalOS* → *Home Screen* → *MinimalOS*, and accept the debugging prompt (tick *Always allow*).

### The manual way

1. **On the TV**, do step 1 above.
2. **Tools:** a JDK (17+), the Android SDK command-line tools (build-tools 34, platform android-34) and adb. On a Mac:
   ```bash
   brew install --cask temurin android-commandlinetools android-platform-tools
   sdkmanager "build-tools;34.0.0" "platforms;android-34"
   ```
   On Linux, get OpenJDK 17, Android's command-line tools and adb from your package manager, then run the same `sdkmanager` line. On Windows, use WSL.
3. **Build:**
   ```bash
   ./build.sh
   ```
   The first run creates `config.properties`. Set your city and your [TMDB key](#the-tmdb-key) there, then run it again.
4. **Install:**
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r FreeMyTV.apk
   ```
   On TCL TVs, also let it run in the background (TCL blocks it by default, and the switch never starts):
   ```bash
   adb shell appops set io.freemytv AUTO_START allow
   ```
5. **On the TV**, open *MinimalOS* → *Home Screen* → *MinimalOS*.

> The build output is still called `FreeMyTV.apk` and the package `io.freemytv`: renaming them would break updates on TVs that already have the app.

## Fire TV

Amazon's launcher can't be replaced or turned off, and its ADB hangs up on connections from the TV itself, so MinimalOS works differently there:

- **An accessibility service takes the Home button.** A short press opens MinimalOS at once, so Amazon's home never shows. Hold Home to open Amazon's home. The service sees which window is on top and the Home key; every other key passes through untouched, and it never reads the screen's content or text.
- **Our screensaver replaces Amazon's** while MinimalOS is on, and Amazon's comes back on *Original*.
- **Nothing of Amazon's is turned off** by the switch.

One-time step, from your computer (it survives reboots):

```bash
adb shell pm grant io.freemytv android.permission.WRITE_SECURE_SETTINGS
adb shell settings put secure enabled_accessibility_services io.freemytv/io.freemytv.HomeKeyService
```

If other services are already listed, add ours after them with a `:`. The permission lets the app turn the service back on by itself after an update or a reboot (Android switches it off when the app is replaced). Then open *MinimalOS* → *Home Screen* → *MinimalOS* on the TV; if the service isn't on yet, that screen shows these commands with your TV's IP and carries on by itself once it starts.

**Optional: free up memory.** `tools/fire-debloat.sh <TV IP> off` turns off Amazon's shopping, promo, tutorial and usage-collection apps (never uninstalls them). `on` brings every one back.

## LG TVs (webOS)

The same home screen as an LG app: the picks, the glass shelf with your apps, the drawer with inputs, weather and prices, day and night, Home Settings and the shelf editor. It installs through LG's own Developer Mode, with nothing rooted or changed on the TV. LG's home screen stays where it is, and MinimalOS is one press of a number key away.

You need an LG TV (webOS 3 or newer) and a computer on the same Wi-Fi, with Python 3.

1. **On the TV**, open the LG Content Store and install **Developer Mode**. Sign in with a free [LG developer account](https://webostv.developer.lge.com), turn on **Dev Mode Status** (the TV restarts), then turn on **Key Server**. Write down the IP address and the passphrase it shows.
2. **On your computer**, get the code and build. `config.properties` works as for Google TV (your city, your [TMDB key](#the-tmdb-key)):
   ```bash
   git clone https://github.com/KingNepty/MinimalOS && cd MinimalOS
   python3 webos/build.py
   ```
3. **Install.** The first time, it asks for the TV's passphrase; updates don't:
   ```bash
   python3 webos/install.py 192.168.1.50
   ```
4. **On the TV**, while MinimalOS is open, hold a number key on the remote. From then on, that key opens it from anywhere.

With Claude Code, say *"Install MinimalOS on my LG TV at 192.168.1.50. I live in Lisbon."* It builds the app and hands you the install command, so you type the passphrase yourself.

Developer Mode lasts about 40 days: open the Developer Mode app now and then and press **EXTEND**. To remove MinimalOS, run `python3 webos/install.py 192.168.1.50 --uninstall`.

## Samsung TVs (Tizen)

The same home screen as a Samsung app, with everything the LG one has: the picks, the shelf, the drawer with the inputs, the weather and the prices, day and night, and its own settings. It installs through Samsung's own Developer Mode, with nothing rooted or changed on the TV, and Samsung's home screen stays where it is.

You need a Samsung TV from 2017 or newer (Tizen 3+) and a computer on the same Wi-Fi.

1. **On the TV**, open **Apps** and type `1 2 3 4 5` on the remote. Turn **Developer mode** on, type your computer's IP address in **Host PC IP**, and restart the TV.
2. **On your computer**, download [TizenBrew Installer](https://github.com/reisxd/TizenBrewInstaller/releases/latest), the community's installer for Samsung TVs, open it and connect to your TV.
3. **Install from GitHub:** type `KingNepty/MinimalOS`. On 2023 and newer TVs it asks you to sign in to your Samsung account once, to sign the app for your TV.
4. **Straight to it:** in the TV's settings, under Start Screen Options, turn on **Autorun Last App**: the TV turns on into MinimalOS.

This build takes your city from the TV's time zone. For your exact city and the trending picks, build it with your own `config.properties` and [TMDB key](#the-tmdb-key): open the folder in Claude Code and say *"Install MinimalOS on my Samsung TV at 192.168.1.50. I live in Lisbon."* It sets up Samsung's tools, and on 2023 and newer TVs you sign in to your Samsung account in the browser to make the certificate.

## Settings

Defaults come from `config.properties`, created by the first build. Whatever you change on the TV (Settings → Preferences) wins over the file.

| Key | What it does | Default |
|---|---|---|
| `tmdb_key` | Your free TMDB key, for the picks and the movie art | empty |
| `tmdb_region` | Your country (two letters): which titles are on which app | `US` |
| `latitude`, `longitude` | Your city, for the weather and the sunset | New York |
| `city` | The name on the weather card | `Weather` |
| `language` | `auto` (follows the TV), `en`, `es`, `pt`, `ru`, `fr` or `de` | `auto` |
| `show_crypto` | Show Binance crypto prices on home and screensaver | `true` |
| `currency` | Crypto prices in `USD`, `EUR`, `BRL`… | `USD` |
| `temperature` | `C`, `F` or `auto` | `auto` |
| `disable_assistant` | Also turn off Google Assistant | `false` |
| `update_repo` | The GitHub repository the TV checks for new versions | empty |

Changed something? Run `./build.sh` and install again. Keep `freemytv.keystore`: updates are signed with it.

### The TMDB key

Free, and it takes two minutes: create an account at [themoviedb.org](https://www.themoviedb.org/signup), then open [Settings → API](https://www.themoviedb.org/settings/api) and request a developer key. Paste the *API Read Access Token* (or the *API Key*) after `tmdb_key=`. It's your key: `config.properties` stays out of git. Without it everything works except the picks and the movie and show art.

### Your own photos

Two ways, both used by the background and the screensaver:

- **From your phone:** *Settings → Preferences → Background → Pictures from phone*. The TV shows a code and an address; open it on your phone (same Wi-Fi), type the code and pick photos.
- **From a folder:** put them in `Pictures/FreeMyTV` on the TV. It needs a permission, which Settings asks for; or run `adb shell pm grant io.freemytv android.permission.READ_EXTERNAL_STORAGE`.

### Lights

*Settings → Preferences → Lights* lists the LED strips the TV hears, by name and signal. Neighbours' strips show too, so pick yours: it lights up so you can tell which one it is. Android 11 needs the location permission to scan; if the TV doesn't ask, run `adb shell pm grant io.freemytv android.permission.ACCESS_FINE_LOCATION`. MinimalOS never sends a command to a strip you didn't choose.

## Updates

*Settings → Preferences → Updates* checks the latest release of the repository in `update_repo` and installs it. A release counts when its tag is newer than the installed version (`v1.8`) and it has a signed `.apk` attached. To publish one: raise `versionName` and `versionCode` in the manifest, build, and attach the APK (named `MinimalOS-1.8.apk`) to a new release. It must be signed with the same keystore.

## Going back

On the TV: *MinimalOS* → *Home Screen* → *Original*. The factory home screen, screensaver and every app come back exactly as they were. If the new home screen ever crashes three times in five minutes, it switches back by itself.

To remove it for good, switch to *Original* first, then uninstall *MinimalOS* (Settings → Apps).

## What it changes

- It becomes the home screen and the screensaver.
- On Google TV and Android TV, it turns off, without uninstalling:
  - the stock home screen and screensaver, with the remote shortcuts they run on some TVs (like the Netflix button on TCL);
  - Google TV's recovery home, Play Games and YouTube Music;
  - on TCL TVs, the TCL Channel, messages, store demo, hotel menu, manuals, mirroring and logging apps;
  - Google Assistant, only with `disable_assistant=true`.
- On Fire TV, nothing is turned off: the Home button and the screensaver are redirected, and both go back on *Original*.
- Picture, sound, inputs, remote pairing, Google services and the Play Store are on a protected list, checked before every change.

## Any TV

MinimalOS runs on Google TV, Android TV and Fire TV: TCL, Sony, Hisense, Philips, Xiaomi, Amazon Fire TV and more, plus streamers like Chromecast with Google TV, the Google TV Streamer, onn 4K and NVIDIA Shield.

**Samsung, LG or any other TV:** plug a Google TV streamer into HDMI and set that one up. Same steps, same home screen.

**A different brand or model:** ask Claude. MinimalOS is made to be adapted by AI. Open the folder in Claude Code and say *"set it up for my Sony"*: it reads your TV, finds its home screen and its promo apps, updates the lists below and walks you through a test. [CLAUDE.md](CLAUDE.md) tells it how.

| What | Where |
|---|---|
| Apps to turn off | `DEBLOAT` in [`HomeMode.java`](app/src/io/freemytv/HomeMode.java) |
| Apps never to touch | `PROTECTED` in the same file |
| Apps on the shelf | `SHELF` in [`HomeApps.java`](app/src/io/freemytv/HomeApps.java) |
| Your brand's live TV app | `LIVE_TV_APPS` in the same file |

## Make it yours

It's your code, and Claude Code finds its way around it. Try:

- *"Add Crunchyroll to the shelf."*
- *"Show the prices in euros."*
- *"Add Spanish."*
- *"Switch to the night look an hour after sunset."*

## How it works

- **A plain Android app, no Gradle, no libraries.** [`build.sh`](build.sh) runs aapt2 → javac → d8 → zipalign → apksigner and makes a small APK.
- **On Google TV and Android TV, it uses the TV's own ADB, from inside the TV.** The app connects to `127.0.0.1:5555` with its own key (that's the *Allow USB debugging?* prompt) and runs the same commands you would: `cmd package set-home-activity`, `settings put secure screensaver_components`, `pm disable-user`. Nothing listens to the network.
- **On Fire TV, an accessibility service** ([`HomeKeyService.java`](app/src/io/freemytv/HomeKeyService.java)) takes the Home key, and the app sets the screensaver itself through the one-time `WRITE_SECURE_SETTINGS` permission.
- **Drawn by hand on one Canvas**, with Android 13 shaders for the frosted glass and the sky (plain fills on Android 12).
- **A safety net.** A watchdog in a separate process brings back the stock home screen after three crashes in five minutes, and a boot check keeps your choice after TV updates.
- **Data** from TMDB (trending titles, new releases and which app streams what in your country), Open-Meteo (forecast, sunrise and sunset) and Binance (24-hour prices).

| File | What it is |
|---|---|
| [`HomeView.java`](app/src/io/freemytv/HomeView.java) | The home screen |
| [`AppsHome.java`](app/src/io/freemytv/AppsHome.java) | The apps home: shelf, shortcuts, background |
| [`HomeMode.java`](app/src/io/freemytv/HomeMode.java) | Switching between MinimalOS and Original |
| [`HomeKeyService.java`](app/src/io/freemytv/HomeKeyService.java) | Fire TV: takes the Home button |
| [`Adb.java`](app/src/io/freemytv/Adb.java) | The TV's own ADB, from inside the TV |
| [`Recs.java`](app/src/io/freemytv/Recs.java) | The picks (TMDB) |
| [`HomeData.java`](app/src/io/freemytv/HomeData.java) | Weather and prices |
| [`HomeApps.java`](app/src/io/freemytv/HomeApps.java) | The shelf, inputs and All Apps |
| [`DreamView.java`](app/src/io/freemytv/DreamView.java) | The screensaver |
| [`Lights.java`](app/src/io/freemytv/Lights.java) | The Bluetooth LED strip |
| [`PhoneUpload.java`](app/src/io/freemytv/PhoneUpload.java) | Sending photos from your phone |
| [`Updater.java`](app/src/io/freemytv/Updater.java) | Updates from GitHub releases |
| [`SettingsActivity.java`](app/src/io/freemytv/SettingsActivity.java) | The settings screen (the app's icon) |
| [`Watchdog.java`](app/src/io/freemytv/Watchdog.java) | The safety net |

The `docs/` folder holds the project's website: plain HTML, CSS and JS on GitHub Pages.

## Privacy

No accounts, no analytics, no tracking. The app only talks to TMDB, Open-Meteo (with the coordinates you set), Binance and GitHub (to look for updates). Your photos stay on the TV. Your settings stay on your TV.

## Credits

- Built with [Claude Code](https://claude.com/claude-code).
- This product uses the TMDB API but is not endorsed or certified by [TMDB](https://www.themoviedb.org/).
- Weather data by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0). Prices from the [Binance](https://www.binance.com/) public API.
- Icons: [Material Symbols](https://fonts.google.com/icons) (Apache 2.0). Font: [Inter](https://rsms.me/inter/) (SIL OFL 1.1). Coin logos: [cryptocurrency-icons](https://github.com/spothq/cryptocurrency-icons) (CC0).
- Not affiliated with Google, Amazon, TCL or any streaming service. App names and logos belong to their owners.

## License

[MIT](LICENSE). Fork it, change it, ship it.
