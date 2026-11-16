# Location Test

Android app for the location check. It sends one pin to the Windows server, then reads that pin back. It does not track you in the background. It only uses a location when you tap **Use this phone**.

The server is [family-location-server](https://github.com/i2ichardt/family-location-server). Start that program on the PC first and leave its window open.

## Download and install

[Download LocationTest.apk](https://github.com/i2ichardt/location-test/raw/main/LocationTest.apk)

1. Open that link on the phone. Tap the download arrow and save the file.
2. Tap the downloaded `LocationTest.apk`.
3. If Android blocks it, allow installs from the browser you used, then tap the file again.
4. Install, then open **Location Test**.

The app is already filled in with a sample pin so you can test before GPS works:

| Field | Sample |
|---|---|
| User ID | Alice |
| Group ID | Fam1 |
| Latitude | 69.3601 |
| Longitude | 69.0589 |

Those numbers are a pin in Boston, not your house.

## How to use it

1. Start `FamilyLocationServer.exe` on the PC. The window must say **Server listening on port 8000**.
2. In **Server URL**, type the PC address:
   - Same Wi-Fi: `http://192.168.x.x:8000`
   - Tailscale, when you are not on that Wi-Fi: `http://100.x.x.x:8000`
3. Tap **Check server**. Status should show `200` and `OK`. If it cannot reach the server, fix the URL before sending.
4. Tap **Send location**. Status should say it stored the pin.
5. Tap **Fetch location**. Status should show Alice, Fam1, and the same latitude and longitude.

To send where the phone actually is:

1. Tap **Use this phone**.
2. Allow location when Android asks.
3. Wait until the latitude and longitude fields change.
4. Tap **Send location** again.

User ID is the name of the person. Group ID is the family name you all share. Everyone who should see the same pins uses the same group. Two phones only see each other if they use the same group and you fetch the other person's user id.

The server keeps one pin per user. Sending again replaces the old one. Closing the server window deletes all of them.

## Finding the PC address

**Same Wi-Fi.** On the PC, run `ipconfig`. Use the IPv4 address on the Wi-Fi adapter, usually `192.168.x.x`. Do not use the phone's address.

**Not on the same Wi-Fi.** Install Tailscale on the PC and the phone, same account, both Connected. On the PC, copy the address that starts with `100.` and put it in the URL: `http://100.x.x.x:8000`.

Always use `http://`. This test server does not speak HTTPS.

## If a button fails

- **Cannot reach server.** The PC program is closed, the URL is wrong, or a firewall is blocking port 8000. On the PC, run `allow-firewall.ps1` from the server repository as Administrator.
- **Check server works, send does not.** Fill user, group, latitude, and longitude. Latitude is -90 to 90. Longitude is -180 to 180.
- **Fetch says not found.** Send first, and use the same spelling for user and group. `Alice` is not `alice`.
- **Use this phone does nothing.** Allow location for this app. Step outside or turn location on if the phone has no GPS fix.

## Keep it private

There is no login. Anyone who can open the server URL can send and read pins. Use it on your home Wi-Fi or on your Tailscale network. Do not point it at a server that is open on the public internet.

## What is in this repository

`LocationTest.apk` is the installable app. `src/`, `res/`, and `AndroidManifest.xml` are the Android source for that same app. `make_icon.py` draws the icon. This is a small test build, not a Play Store project.
