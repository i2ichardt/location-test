# Family

Android app for the family map. It opens on the map. The Windows server keeps everyone's latest pin, name, phone number, and photo.

The server is [family-location-server](https://github.com/i2ichardt/family-location-server). Start that program on the PC first.

## Download and install

The compiled app is on the [v2.7 release](https://github.com/i2ichardt/location-test/releases/tag/v2.7).

[Download LocationTest.apk](https://github.com/i2ichardt/location-test/releases/download/v2.7/LocationTest.apk)

The installed app is named **Family** (version 2.7). Android will ask you to allow installs from the browser.

Allow location when the app asks. Sharing keeps a small notification so Android does not stop the updates.

## Settings

Settings opens to a category menu, not one long page:

- **Connection.** Home address, Tailscale address, and which one to use.
- **Sharing.** How often your pin is sent, including a custom time.
- **Group.** The name everyone in the family uses. This is separate from your profile.
- **Profile.** Your name, phone number, and photo.
- **Shared places.** Places the group can see, and alerts for this phone.

**Back** returns to that menu without saving. **Close** on the menu leaves Settings without saving. Android's back button does the same: a category goes back to the menu, and the menu closes Settings.

**Save** is at the bottom of Connection, Sharing, Group, and Profile. It commits only that page, then returns to the menu. Shared places has no Save button; a place is sent as soon as you save it with its own button.

The phone field formats as you type. You only enter digits. A US number becomes `111-111-1111`.

Sharing is **Fast**, **Slow**, or **Custom**. Custom is a number plus **Seconds** or **Minutes**. Fast is every 30 seconds while moving and every 5 minutes while still. Slow is every 1–2 minutes while moving and every 15 minutes while still. With the map open, Fast and Slow refresh every few seconds. Custom uses the time you set for both sending your pin and fetching the group, including while the map is open. Low battery, weak GPS, or deep sleep can delay a pin by up to 30 minutes.

You can also turn sharing off, or update the pin only when you stop moving.

## The map

The map is the whole screen. Pinch to zoom. There are no +/- buttons.

Your photo, and everyone else's, sits on the pin. **Satellite** switches to aerial photos. Tap it again for the street map. The app opens in the same view next time.

**People** is the handle at the bottom. Tap it to open or close the list all the way. Drag the handle, or drag a person in the list, and the panel follows. The list still scrolls when it is open and does not fit. Each person shows how far away they are and their battery. **Center** moves the map to them. **Directions** opens Google Maps with a route ready to start. **Call** and **Text** use the phone. Tap a profile photo for a larger picture. Tap or swipe that picture to close it.

Tapping a person pin opens a card. Swipe anywhere on the card to close it, or tap that person again. **Directions** starts a Google Maps route. Times read as hours and minutes, not a long count of minutes.

People at the same spot share one map bubble. Their photos sit next to each other and do not overlap. A new profile photo replaces the old one on the map.

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
