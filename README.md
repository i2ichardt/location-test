# Family

Android app for the family map. It opens on the map. The Windows server keeps everyone's latest pin, name, phone number, and photo.

The server is [family-location-server](https://github.com/i2ichardt/family-location-server). Start that program on the PC first.

## Download and install

[Download LocationTest.apk](https://github.com/i2ichardt/location-test/raw/main/LocationTest.apk)

The installed app is named **Family**. Android will ask you to allow installs from the browser.

Allow location when the app asks. Sharing keeps a small notification so Android does not stop the updates.

## Settings

Open **Settings** from the map.

- **Home Wi-Fi address.** The PC's LAN address, from `ipconfig`. Example: `http://192.168.1.5:8000`
- **Tailscale address.** The PC's `100.x` address, from `tailscale ip -4`, or its MagicDNS name. Example: `http://100.64.1.2:8000`
- **Auto, Home, or Tailscale.** Auto tries the home address. If that PC is not reachable, it uses Tailscale. That matches being on the same Wi-Fi versus being on mobile data.
- **Group name.** Every phone that should see each other uses the same name.
- **Your profile.** Name, phone number, and a photo. The photo is the pin on the map. Save sends it to the PC.
- **Send my location every.** How often your pin is updated. 30 seconds is the start.
- **Check the group every.** How often this phone reloads everyone else's pins.
- **Update my pin only when I stop moving.** While you are walking or driving, the pin stays where you last stopped. When you stop, it sends the new spot.

## Settings groups

Settings is split into Connection, Sharing, Profile, and Shared places.

- **Fast updates.** Every 30 seconds while moving, every 5 minutes while still.
- **Slow updates.** Every 1–2 minutes while moving, every 15 minutes while still.
- With the map open, pins refresh every few seconds. In the background they slow down. Low battery, weak GPS, or deep sleep can delay a pin by up to 30 minutes.
- Saving a place sends it to the PC immediately. Other open phones refresh their place list right away.

## The map

**Satellite** switches the map to aerial photos. Tap it again for the street map.


The map is the whole screen. Your photo, and everyone else's, sits on the pin.

**People** is a bar at the bottom. Tap it to open the group. Tap a name, or **View**, for a Street View of where they are. **Call** opens the phone dialer. **Trip** opens Google Maps directions to them. Tap the bar again to hide the list.

Tap a pin on the map for the same Street View.

The map tiles are OpenStreetMap. Street View and directions open Google Maps, so no Maps key is stored in the app.

## Shared places

In Settings, **Save where I am as a place** adds that spot for the whole group. Home, school, or work.

Each phone chooses its own alerts:

- Tell me when someone arrives
- Tell me when someone leaves

Those alerts are only for this phone. Another phone in the group will not buzz unless that phone turned the same alert on.

## Keep it private

There is no login. Use it on your home Wi-Fi or on your Tailscale network. Do not point it at a server that is open on the public internet.

## What is in this repository

`LocationTest.apk` is the installable app. `src/`, `res/`, `assets/`, and `AndroidManifest.xml` are the source for that same app.
