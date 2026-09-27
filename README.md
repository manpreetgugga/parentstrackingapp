# Parents Tracker

An Android app for keeping an eye on the people you care about, such as Mom and Dad, **with their permission**.
Built with Kotlin and Jetpack Compose (Material 3).

## Features

| Tab | What it does |
| --- | --- |
| **Home** (dashboard) | Your current location (street address, accuracy, battery), each family member's live location, how far away they are, whether they're at a saved place ("At Home · 2 min ago"), battery level, last check-in, and one-tap **Call / Route / Map** buttons. |
| **Map** | Live Google Map of everyone, with colour-coded markers, saved-place circles and a person picker. **Ready for your API key**: until one is added it shows setup steps and "Open in Google Maps" links. |
| **People** | Add people by invite code, see who can see you, stop sharing with anyone, cancel or re-send invites, and enter a code someone sent you. |
| **Settings** | Name, pause sharing, update frequency (1/5/15 min), permissions, saved places, emergency contacts, alert toggles, setup status, and a demo family for previewing. |

**Consent and transparency** (the "banner about accessing"):

- Adding someone creates a 6-character invite code (valid 48 h) you send over WhatsApp or SMS.
- When they enter the code, they get a **consent screen** first. It shows who is asking, exactly what will be shared (location, address, battery, last update, SOS and check-ins), and how to stop. Nothing is shared until they tap **Allow**.
- While anyone can see them, the tracked person always sees:
  - a **banner on their Home screen**: *"Your location is visible to Manpreet (Son)"*, with **Pause** and **Manage** buttons;
  - a **persistent notification**: *"Your location is being shared"*, with a **Pause sharing** button.
- They can pause, or stop sharing with a specific person, at any time.

**Other features:**

- **SOS button**: alerts everyone who can see you, opens an SMS with your location to your emergency contacts, and offers a one-tap call to 112 (configurable).
- **Quick check-ins**: "I'm OK 👍", "Reached home 🏠", "On my way 🚶", "Please call me 📞", shown on your family's dashboards.
- **Saved places and alerts**: notifications when someone arrives at or leaves Home (or any saved place), when their battery is low, or when they send an SOS.
- **Offline / stale detection**: warns when someone's phone hasn't updated in 2+ hours.
- **Keeps working in the background**: a foreground location service, restarted after reboot if "Allow all the time" is granted.
- **Demo family**: preview the full dashboard before connecting real phones.
- Prominent location-use disclosure during onboarding (required by Google Play), plus light and dark themes.

## What works right now vs. what needs keys

| Works with no setup | Needs your keys |
| --- | --- |
| Your own location, address, battery, sharing links | **Live map tab** → Google Maps API key |
| Saved places, SOS (SMS + call), check-ins | **Connecting family phones** (invites, live family locations, alerts) → Firebase `google-services.json` |
| Demo family on the dashboard and map list | |
| "Open in Google Maps" / directions (no key needed) | |

The app detects both automatically at build time; you don't need to change any code.

## 1. Add the Google Maps API key (Map tab)

1. Open [Google Cloud Console](https://console.cloud.google.com/), create or select a project, then go to **APIs & Services → Library** and enable **Maps SDK for Android**.
2. Go to **APIs & Services → Credentials → Create credentials → API key**.
3. (Recommended) Restrict the key to **Android apps**, with package name `com.parentstracker.app` and your signing certificate's SHA-1. For the debug SHA-1, run `./gradlew signingReport`.
4. Add the key to `local.properties` in the project root. This file is git-ignored, so the key never gets committed:

   ```properties
   MAPS_API_KEY=AIza...your key...
   ```

5. Rebuild and install. The Map tab switches to the live map.

## 2. Turn on family sync (Firebase)

1. Create a project in the [Firebase console](https://console.firebase.google.com/).
2. Add an **Android app** with package name `com.parentstracker.app`, then download `google-services.json` into the **`app/`** folder. It is git-ignored.
3. **Authentication → Sign-in method** → enable **Anonymous**. Nobody needs an account or password.
4. **Firestore Database → Create database** (production mode).
5. **Firestore → Rules**: paste the contents of [`firestore.rules`](firestore.rules), then **Publish**.
6. Rebuild and install on each family phone.

Settings → *Setup status* shows whether sync is connected, or what's wrong (for example, if the rules weren't published).

## Build and run

Requirements: Android Studio (JDK 17+) and Android SDK 36.

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. You can also open the folder in Android Studio and press **Run**.

Run the unit tests:

```bash
./gradlew testDebugUnitTest
```

## How to use it with your parents

1. Install the app on your phone and on your parents' phones.
2. On **your** phone: People → **Add person** → name "Mom" → **See their location** → **Send invite**.
3. On **Mom's** phone: People → **Enter code** → review the request → **Allow** → allow location.
   For reliable sharing, go to Settings → **Allow all the time**.
4. Mom now appears on your dashboard and map. She sees the "visible to …" banner and notification, and can pause anytime.
5. Optional: on your phone, stand at Mom's house → Settings → **Save my current location as a place** → "Mom's home", to get arrival and departure alerts.

## Project structure

```
app/src/main/java/com/parentstracker/app/
├── ParentsTrackerApp.kt, AppContainer.kt     app start-up and dependency wiring
├── data/        Models, SettingsStore (DataStore), FamilyRepository,
│                CloudBackend + FirebaseBackend / LocalOnlyBackend, DemoData
├── location/    LocationTracker (fused location, geocoder, battery),
│                LocationSharingService (foreground service), BootReceiver
├── notify/      Notifications (channels, sharing notification), AlertMonitor (SOS/arrival/battery)
├── ui/          MainActivity, Root (navigation), AppViewModel, screens/, components/, theme/
└── util/        Geo, InviteCodes, Intents (dial/SMS/maps/share), Perms
firestore.rules  Firestore security rules
```

## Notes and next steps

- Family alerts (SOS, arrivals, low battery) are raised on the viewer's phone while the app is running or sharing in the background. For guaranteed push alerts even when the app is closed, add Firebase Cloud Messaging with a Cloud Function.
- Anonymous sign-in means reinstalling the app creates a new identity, so family members would need to re-invite. Phone-number sign-in can be added later.
- Before publishing on Google Play, background location needs a Play Console declaration and a short demo video. The in-app disclosure is already in place.
