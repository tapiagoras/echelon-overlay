# Physical test 01: read-only BLE diagnostic for the EX-5s

This is the first read-only, non-actuation BLE capture. The goal is to confirm discoverability, GATT inventory, and real-time notification traffic without sending any resistance-control, calibration, unlock, stop, or startup command to the bike.

## 1. Install the diagnostic app

Install the Android app only after the Android SDK is available:

1. Install Android Studio or the Android command-line SDK.
2. Configure an Android SDK with API 35 / build tools matching the project.
3. Set the SDK path in `local.properties` so Gradle can build.
4. Install the debug build onto the test phone:
   - `./gradlew.bat :app:installDebug`
   - or use Android Studio's Run/Debug with the project open.
5. Confirm the app icon is visible on the phone and the package is the project app ID.

## 2. Permissions to grant

On Android 12+ grant the following when prompted:

- Bluetooth scan
- Bluetooth connect
- Location (if the OS requires it to discover nearby BLE devices)

On older Android versions, grant the location permission used by BLE discovery.

The app must not request any actuation or control write. It should only scan and subscribe to notifications.

## 3. Echelon app state

For the initial analysis, keep the official Echelon app closed.

Reasoning:

- The stock app may already own the bike BLE connection.
- A competing app can prevent successful passive capture or produce misleading results.
- The first proof we need is raw passive telemetry from the device itself.

If the stock app is already running:

1. Disconnect it from the bike through the app UI.
2. Close the app fully.
3. Re-run the diagnostic app.

Do not assume the app can connect while the stock app is still connected.

## 4. Identify the correct BLE device

On the diagnostic screen:

1. Tap START SCAN.
2. Identify the device whose name appears as an Echelon-like name or begins with `ECH`.
3. Compare the MAC address to the bike as physically present in the room.
4. Prefer the device that is actively advertised near the bike and not a nearby phone, tablet, or unrelated BLE peripheral.
5. If several Echelon-like devices appear, record all of them and note which one was selected.

Record:

- BLE name
- MAC address (when Android exposes it)
- RSSI value
- whether the device is labeled as likely Echelon by the app

## 5. Connection and GATT checks

1. Select the likely Echelon device.
2. Tap CONNECT.
3. Wait for status to move to Connected.
4. Observe the GATT service list.
5. Record service UUIDs discovered.
6. Record the notification characteristic UUID used for subscription.
7. Confirm the app shows a subscription success/failure log.

The expected flow is passive only:

- connect
- discover services
- subscribe to notify characteristic(s)
- capture incoming notifications
- disconnect cleanly

Do not send any vendor initialization, polling, unlock, or resistance command.

## 6. What to record while the bike is idle

Keep the bike stationary and not pedaling.

Record for at least 30 to 60 seconds:

- time and date
- discovered device name and address
- exact GATT service UUID(s)
- notification characteristic UUID
- raw bytes received, if any
- packet count
- whether the app logs status changes or errors

If no packets appear while idle, note it as a valid observation. Do not jump to conclusions.

## 7. What to record while pedaling slowly

1. Pedal slowly for 30 to 60 seconds with a moderate, comfortable cadence.
2. Keep the bike at a low or neutral resistance setting.
3. Do not manually change resistance during this segment.
4. Record:
   - time stamps
   - raw packet bytes for each notification
   - packet count
   - observed cadence if visible on the bike console
   - any manual movement or changes in the bike controls

Use the diagnostic screen to capture the raw bytes and the log file.

## 8. What to record while pedaling faster

1. Increase cadence to a comfortable faster pace.
2. Maintain the same general resistance setting unless the bike is intentionally changed.
3. Record 30 to 60 seconds of traffic.
4. Capture:
   - packet timestamps
   - raw bytes
   - packet count
   - any obvious changes in cadence or status

The target is to see whether packet cadence, lengths, or payload pattern changes as actual pedaling speed changes.

## 9. What to record while manually changing resistance using the bike controls

This is a purely observational step.

1. Keep pedaling at a steady cadence.
2. Use the bike's manual resistance controls once.
3. Note the exact resistance knob change or console control action.
4. Record the following during and immediately after the change:
   - exact time of the change
   - raw packets before and after the change
   - whether the packet stream changes quickly, gradually, or not at all
   - any visible console response
   - whether the app logs any extra connection or service events

Repeat once or twice if the device is stable, but do not do repeated forced changes or any actuation writes from the diagnostic app.

## 10. Required screenshots and logs

Bring back all of the following:

1. A screenshot of the app's main diagnostic screen showing:
   - title
   - status
   - device name and RSSI
   - GATT service UUID
   - notify characteristic UUID
   - packet count
2. A screenshot of the discovered device list, including the selected device.
3. A screenshot of the packet log, including timestamped notification payloads.
4. A screenshot of the connection lifecycle log, especially connect/disconnect and any error messages.
5. The local app log file generated by the app, if available:
   - path: `/data/data/dev.echelonoverlay.app/files/echelon_ble_diagnostic.log`
   - or use a file export method supported by the Android device and note the path.
6. A short note listing the exact manual actions performed during the session.

## 11. Clean disconnect

After the capture window:

1. Tap DISCONNECT.
2. Confirm the app returns to an idle state.
3. Verify the bike still responds to its own manual controls.
4. Stop the test if the bike displays unexpected behavior or any actuation occurs without the rider doing it.

Do not retry automatically or send any fallback command.

## 12. Pass/fail criteria for this first test

This test passes for a diagnostic capture if:

- the EX-5s is discoverable
- the app connects successfully
- GATT service discovery completes
- the notify characteristic is discovered and subscribed
- at least one notification packet is captured with a timestamp and raw bytes
- the logs show clean connection lifecycle events
- no actuation command was sent by the diagnostic app

This test is inconclusive if the bike is not discoverable or no notifications arrive after subscription. Inconclusive is valid. Do not escalate into guessed initialization or control writes without a separate, clearly confirmed protocol step.
