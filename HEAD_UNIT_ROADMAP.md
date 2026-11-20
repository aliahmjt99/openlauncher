# Open Launcher head unit audit and roadmap

Audited the fork at commit `8ee7d3a`. Status reflects the source and supplied CPU-Z photos.

## Target device

| Item | Photo evidence | Compatibility requirement |
| --- | --- | --- |
| Board and CPU | ALPS tb8163p3_bsp, MediaTek MT8163, four Cortex-A53 cores at up to 1.5 GHz | Keep startup work small and defer optional services |
| GPU and display | Mali-T720, 1280×720 landscape, 160 dpi | Test stock system insets, touch targets, keyboard, and all sidebar positions |
| Memory | 1,986 MB total, roughly 680–700 MB available in the photos | Bound icon and artwork caches, avoid animated wallpaper, and test app switching under memory pressure |
| Firmware | CPU-Z reports Android 15/API 35, with a PPR1 build identifier, a 2021 security patch, and an armv7l kernel | Check runtime SDK, ABI list, package installation, and vendor behavior on the unit |
| Integration | No root reported. Stock radio and system navigation are visible | Use a standard APK. Verify each vendor integration independently |

The project declares minSdk 21 and targetSdk 36. Treat the effective minimum Android version as pending manifest merge and runtime tests. A 64-bit CPU can ship with a 32-bit Android build. Inspect the built APK for native libraries and preserve armeabi-v7a support whenever any dependency needs native code.

## Existing features and audit findings

| Area | Present in the fork | Finding |
| --- | --- | --- |
| Home | Landscape HOME activity, setup flow, default-launcher settings, draggable/resizable 3×2 widget grid | Fullscreen was forced. Failed grid placement could leave cards overlapping. Home selection and wake behavior depend on firmware |
| Apps | Search, installed/system/all filters, package picker, reorderable left/right/bottom shortcuts, custom icon overrides | Eager icon loading for all packages is a memory risk. Some vendor packages expose restricted activities |
| Media | Track metadata, artwork and transport controls through notification access | Function depends on the active app publishing a usable MediaSession |
| Radio | szchoiceway commands when detected and assigned media-session radio fallback | CPU-Z board data does not establish szchoiceway compatibility. Verify the stock radio app and its session first |
| Projection | Assignable CarPlay and Android Auto app shortcuts | Requires a working receiver app or dongle. The launcher opens the receiver |
| Widgets | Clock, weather, telemetry and compass, speedometer, altimeter, trip meter, vitals, sound pads and radio through Now Playing | Weather needs network. GPS and sensors need working hardware and permission. Review timing and interactive widgets for parked use |
| Appearance | Day/night modes, offline sunrise/sunset calculation, colors, gradients, wallpaper dimming, typography and scaling | Check legibility with the actual system bar and at minimum and maximum supported scale |
| Floating windows | PiP settings fields and app-launch wiring | The checked-out standard app contains no verified universal floating-window implementation |

## Delivery order

| Level | Features and fixes | Acceptance check |
| --- | --- | --- |
| Foundation | Persistent system-bar option, safe launch feedback for restricted or removed apps, reject grid edits that cannot fit | Toggle survives restart. Restricted targets show feedback. Impossible layouts preserve prior positions |
| Essentials | Large favorites for Navigation, Music, Radio, Phone/Bluetooth and Projection. Phone-safe settings links, missing-app reassignment, Home and Back behavior, readable 720p preset, and Arabic/English labels with RTL tests | Core actions usable offline. Stock app discovery and returning Home work with the real firmware |
| Everyday usability | Preset dashboard layouts, layout lock, reset layout, versioned backup/restore, searchable settings, and on-device diagnostics for SDK, ABI, memory, permission status and media support | Import validates layout and tolerates apps absent from the target unit. Diagnostics work offline |
| Performance and recovery | Lazy bounded icon loading, sized album art, reduced animation mode, lifecycle-aware polling, Android shrinker review, and memory measurements | Stable cold start, warm return, ignition sleep/wake and repeated app switching. Report measured memory and startup time |
| Media and navigation | Preferred media source and app fallback, capability-aware playback buttons, offline navigation app shortcuts, OEM Bluetooth/phone shortcuts | Playback controls only appear for supported actions. Receiver or stock app remains usable if metadata is unavailable |
| Advanced and conditional | Per-device radio presets/seek adapter after confirming vendor API, optional headlight-based theme, steering-button integration where exposed, Android AppWidgets, optional OBD-II read-only dashboard and OEM-supported split screen | Each integration is opt-in and tested against the unit and relevant accessory. Reverse camera and OEM audio behavior remain under stock firmware control |

## Before marking this unit compatible

1. On the parked head unit, read `ro.build.version.sdk`, `ro.product.cpu.abilist`, and the build fingerprint via ADB if available. Record the stock launcher, radio, Bluetooth and projection packages.
2. Build and inspect a standard APK. Install it while the stock launcher is still selected, test Open Launcher as an ordinary app, then select it as Home and verify you can return to the stock launcher.
3. Test 1280×720 with the top bar shown and hidden, keyboard open, cold boot, sleep/wake, rotation handling, memory pressure, Wi-Fi disconnected, GPS unavailable, and notification access granted and denied.
4. With the vehicle parked, verify stock tuner, Bluetooth audio/calls, projection, Home/Back/volume keys, and reverse camera handoff. Log missing or vendor-restricted capabilities and use stock-app shortcuts as the fallback.

Build verification is pending. This environment had no Android SDK and could not download the Gradle wrapper distribution.

## Accessory stages

| Stage | Scope | Test gate |
| --- | --- | --- |
| 1 — Startup and Android CI | Default car icon, Open Launcher icon, custom logo from storage, or Off. Short in-app splash on cold launch. GitHub Actions runs tests and lint, builds a debug APK, and publishes a test prerelease for pushed `v*` tags. | Verify startup with each logo, revoked document access and process restart. Download the action artifact and install it on the head unit. Debug signing keys can change between CI runners. |
| 2 — Accessory discovery and Wi-Fi OBD-II | Settings → Accessories reports SDK, app ABIs, and connected USB vendor/product IDs. The Wi-Fi ELM327 test reads RPM and speed on demand. | JVM tests check ELM replies and a loopback TCP session. Verify adapter Wi-Fi routing on this device, missing ECU data, and disconnection. |
| 3 — Live OBD-II widget | Move ELM327 queries behind interchangeable transports. Add paired Bluetooth Classic SPP, supported USB serial chips, and optionally documented BLE services. Add live RPM/speed/coolant readings with stale values and connect/disconnect controls. | Test each transport against a real adapter, including denied permissions, ECU sleep, unplugging and network changes. |
| 4 — USB TPMS widget | Identify the ANR-compatible dongle by VID/PID, interface descriptors, model and captured frames. Add only verified protocol decoders, then expose wheel mapping, pressure, temperature and last-seen status. Treat HID and USB serial as distinct receiver families. | Use dongle-specific fixtures for valid, fragmented, corrupt and duplicate frames. Verify permission, USB detach, coexistence with ANR, and readings against the vendor app and gauge. |
| 5 — Camera and vendor apps | Inventory the stock front-camera, Bluetooth, radio and projection apps. Use user-assigned shortcuts first. If a documented camera source is accessible, render it in a launcher widget and offer PiP for a launcher-owned camera activity where available. For vendor apps, use their own PiP, an Android AppWidget they provide, or a documented OEM embedding API. | Test foreground and background transitions, disconnect/reconnect, and stock reverse-camera priority on the parked unit. Verify any OEM API against the exact firmware and keep launch shortcuts available. |

Provide the TPMS scan line, the product listing or adapter model, and the stock front-camera app package to identify the two vendor-specific paths. The launch surface for ordinary apps is already present through the app picker and sidebar.

### Accessory implementation update

Settings → Accessories now includes ELM327 snapshot tests over Wi-Fi TCP, paired Bluetooth Classic SPP, and USB serial using the default usb-serial-for-android driver registry. USB OBD baud rate is selectable. All three transports share the same read-only mode 01 PID requests for RPM and speed. A Bluetooth adapter exposed only through the firmware's dedicated Bluetooth app may be missing from Android's paired-device list. BLE adapters need a documented GATT profile.

A separate USB test uses the documented 55 AA serial receiver heartbeat and validates eight-byte tire frames with XOR checksums at 19200 baud. It displays wheel position, decoded pressure and temperature, and receiver flags. The pressure conversion follows the SamSkjord reference profile. Compare readings against the vendor app and a gauge before enabling this profile for a new receiver. HID report formats and Deelife/MU7J variants need their own profiles and fixtures. USB chip detection alone cannot identify a tire receiver's application protocol. CI tests include valid, fragmented and corrupt serial frame fixtures.

References: [55 AA serial receiver](https://github.com/SamSkjord/TPMS), [USB serial drivers](https://github.com/mik3y/usb-serial-for-android), [other TPMS protocol families](https://github.com/drrhaos/tpms).

## Non-AI parity backlog

This roadmap treats the requested features as requirements for OpenLauncher. CI checks the merged APK minimum SDK and armeabi-v7a native library coverage. Android 9/API 28 runtime behavior still needs a physical head-unit test.

| Area | Available or in this change | Remaining |
| --- | --- | --- |
| Display and layout | Left/right/bottom dock, landscape layout, widget editor, day/night themes, independent launcher-window brightness, keep-screen-on, 12/24-hour clock, independent weather temperature units, searchable app drawer | Multiple dashboard pages, Home button page cycling, driver profiles, translations, draggable return overlay, OEM-supported split screen, expanded widget catalog |
| Vehicle costs and maintenance | Local fuel and EV charging costs with monthly totals, editable service history, in-app service countdowns and CSV export | scheduled reminders, distance-based intervals |
| Trips and telemetry | GPS trip meter, manual local trip archive, trip CSV export and 0–100 timer, live Wi-Fi ELM327 RPM/speed/coolant, RPM/speed snapshots over USB/Bluetooth Classic, 55 AA serial TPMS sample | Live OBD/BLE dashboard, expanded optional PIDs, DTC read and user-confirmed parked clear, threshold alerts, gauge canvas, automatic trip recording, validated fuel economy and eco/emissions estimates, live TPMS and additional receiver profiles |
| Navigation | One-tap fuel, food, hotel, ATM and charging searches in an installed maps app | Embedded navigation, offline regional map downloads/search/routing, voice guidance, rerouting, 3D rendering, Google Navigation and live traffic, TomTom/Places data, speed limits and hazard data |
| Media and utilities | MediaSession widget, existing FM integrations, phone keypad, Android contact picker, local phone favorites and recent dialer handoffs, Android Bluetooth status, OEM phone app handoff | Spotify library/search, track-reactive background, internet radio and favorites/history, lightweight browser, GPS weather refresh |
| Camera and companion | Launch stock camera and projection apps from assigned shortcuts where exposed | Front-camera widget/PiP, verified UVC capture, recording and retention, experimental ADAS, supported ambient BLE profiles and theme sync, companion contacts/call history/battery/sharing on supported devices |

Prioritize offline launcher and accessory functionality on Android 9. Map and media providers need their own compatibility and credential review, especially where native libraries are involved. Measure recording, mapping and experimental ADAS under the head unit's 2 GB RAM budget before enabling them by default.

### Live Wi-Fi OBD-II verification

Settings → Accessories → Start live readings polls Mode 01 RPM, speed and coolant temperature through one ELM327 TCP connection. Stop the session from the same panel. The connection closes when the panel leaves the foreground, and resumes while the panel is still selected when the app returns to the foreground. Unsupported values appear as unavailable. The loopback test checks two batches over one TCP session, including a missing coolant reading and socket close. Verify the adapter's address, Wi-Fi routing and ECU sleep behavior on the parked vehicle. Home-screen gauges and live USB/Bluetooth transport support remain open.
### Phone and head-unit Bluetooth verification

From the dashboard's Phone shortcut, open Bluetooth to pair through Android Settings, inspect Android-visible paired devices, and select the firmware's phone app. Android headset/audio profile indicators cover the Android Bluetooth stack. A separate MCU may only expose its state and controls inside the OEM app. Dial requests use the selected app when it accepts the standard dial intent, then fall back to Android's dialer. The Recent tab tracks dialer handoffs, including cancelled dialer visits, with a clear action. It does not import completed or incoming calls.

Use the OEM phone app to test inbound calls, answer/end, audio routing, phonebook sync and call history. Verify that its Bluetooth music session exposes usable controls to Android. Direct HFP-client calls, PBAP logs and phone battery readings require a documented firmware interface or a separate phone companion. Validate both Android-managed and MCU-managed pairing on the physical head unit.

### Live 55 AA TPMS

Select the USB serial receiver in Settings → Accessories and open Live 55 AA TPMS. The four main wheels show waiting, fresh, lost-signal or stale states. Spare readings appear if the receiver supplies them. Readings become stale after five minutes without a valid frame and the last displayed pressure is removed. Polling stops when the panel is backgrounded or closed. Physical testing must confirm the receiver profile, sensor cadence and pressure calibration.
