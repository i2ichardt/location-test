# Family

The phone opens on a map. Pins, names, and photos stay on your PC. At home the app uses the PC’s LAN address. Away from that Wi-Fi it uses the Tailscale address. Both are saved in Settings.

The Windows server is [family-location-server](https://github.com/i2ichardt/family-location-server). Start that program on the PC first.

## Download

The compiled app is [Family 2.12](https://github.com/i2ichardt/location-test/releases/tag/v2.12).

[Download LocationTest.apk](https://github.com/i2ichardt/location-test/releases/download/v2.12/LocationTest.apk)

The installed app is named **Family**. Allow installs from the browser if Android asks. Allow location when it asks.

## Set it up

1. **Join one tailnet.** Install Tailscale on the Windows 11 PC and on each phone. Sign in with the same account and pick the Personal plan. It is free for non-commercial use, up to 6 users, with as many devices as you need.

2. **Start the server.** Unzip the Windows download and run FamilyLocationServer.exe. It listens on port 8000 for both the home network and Tailscale. The saved Tailscale address is under File, then Settings. Minimize hides the window and leaves a tray icon. Closing the window stops it. If Windows blocks the unsigned file, choose More info, then Run anyway, and allow it on Private networks.

3. **Install the app.** Download the APK on the phone and open it. Allow installs from the browser if Android asks. Allow location when it asks.

4. **Save both addresses.** Open Settings. Put the PC’s home address in the Wi-Fi field, from `ipconfig`, like `http://192.168.1.5:8000`. Put the Tailscale address in the other field, from `tailscale ip -4`, like `http://100.x.x.x:8000`. Leave the choice on Auto. Set the group name, your name, phone, and a photo, then Save.

## Which address to type

| Phone | Server URL |
| --- | --- |
| On the same Wi-Fi | `http://PC-LAN-address:8000` |
| Mobile data, or any network | `http://100.x.x.x:8000` |

Tailscale encrypts the tunnel. This server does not. It has no password and no HTTPS. Anyone on the tailnet who knows the address can read the pins. Do not forward port 8000 to the public internet.

## On the phone

The map is the home screen. Pull the People handle to slide the list up, and leave it at any height. Tap the handle or the title to open or close it all the way. The list scrolls. Swipe a person’s card sideways to close it.

Each person is a white circle with a small arrow on the exact spot. The panel shows battery, signal, a green or red location icon, Wi-Fi when they are on Wi-Fi, and a connection icon. If the server cannot reach them, the panel says when the last response came in and a likely reason. It also shows how often their phone is set to update.

Street View, Call, and Text use the Google Maps app and the phone dialer. Call starts the call. Satellite or street map is remembered the next time you open the app.

Saved places are shared. Arrive and leave alerts are chosen on each phone. A fix that jumps while the phone is still on that place’s Wi-Fi is dropped. The sharing notification says that swiping it away stops sharing your location.

Sharing can be Fast, Slow, or Custom. Fast is about every 30 seconds while moving and every 5 minutes while still. Slow is about every 1–2 minutes while moving and every 15 minutes while still. While charging, and while traveling over 30 mph, updates can be faster. Those choices sit next to the option they describe.

The server keeps profiles, photos, pins, and places in a data folder next to the program.
