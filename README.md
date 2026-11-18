# Family

Android app for the family map. It opens on the map. The Windows server keeps everyone's latest pin, name, phone number, and photo.

The server is [family-location-server](https://github.com/i2ichardt/family-location-server). Start that program on the PC first.

## Download and install

The compiled app is on the [v2.11 release](https://github.com/i2ichardt/location-test/releases/tag/v2.11).

[Download LocationTest.apk](https://github.com/i2ichardt/location-test/releases/download/v2.11/LocationTest.apk)

The installed app is named **Family** (version 2.11). Android will ask you to allow installs from the browser.

Allow location when the app asks. Sharing keeps a small notification so Android does not stop the updates.

## Settings

Settings opens under the same blue header as Shared Places. Each section is a white card.

Shared Places lists every saved spot with its picture, its arrive and leave distance, and two checks: notify when someone arrives, and notify when someone leaves. **Add Place** saves where you are. Tap the meters to change that distance. The trash button removes the place. **Notifications** turns place alerts on or off for this phone.

**Updates** checks the GitHub release page. If a newer app is there, it downloads it and starts the install.

**Back** is the round arrow at the top left of every settings page, including the first one. On the first page it closes Settings. On the others it returns to the menu. The header picture changes with the season and the holiday. **Save** commits only that page.

The phone field formats as you type. You only enter digits. A US number becomes `111-111-1111`.

Sharing is **Fast**, **Slow**, or **Custom**. Each option has its own description on that page. Fast is every 30 seconds while moving and every 5 minutes while still. Slow is every 1–2 minutes while moving and every 15 minutes while still. Custom is a number plus **Seconds** or **Minutes**. With the map open, Fast and Slow refresh every few seconds. Custom uses the time you set for both sending your pin and fetching the group, including while the map is open.

**Faster updates while charging** uses the number of seconds on that row while the phone is plugged in. **Real-time updates above 30 MPH** sends a pin about every 5 seconds while you are going faster than 30 miles per hour. You can also turn sharing off, or update the pin only when you stop moving. Low battery, weak GPS, or deep sleep can still delay a pin.

## The map

The map is the whole screen. Pinch to zoom. There are no +/- buttons.

Your photo, and everyone else's, sits on the pin. **Satellite** switches to aerial photos. Tap it again for the street map. The app opens in the same view next time.

**People** is the handle at the bottom. Tap the handle, or the "People in your group" title, and the list opens or closes all the way. Drag the handle or drag a person in the list and the panel follows your finger. The list still scrolls when it is open and does not fit. Battery is a phone-style icon with the percent at the top right of that person's panel. Next to it is a signal icon with the same kind of bars as a phone. If a check-in is missed, that icon becomes a no-signal mark, and the same mark sits on their map pin until the next check-in. If their location is turned off, the panel says so. **Center** moves the map to them. **Directions** opens Google Maps with a route ready to start. **Call** places the call. **Text** uses the phone. Tap a profile photo for a larger picture. Tap or swipe that picture to close it.

The line at the top left, **Home · 3 people** (or Tailscale), is a button. Tap it and the map frames everyone in the group who has a pin.

Tap a person while the map is zoomed out and it zooms back in. If that person is traveling, the map stays centered on them until you close their card. The arrived time does not reset to "just now" while they are still moving. "Here with you" is only for someone else standing next to you.

Tapping a person pin opens a card. Swipe anywhere on the card to close it, or tap that person again. **Directions** starts a Google Maps route. Times read as hours and minutes, not a long count of minutes.

People at the same spot share one map bubble. Their photos sit next to each other and do not overlap. A new profile photo replaces the old one on the map.

## Shared places

In Settings, the pencil above the trash can renames a place and can use a photo from the gallery or the camera. **Save where I am as a place** adds that spot for the whole group. Home, school, or work.

Each phone chooses its own alerts:

- Tell me when someone arrives
- Tell me when someone leaves

Those alerts are only for this phone. Another phone in the group will not buzz unless that phone turned the same alert on.

## Keep it private

There is no login. Use it on your home Wi-Fi or on your Tailscale network. Do not point it at a server that is open on the public internet.

## What is in this repository

`LocationTest.apk` is the installable app. `src/`, `res/`, `assets/`, and `AndroidManifest.xml` are the source for that same app.
