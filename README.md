# SpotAtlas — an AppFunctions demo for on-device Gemini

A small parking app that exists to show two things:

1. **Gemini can read your app's data mid-conversation.** Ask *"where can I park in Warsaw?"* and the
   on-device intelligence system calls SpotAtlas's `findParkingSpots` function and answers with real
   spots, prices and availability — without opening the app.
2. **Gemini can hand the user a deep link into your app.** Every result carries a
   `spotatlas://park/<spotId>` link that opens SpotAtlas on a pre-filled "start parking" screen.

The parking data is hardcoded (5 cities, 23 spots). The integration is not.

## What is exposed to the agent

Three functions, declared in
[`BaseSpotAtlasAppFunctionService`](app/src/main/java/com/example/spotatlas/ai/BaseSpotAtlasAppFunctionService.kt):

| Function | Purpose | Function id |
| --- | --- | --- |
| `findParkingSpots` | Search a city, nearest first. Returns price, free spaces and a deep link per spot. | `com.example.spotatlas.ai.BaseSpotAtlasAppFunctionService#findParkingSpots` |
| `startParkingSession` | Start a paid stay. Charges the driver, so the KDoc requires the agent to confirm first. | `…BaseSpotAtlasAppFunctionService#startParkingSession` |
| `endParkingSession` | Stop the running stay and report the final cost. | `…BaseSpotAtlasAppFunctionService#endParkingSession` |

App-level guidance for the agent — which function to call first, what it may not assume, the
money-spending warning — lives in [`res/xml/app_metadata.xml`](app/src/main/res/xml/app_metadata.xml).

The KDoc on those functions is not documentation for humans. It is the prompt the model reads when
it decides whether and how to call them, and KSP compiles it into
`assets/spotatlas_app_function_service.xml` inside the APK.

## Project layout

```
com.example.spotatlas
├── domain/       ParkingSpot, ParkingSession, City, GeoPoint, ParkingRepository, ParkingException
├── data/         ParkingCatalog (the hardcoded spots) + InMemoryParkingRepository
├── ai/           AppFunctions: the service entry point, the agent-facing DTOs, and the mappers
├── deeplink/     SpotAtlasDeepLink — builds and resolves spotatlas:// and https:// links
├── di/           AppContainer (manual DI)
└── ui/           Compose: map, start-parking confirmation, running session
```

Layering rule: the AppFunctions service and the Compose UI are **peers**. Both call the same
`ParkingRepository`, which is why a stay Gemini starts shows up on the map instantly, and why
`ai/` contains no business logic of its own.

`ai/model/` is deliberately a separate set of types from `domain/`. The agent-facing contract is
read by a language model, so its field names, units and docs are chosen for that reader and must stay
stable even when the domain model is refactored.

### Why manual DI instead of Hilt

[`AppContainer`](app/src/main/java/com/example/spotatlas/di/AppContainer.kt) is a hand-rolled container.
The app has one repository shared by the UI and the service, so this avoids adding a second
annotation processor next to the AppFunctions KSP compiler. Switching to Hilt is a contained change:
annotate `BaseSpotAtlasAppFunctionService` with `@AndroidEntryPoint`, replace the container with a
`@Module`, and swap the two `by lazy { appContainer… }` properties for `@Inject lateinit var`.

## Requirements

- **A device running Android 16 (API 36) or newer.** AppFunctions is a platform API from Android 16.
  The app installs on API 31+, but the function service is never bound below 36.
- JDK 17, Android SDK platform 37.
- Optional: a **Google Maps API key**. Without one everything works except the map tiles.

## Setup

Add your Maps key to `local.properties` (already git-ignored), or export `MAPS_API_KEY`:

```properties
sdk.dir=/path/to/Android/sdk
MAPS_API_KEY=AIza...
```

Then:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Verifying the integration without Gemini

This is the fastest feedback loop, and it proves the same path Gemini uses.

**1. Confirm the system indexed the functions**

```bash
adb shell cmd app_function list-app-functions --package com.example.spotatlas
```

**2. Use case 1 — find parking**

```bash
adb shell "cmd app_function execute-app-function \
  --package com.example.spotatlas \
  --function 'com.example.spotatlas.ai.BaseSpotAtlasAppFunctionService#findParkingSpots' \
  --parameters '{\"params\": {\"city\": \"Warszawa\", \"maxResults\": 3}}' --brief-yaml"
```

Returns the three nearest spots with `startParkingLink` deep links. Note that `"Warszawa"`,
`"Krakow"` and `"Kraków"` all resolve — the repository normalises diacritics and local aliases.

**3. Start and stop a stay**

```bash
adb shell "cmd app_function execute-app-function \
  --package com.example.spotatlas \
  --function 'com.example.spotatlas.ai.BaseSpotAtlasAppFunctionService#startParkingSession' \
  --parameters '{\"params\": {\"spotId\": \"waw-zlote-tarasy\", \"vehiclePlate\": \"WZ 1234A\", \"durationMinutes\": 90}}' --brief-yaml"

adb shell "cmd app_function execute-app-function \
  --package com.example.spotatlas \
  --function 'com.example.spotatlas.ai.BaseSpotAtlasAppFunctionService#endParkingSession' \
  --parameters '{\"params\": {}}' --brief-yaml"
```

An empty `params` object stops whatever stay is running, so the agent never has to look an id up.

**4. Check the error surface** — these are the messages the model gets back, so they are written as
recovery instructions:

```bash
# Unknown city -> lists the covered cities
--parameters '{"params": {"city": "Atlantis"}}'
# Second concurrent stay -> names the session id to stop
# Full car park -> suggests the next nearest spot
```

**5. Use case 2 — the deep link**

```bash
adb shell 'am start -a android.intent.action.VIEW -d "spotatlas://park/krk-galeria-krakowska?plate=KR%2099XYZ&duration=120"'
```

Opens the confirmation screen with the plate and duration already filled in. The driver still has to
press **Start parking** — an agent link never charges anyone on its own.

## Demoing with Gemini

With the app installed on an Android 16+ device, try:

- *"Where can I park in Warsaw?"* → `findParkingSpots`
- *"Find me somewhere in Kraków with EV charging"* → `findParkingSpots`, then the model filters on
  `hasEvCharging`
- *"Start parking at Złote Tarasy for two hours"* → `startParkingSession` after it confirms the spot,
  plate and price with you
- *"Stop my parking"* → `endParkingSession`

If you would rather drive it from a terminal agent than from Gemini itself, this prompt works well:

> Execute `adb shell cmd app_function` to learn how the tool works, then act as a chat agent aiming
> to invoke AppFunctions to fulfil user prompts for this app. Rely on the AppFunction description as
> instructions.

## Deep link reference

| Link | Opens |
| --- | --- |
| `spotatlas://city/<cityId>` | Map centred on a city |
| `spotatlas://spot/<spotId>` | Map with one spot selected |
| `spotatlas://park/<spotId>?plate=<plate>&duration=<minutes>` | Pre-filled start-parking confirmation |
| `spotatlas://session/<sessionId>` | The running stay |

The same paths work as `https://spotatlas.example.com/...`. `autoVerify` is off in the manifest because
the demo does not serve `/.well-known/assetlinks.json`; point the host at a domain you control and
turn it on to get verified App Links.

## Tests

```bash
./gradlew :app:testDebugUnitTest          # 23 JVM tests: city matching, search, session lifecycle, billing
./gradlew :app:connectedDebugAndroidTest  # 10 device tests: deep link round-trips
```

## Known limits

- Parking data is hardcoded in `ParkingCatalog` and lives in memory, so sessions reset when the
  process dies. Swapping in a real backend touches only `InMemoryParkingRepository`.
- `endParkingSession` can only stop the running stay; there is no read-only "am I parked?" function.
  Adding one is a single method on the service if the agent needs it.
- Billing is prorated per whole minute, so a stay shorter than a minute costs nothing.
