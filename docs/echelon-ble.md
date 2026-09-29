# Echelon EX-5 / EX-5s: QZ BLE reference research

Research date: 2026-09-28. Upstream: `cagnulein/qdomyos-zwift` (QZ).
All source links below are pinned to revision
[`0bd17860cce73ee945f8717761c899e180e921f3`](https://github.com/cagnulein/qdomyos-zwift/commit/0bd17860cce73ee945f8717761c899e180e921f3),
resolved from upstream HEAD during this research.

This is an original description of observed source behavior and protocol facts,
not a port, implementation, or device-tested specification. QZ's root
[LICENSE](https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/LICENSE)
contains GPL version 3. No QZ implementation or power lookup table is copied into
this project. Reference downloads were kept outside this repository. No Android
application files were changed and no bike commands were sent.

**UNCERTAIN** means the inspected source does not establish the fact for an actual
EX-5 / EX-5s, or supports only an inference. Confirmed QZ behavior is not automatically
confirmed hardware behavior, especially on the EX-5s 22-inch console.

## Main result and relevant files

QZ routes eligible Bluetooth names beginning with uppercase `ECH` to the shared
`echelonconnectsport` class, derived from `bike`. The discovery branch excludes
already-selected Echelon rower, treadmill, stairclimber, FTMS bike, and Connect
Sport instances and applies the discovery filter. It does not select a separate
EX-5 or EX-5s class in this branch. Thus an EX-5/EX-5s advertising such a name is
expected to follow this shared path; the actual console advertisement and firmware
compatibility are **UNCERTAIN**. Sources: `src/devices/bluetooth.cpp`,
`bluetooth::deviceDiscovered()` [routing][discovery];
`src/devices/echelonconnectsport/echelonconnectsport.h`, class
`echelonconnectsport` [declaration][header].

| File | Relevant class/functions | Why inspect it |
| --- | --- | --- |
| `src/devices/echelonconnectsport/echelonconnectsport.cpp` | `echelonconnectsport::{deviceDiscovered, serviceScanDone, stateChanged, descriptorWritten, btinit, update, characteristicChanged, forceResistance, sendPoll, watts, wattsFromResistance, controllerStateChanged}` | Real-bike connection, decoding, commands, estimates, recovery |
| `src/devices/echelonconnectsport/echelonconnectsport.h` | `echelonconnectsport`, `maxResistance()`, state fields | Maximum level 32, initial poll counter 1, initialization/reconnect state |
| `src/devices/bluetooth.cpp` | `bluetooth::deviceDiscovered()` | Shared-driver selection |
| `src/virtualdevices/virtualbike.cpp` | `virtualbike::{virtualbike, characteristicChanged, echelonWriteStatus, echelonWriteResistance, relayEchelonPacket}` | Emulated Echelon peripheral and relay; secondary evidence only |
| `src/qzsettings.cpp` | `QZSettings::default_echelon_watttable` | Default power-estimation table selection |
| `tst/Devices/devicetestdataindex.cpp` | `DeviceTestDataIndex::Initialize()` | Name-routing test expects `ECH` prefix to select `echelonconnectsport`; not a hardware/protocol test |

Source links: [real driver][driver], [header][header], [discovery][discovery],
[virtual peripheral][virtual], [settings][settings], [discovery test][test].

## BLE service and characteristic UUIDs

| Role | UUID | Exact source |
| --- | --- | --- |
| Real-bike communication service | `0bf669f1-45f2-11e7-9598-0800200c9a66` | Driver `echelonconnectsport::serviceScanDone()` [source][services] |
| Command write characteristic | `0bf669f2-45f2-11e7-9598-0800200c9a66` | Driver `echelonconnectsport::stateChanged()` [source][subscribe] |
| Notify 1 | `0bf669f3-45f2-11e7-9598-0800200c9a66` | Same function [source][subscribe] |
| Notify 2 | `0bf669f4-45f2-11e7-9598-0800200c9a66` | Same function [source][subscribe] |
| Additional emulated Echelon service | `0bf669f0-45f2-11e7-9598-0800200c9a66` | `src/virtualdevices/virtualbike.cpp`, `virtualbike::virtualbike()` [source][virtual-services] |

QZ writes `01 00` to the Client Characteristic Configuration descriptor on both
notify characteristics. The source refers to this descriptor by Qt's
`QBluetoothUuid::ClientCharacteristicConfiguration` enum (standard CCCD `0x2902`).
The real-bike driver uses the `...f1` service; it does not require the `...f0`
service in `serviceScanDone()`. **UNCERTAIN:** presence/role of `...f0` on the actual
EX-5s. Do not substitute the virtual peripheral's service inventory for a device
GATT capture. Sources: driver `stateChanged()` [subscription][subscribe];
`virtualbike::virtualbike()` [emulated services][virtual-services].

Both notification channels feed the same real-driver parser. QZ's virtual
peripheral emits D1 status and D2 resistance on `...f4` and several handshake
replies on `...f3`; the real parser does not enforce that split. **UNCERTAIN:**
exact notification-channel assignment for each real EX-5s firmware.
Sources: driver `stateChanged()`, `characteristicChanged()` [parser][telemetry];
`virtualbike::characteristicChanged()`, `echelonWriteStatus()`,
`echelonWriteResistance()` [virtual replies][virtual-replies], [virtual telemetry][virtual-telemetry].

## Connection and initialization sequence

This describes the Qt BLE central path applicable to Android. The file also has
an optional native iOS path; its behavior is not an Android requirement.

1. `bluetooth::deviceDiscovered()` stops scanning and creates the shared driver
   for an eligible `ECH` device [routing][discovery]. The driver's
   `echelonconnectsport::deviceDiscovered()` also checks that prefix, creates a
   `QLowEnergyController` central, installs callbacks, and connects. On connection
   it discovers services [connection][connect].
2. `echelonconnectsport::serviceScanDone()` creates the `...f1` service object and
   discovers details. If unavailable, it requests a bike-restart toast when UI is
   available and disconnects [service discovery][services].
3. `echelonconnectsport::stateChanged()` resolves/asserts the three characteristics,
   attaches callbacks, and enables both CCCDs [subscription][subscribe].
4. `echelonconnectsport::descriptorWritten()` sets `initRequest` and emits
   `connectedAndDiscovered`. It does not explicitly count both CCCD completions.
   The 200 ms refresh timer calls `update()`, which consumes `initRequest` and
   invokes `btinit()` [descriptor callback][descriptor], [timer/update][update].
5. `echelonconnectsport::btinit()` transmits the following sequence, then sets
   `initDone`. The source comment attributes the four repetitions to a sniffed
   exchange and expresses uncertainty about how many are necessary [init][init].

| Order | Bytes (hex) | Evidence/meaning |
| --- | --- | --- |
| 1–4 | `F0 A1 00 91` each time | Repeated initialization request; exact device semantics **UNCERTAIN** |
| 5 | `F0 A3 00 93` | Initialization request; exact semantics **UNCERTAIN** |
| 6 | `F0 A1 00 91` | Same request again |
| 7 | `F0 B0 01 01 A2` | Final initialization write; exact real-bike mode semantics **UNCERTAIN** |

Every initialization write uses `writeCharacteristic(..., wait_for_response=true)`.
That helper waits for **any** service `characteristicChanged` event or a 300 ms
timeout; it does not correlate response opcode, payload, or success. Otherwise
it waits for `characteristicWritten` or the same timeout. It invokes Qt's write
method without an explicit write-mode argument. The driver has no negotiated
write-mode selection here; actual EX-5s properties should be captured. `btinit()`
does not require a validated acknowledgment before setting `initDone`.
Source: driver `echelonconnectsport::writeCharacteristic()` [write helper][write],
`btinit()` [init][init].

After initialization, `update()` periodically calls `sendPoll()`, sending
`F0 A0 01 N C`, where `N` starts at 1, increments through 255, and wraps to 1;
`C` is the sum of preceding bytes modulo 256. The comment says every two seconds;
the post-increment comparison against `2000 / 200` actually schedules every 11
eligible update calls (nominally 2.2 seconds, before blocking/timer effects).
**UNCERTAIN:** required hardware keepalive deadline and poll-response meaning.
Sources: driver `sendPoll()`, `update()` [poll/update][update]; header
`counterPoll`, `sec1Update` [state][header].

## Telemetry packet format

Offsets below are zero-based. The real parser first recognizes a five-byte D2
resistance notification. Other notifications must be exactly 13 bytes to enter
status decoding. Its D1 header check is commented out, and it does not validate
incoming length-field or checksum bytes. This is a description of QZ's parser,
not a validation policy to reuse. Source: driver
`echelonconnectsport::characteristicChanged()` [parser][telemetry].

### Status: expected 13-byte D1 frame

| Offset | Interpretation | Evidence and confidence |
| --- | --- | --- |
| 0 | Expected `F0` | Virtual writer emits it; real parser does not enforce it |
| 1 | Expected `D1` | Same limitation |
| 2 | Expected `09` | Virtual writer emits it; interpretation as payload length is an inference, **UNCERTAIN** on real hardware |
| 3–4 | Elapsed seconds, intended big-endian 16-bit value | Real driver's `GetElapsedFromPacket()` converts to minutes/seconds |
| 5–8 | Distance-related region | Virtual writer emits a four-byte value; real helper reads only offsets 7–8, divides by 100 |
| 9 | Unknown | Virtual writer emits zero; meaning **UNCERTAIN** |
| 10 | Cadence, unsigned one-byte rpm | Real driver's `characteristicChanged()` |
| 11 | Possible heart rate | Virtual writer emits heart rate here; real-bike driver does not decode it; **UNCERTAIN** for EX-5s |
| 12 | Candidate additive checksum | Virtual writer sums bytes 0–11 modulo 256; real parser does not check it; applicability to physical frames **UNCERTAIN** |

Sources for this table: `src/devices/echelonconnectsport/echelonconnectsport.cpp`,
`echelonconnectsport::characteristicChanged()` [parser][telemetry],
`GetElapsedFromPacket()`, `GetDistanceFromPacket()` [helpers][helpers];
`src/virtualdevices/virtualbike.cpp`, `virtualbike::echelonWriteStatus()`
[emulated packet construction][virtual-telemetry].

Distance units and full width are **UNCERTAIN**: the virtual writer multiplies its
odometer by `1.60934 * 100`, while the real helper uses only two bytes divided by
100. Also, the two real helpers combine `QByteArray::at()` values without explicit
unsigned-byte casts; do not assume robust unsigned decoding from these expressions.
The returned packet distance is logged, while QZ's accumulated distance is instead
integrated from computed speed and elapsed notification time. Sources: driver
`GetDistanceFromPacket()`, `GetElapsedFromPacket()`, `characteristicChanged()`
[helpers][helpers], [integration][telemetry]; virtual writer [source][virtual-telemetry].

### Cadence and speed

The real parser takes offset 10 as an unsigned byte and assigns cadence directly,
without scaling, when the external cadence-sensor setting begins with `Disabled`.
It does not calculate cadence from crank revolutions in this packet. Source:
driver `echelonconnectsport::characteristicChanged()` [source][telemetry].

Speed is **derived**, not decoded: the non-power-based branch computes
`0.37497622 × cadence` in QZ's km/h convention (also evidenced by its distance
integration divisor of 3,600,000 for milliseconds). The alternative calls
`metric::calculateSpeedFromPower()` with power, inclination, prior speed, elapsed
time, and a speed limit. **UNCERTAIN:** accuracy/calibration for the EX-5s.
Source: driver `echelonconnectsport::characteristicChanged()` [source][telemetry].

### Power: no watt field decoded

`echelonconnectsport::watts()` returns zero when current cadence is zero. Otherwise,
it uses `m_watt` if an external power sensor is configured; without that sensor,
it calls `wattsFromResistance(Resistance.value())`. No direct watts field is read
from the bike notification by this driver. Source: driver `watts()` [source][power].

`wattsFromResistance()` uses one of two 33-row, 11-column tables, indexed by
resistance and cadence in 10 rpm steps. It clamps the integer resistance index
to 0–32, interpolates between cadence columns below 100 rpm, and scales the
100 rpm entry proportionally at higher cadence. Selection `mgarcea` uses the
alternative table; otherwise it uses the default table. With an external sensor,
the helper instead uses the learned ERG table for estimates. The table data is
intentionally not reproduced here. Sources: driver `wattsFromResistance()`
[source][power]; `src/qzsettings.cpp`, `QZSettings::default_echelon_watttable`
(default `Echelon`) [setting][settings].

**UNCERTAIN:** whether either power table is calibrated for EX-5/EX-5s, and its
accuracy across firmware, cadence, and individual bikes. A future app must label
such power as estimated unless a separately validated measurement source exists.
The inspected selection is setting-based, not an EX-5s model branch.
Source: driver `wattsFromResistance()` [source][power].

### Resistance notification

The real parser accepts exactly five bytes beginning `F0 D2`, takes byte 3 as the
resistance level, updates `Resistance`, and emits `resistanceRead`. It does not
check byte 2, the final checksum, or the allowed resistance range here. The
virtual writer constructs `F0 D2 01 R C`, with additive checksum modulo 256.
Sources: driver `characteristicChanged()` [real parser][telemetry];
`virtualbike::echelonWriteResistance()` [virtual writer][virtual-telemetry].

The driver notes that resistance arrives when it changes and retains the value
between status packets. **UNCERTAIN:** initial report guarantees, notification
cadence, physical-position confirmation, and whether every manual knob change is
reported on EX-5s. A D2 report is not a request-correlated acknowledgment in this
driver. The optional `gears_from_bike` handling also interprets selected changes
as QZ gear adjustments. Source: driver `characteristicChanged()` [source][telemetry].

## Resistance command format and control limits

`echelonconnectsport::forceResistance()` sends five bytes to the `...f2`
characteristic: `F0 B1 01 R C`, where `R` is the requested level and
`C = (F0 + B1 + 01 + R) modulo 256`. For example, level 1 gives
`F0 B1 01 01 A3`; level 32 gives `F0 B1 01 20 C2`. These examples are arithmetic
derivations from the source format, not hardware-tested commands.
Source: driver `forceResistance()` [source][command].

`update()` clamps ordinary pending requests to 1–32, skips writing when the request
equals current resistance, then clears the request. `max_resistance` is 32 in the
header. `forceResistance()` itself does not clamp. It waits through the generic
notification/timeout helper, with no command-specific success result or actuation
verification. Sources: driver `update()` [source][update], `forceResistance()`
[source][command], `writeCharacteristic()` [source][write]; class constants [header][header].

The inspected `update()` stop branch clears a request but sends no physical stop
command. The normal resistance dispatch branch has no explicit `noWriteResistance`
guard, despite the constructor retaining that flag and passing it to virtual-device
construction. This flag alone is not evidence of a fail-safe transport interlock.
**UNCERTAIN:** supported motorized control on the particular EX-5s, acceptable
ramp rate, motor latency, timeout recovery, and safe physical resistance bounds.
Sources: driver constructor, `update()`, `createVirtualBike()` [driver][driver].

## Reconnection behavior

On `UnconnectedState`, `echelonconnectsport::controllerStateChanged()` saves the
current resistance, sets `initDone=false`, and immediately calls `connectToDevice()`
on the Qt path. No backoff or retry limit appears in that callback. Once discovery
and initialization repeat, `btinit()` automatically sends the saved resistance
and clears the saved marker. This bypasses the ordinary `update()` request clamp.
Sources: driver `controllerStateChanged()` [reconnect][reconnect], `btinit()` [restore][init].

Connection error/disconnect callbacks emit `disconnected`; `update()` also emits
it while unconnected. The driver does not reset cadence/resistance to unavailable
in the reconnect callback, and does not implement a telemetry freshness interlock
there. The discovery manager's connection from this driver's `disconnected` signal
to `bluetooth::restart()` is commented out. Sources: driver `deviceDiscovered()`
[callbacks][connect], `update()` [loop][update], `controllerStateChanged()`
[state][reconnect]; `bluetooth::deviceDiscovered()` [manager][discovery].

These are QZ behaviors, not proposed Echelon Overlay policy. Our documented policy
remains disarm on loss/fault, discard pending targets, invalidate stale readings,
and require explicit rider re-arming after reconnect. **UNCERTAIN:** actual bike
resistance retention, connection exclusivity, and disconnect watchdog behavior.

## EX-5s-specific evidence and limits

- No EX-5/EX-5s conditional, separate UUID set, or 22-inch-console special case was
  found in the inspected shared driver/header or its discovery branch. A model
  name alone cannot establish protocol compatibility. Sources: class
  `echelonconnectsport` [driver][driver], [header][header];
  `bluetooth::deviceDiscovered()` [routing][discovery].
- `src/virtualdevices/virtualbike.cpp`, `virtualbike::virtualbike()` uses
  `ECHEX-5s-113399` as a fallback advertised name, or the real connected Echelon's
  name when available. This is emulation/bridging evidence, not a detected EX-5s
  model or proof of console support [source][virtual].
- The real driver detects an eight-byte `F0 E0 ...` frame as a locked-bike prompt
  trigger, and recognizes `F0 A5 01 0E A4` as an unlock-response marker. A virtual
  bridge can forward app commands to the real bike and relay notifications.
  `proxyVirtualBikeCommand()` also heuristically marks the unlock flow as seen
  for a non-A0 F0 command because the A5 reply may not surface. **UNCERTAIN:**
  which EX-5s firmware needs this, the complete unlock exchange, and whether the
  basic initialization sequence is sufficient. Sources: driver
  `characteristicChanged()`, `maybePromptToEnableVirtualEchelon()`,
  `maybePromptForClassicBridge()`, `proxyVirtualBikeCommand()` [driver][driver];
  `virtualbike::characteristicChanged()`, `relayEchelonPacket()` [bridge][virtual-replies].
- **UNCERTAIN:** console-to-controller transport availability to a sideloaded app,
  stock Echelon app ownership of the BLE link, simultaneous clients, pairing/bonding,
  and Android permissions/background restrictions on the 22-inch console. The
  inspected driver establishes an external BLE central path, not these deployment
  properties. Source boundary: `echelonconnectsport::deviceDiscovered()` [source][connect].

## Follow-up validation before implementation

These are project recommendations, not claims from QZ: capture the actual bike's
advertised name, firmware, service/characteristic properties, and notification
channels; collect read-only packet traces while changing cadence and resistance
manually; validate frame lengths, checksums, distance units, initial/stale readings,
and reconnect behavior. Only after a separate control-safety review should a
supervised write experiment verify motor response and acknowledgments. Do not
reuse QZ's lookup tables or assume its reconnect/timeout behavior is our policy.

[driver]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp
[header]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.h
[discovery]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/bluetooth.cpp#L2414-L2430
[test]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/tst/Devices/devicetestdataindex.cpp#L322-L327
[settings]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/qzsettings.cpp#L266-L267
[write]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L38-L97
[command]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L99-L109
[update]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L21-L191
[telemetry]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L249-L390
[helpers]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L392-L402
[init]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L404-L432
[subscribe]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L434-L524
[descriptor]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L526-L531
[services]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L539-L563
[connect]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L683-L735
[power]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L774-L900
[reconnect]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp#L902-L923
[virtual]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/virtualdevices/virtualbike.cpp#L21-L42
[virtual-services]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/virtualdevices/virtualbike.cpp#L568-L599
[virtual-replies]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/virtualdevices/virtualbike.cpp#L1599-L1720
[virtual-telemetry]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/virtualdevices/virtualbike.cpp#L2229-L2307
