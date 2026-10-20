#!/usr/bin/env bash
# Fire TV: turns off Amazon's shopping, promo, tutorial and usage-collection apps (never uninstalls them), so the TV has
# more memory and less background work for Free My TV. Run from a computer with adb:
#   tools/fire-debloat.sh <TV IP> off     turn them off
#   tools/fire-debloat.sh <TV IP> on      bring every one of them back
# Nothing here is needed for picture, sound, inputs, the remote, updates or Alexa. Not touched: the launcher, settings,
# system UI, Live TV, the keyboard, the Amazon store, the web view and the OTA updater.
set -u
ip="${1:?usage: fire-debloat.sh <TV IP> off|on}"; mode="${2:?usage: fire-debloat.sh <TV IP> off|on}"
adb connect "$ip:5555" >/dev/null
a() { adb -s "$ip:5555" shell "$@"; }

PKGS="
com.amazon.bueller.photos        # Amazon Photos
com.amazon.bueller.music         # Amazon Music
com.amazon.shoptv.client         # Shop TV
com.amazon.shoptv.firetv.client  # Shop TV
com.amazon.minitv.android.app    # miniTV
com.amazon.gamehub               # Game hub
com.amazon.sneakpeek             # promo previews
com.amazon.tv.earlyaccess        # early access program
com.amazon.tv.releasenotes       # release notes
com.amazon.storm.lightning.tutorial  # tutorial
com.amazon.tmm.tutorial          # tutorial
com.amazon.ftvads.deeplinking    # ads deep links
com.amazon.tv.website_launcher   # website launche
com.amazon.perfc                 # usage collection
com.amazon.perfcollection        # usage collection
"

echo "$PKGS" | sed 's/#.*//' | awk 'NF {print $1}' | while read -r p; do
  if !  a pm list packages "$p" </dev/null | grep -q "package:$p\$"; then echo "-  $p (not on this TV)"; continue; fi
  if [ "$mode" = off ]; then out=$(a pm disable-user --user 0 "$p" 2>&1 </dev/null); else out=$(a pm enable "$p" 2>&1 </dev/null); fi
  echo "$out" | tr -d '\r' | head -1 | sed "s|^|$p: |"
done
