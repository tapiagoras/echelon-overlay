# BLE Review

Review date: 2026-09-29. Decision: **NEEDS MORE RESEARCH** before hardware-control
implementation. The source supports a discovery/subscription experiment and pure
parser design, but does not establish EX-5s handshake requirements, actuation
confirmation, or safe control limits.

Reviewed local `AGENTS.md`, `docs/architecture.md`, and `docs/echelon-ble.md`.
Re-fetched the relevant QZ sources at revision
`0bd17860cce73ee945f8717761c899e180e921f3`; this review deliberately assesses that
documented revision, not an unpinned latest branch. No hardware was tested.

**VERIFIED** below means verified in QZ source, not verified on this bike.
**INFERRED** means a deduction; **UNCERTAIN** means further evidence is needed.
Recommendations are our design decisions, not QZ protocol claims. QZ is GPL-3.0:
this review describes facts independently and includes no copied implementation
or lookup tables. No application code was changed.

## Verified

The following source keys identify exact files; all links are pinned:

- **D**: [`src/devices/echelonconnectsport/echelonconnectsport.cpp`][D], class `echelonconnectsport`.
- **H**: [`src/devices/echelonconnectsport/echelonconnectsport.h`][H], class `echelonconnectsport`.
- **B**: [`src/devices/bluetooth.cpp`][B], `bluetooth::deviceDiscovered()`.
- **V**: [`src/virtualdevices/virtualbike.cpp`][V], class `virtualbike`.
- **S**: [`src/qzsettings.cpp`][S], `QZSettings::default_echelon_watttable`.
- **T**: [`tst/Devices/devicetestdataindex.cpp`][T], `DeviceTestDataIndex::Initialize()`.

### Verification matrix

All entries in the behavior column are **VERIFIED in source**. Model-specific
hardware applicability is addressed in the next section.

| Review item | Behavior verified | Exact source/function |
| --- | --- | --- |
| 1. BLE service | Real central opens `0bf669f1-45f2-11e7-9598-0800200c9a66`. `...f0` is additionally defined by the virtual peripheral, not required by this central path. | D `serviceScanDone()` (539–563); V constructor (568–599) |
| 2. Characteristics | Writes: `0bf669f2-45f2-11e7-9598-0800200c9a66`; notifications: `0bf669f3-45f2-11e7-9598-0800200c9a66` and `0bf669f4-45f2-11e7-9598-0800200c9a66`. | D `stateChanged()` (434–524) |
| 3. Identification | Generic case-sensitive `ECH` prefix selects shared bike driver after earlier device branches and filters. Earlier branches distinguish Echelon rower, treadmill, and stairclimber names. T tests prefix routing, not EX-5s hardware. | B `deviceDiscovered()` (2274–2430); D `deviceDiscovered()` (683–735); T `Initialize()` (322–327) |
| 4. Connection | Create central, connect, discover services, open communication service, discover characteristics. Missing service triggers disconnect; characteristic presence uses assertions. | D `deviceDiscovered()`, `serviceScanDone()`, `stateChanged()` |
| 5. Notifications | Attach one shared parser, request `01 00` on notify 1 CCCD then notify 2 CCCD. Each descriptor-written callback sets `initRequest` and emits `connectedAndDiscovered`; there is no two-completion barrier. | D `stateChanged()`, `descriptorWritten()` (526–531), `update()` (127–191) |
| 6. Initialization | Writes `F0 A1 00 91` four times, `F0 A3 00 93` once, A1 once more, then `F0 B0 01 01 A2`. Sets `initDone` without validating a protocol success reply. These are QZ's chosen writes, not proven mandatory EX-5s commands. | D `btinit()` (404–432), `writeCharacteristic()` (38–97) |
| 7. Packet structure | D2 branch accepts five bytes with `F0 D2` prefix. Otherwise status decoding accepts any 13-byte value; D1 header validation is commented out. No incoming checksum/length-byte validation or fragment reassembly appears here. V generates `F0 D1 09`, nine payload bytes, and additive checksum. | D `characteristicChanged()` (249–390); V `echelonWriteStatus()` (2229–2268) |
| 8. Cadence | Status offset 10 is cast to unsigned byte, assigned directly when external cadence sensor is disabled. | D `characteristicChanged()` |
| 9. Power | No watt field is decoded. Zero cadence gives zero watts; otherwise external power is used if configured, or cadence/resistance tables estimate watts. Two tables are selectable; default setting is `Echelon`. | D `watts()`, `wattsFromResistance()` (774–900); S named constant (266–267) |
| 10. Resistance | D2 offset 3 is assigned to resistance without unsigned normalization or range/checksum validation. Stored resistance persists between updates; QZ comments describe change-triggered reporting. | D `characteristicChanged()` |
| 11. Resistance write | `F0 B1 01 R C`, checksum sum of preceding bytes modulo 256. Ordinary queued requests are clamped to 1–32; `forceResistance()` itself has no clamp. | D `forceResistance()` (99–109), `update()`; H `max_resistance`, `maxResistance()` |
| 12. Responses | Initialization, poll, and resistance paths wait for any service notification or 300 ms timeout. Neither result is a validated protocol acknowledgment. Qt write mode defaults to `WriteWithResponse`; the helper's wait flag does not change it. | D `writeCharacteristic()`, `btinit()`, `sendPoll()`, `forceResistance()`; [Qt API][Qt] |
| 13. Reconnect | Saves resistance, clears `initDone`, immediately reconnects; subsequent `btinit()` sends the saved level automatically. No backoff/re-arm policy in that callback. | D `controllerStateChanged()` (902–923), `btinit()` |
| 14. Model differences | No EX-5 versus EX-5s branch is present in the inspected shared driver/header or generic discovery branch. Other Echelon equipment has earlier discovery branches. This is a bounded finding, not a repository-wide compatibility certification. | D class, H class, B `deviceDiscovered()` |
| 15. EX-5s evidence | V's fallback advertised name is `ECHEX-5s-113399`; a real Echelon connection can supply its own name. D recognizes an eight-byte `F0 E0` prefix as a locked-bike prompt trigger and an exact `F0 A5 01 0E A4` unlock marker, but does not tie these to an EX-5s model. | V constructor (21–42); D `maybePromptToEnableVirtualEchelon()` (599–612), `characteristicChanged()`, `proxyVirtualBikeCommand()` (737–755) |

Additional **VERIFIED** details:

- D `sendPoll()` (111–125), `update()`, and H counters specify `F0 A0 01 N C`,
  with an 8-bit counter cycling 1–255 and additive checksum. **INFERRED:** the
  post-increment comparison gives 11 eligible 200 ms timer calls, nominally
  2.2 seconds; this is not a measured or required keepalive interval. [D][D], [H][H]
- D `characteristicChanged()` derives speed from cadence using `0.37497622`, or
  from a power-based speed model. Its packet distance helper reads offsets 7–8
  divided by 100; elapsed helper reads offsets 3–4. V uses four distance bytes
  at 5–8 and writes a heart-rate byte at 11. These are different evidence sources,
  not confirmation of a full physical packet specification. [D][D], [V][V]
- V `characteristicChanged()` (1599–1695) synthesizes A1/A3/A4/A5/D0 replies and
  echoes A0 in emulation. With a connected real Echelon it instead delegates to
  D `proxyVirtualBikeCommand()` and relays actual notifications through
  V `relayEchelonPacket()`. Emulated replies are not captured EX-5s replies. [V][V], [D][D]

### Corrections and clarifications made to the research

The primary UUIDs, byte offsets used by QZ, initialization order, and resistance
command checksum matched source. Updated `echelon-ble.md` to:

1. Distinguish VERIFIED, INFERRED, and UNCERTAIN, and explain earlier discovery
   branches. A prefix is not positive EX-5s identification. B `deviceDiscovered()`.
2. Resolve the previously unspecified default write mode and separate transport
   response from protocol/physical confirmation. D `writeCharacteristic()`; [Qt API][Qt].
3. Clarify CCCD request order, Qt FIFO serialization, and lack of a readiness
   barrier. Duplicate initialization scheduling is **INFERRED**, dependent on
   callback order, not an observed bug. D `stateChanged()`, `descriptorWritten()`,
   `update()`; [Qt service interaction][Qt].
4. Correct the ambiguous stop wording: `requestStop` alone is cleared, and
   resistance processing comes first. D `update()`.
5. Highlight the resistance-byte signedness issue. D `characteristicChanged()`.
6. Replace an ambiguous “read-only” test description: subscription needs CCCD
   writes, whereas initialization/poll/control writes remain excluded. This is
   our test boundary, motivated by D `btinit()`, `sendPoll()`, `forceResistance()`.

## Uncertain

| Status | Open question | Evidence boundary / how to resolve |
| --- | --- | --- |
| UNCERTAIN | Does this EX-5s advertise the expected service and expose it to its own console? Is stock software already the central? | D `deviceDiscovered()` establishes only QZ's central behavior. Record actual advertisement, GATT inventory, console OS, firmware, and connection ownership. |
| INFERRED | `ECH` plus matching GATT schema is a candidate Echelon bike. | B `deviceDiscovered()` and V constructor demonstrate that naming alone can match other equipment or an emulator. Confirm the physical device manually. |
| UNCERTAIN | Which A1/A3/B0 writes are necessary, sufficient, or safe? Does this firmware require unlocking? | D `btinit()` has a repetition comment expressing uncertainty; locked/bridge functions are heuristic. Capture the legitimate stock exchange before choosing a profile. |
| INFERRED | D1 byte 2 denotes nine payload bytes; last byte is an additive checksum. | V `echelonWriteStatus()` constructs those values; D does not validate them. Verify against physical captures, including multiple values. |
| UNCERTAIN | Distance width/units, elapsed rollover, byte 9, byte 11, and notification channel assignments. | D helpers disagree with V on distance width; V's heart-rate field is not decoded by D. No invented fields in the first parser. |
| UNCERTAIN | Initial resistance, reporting when unchanged, cadence-zero timing, loss/silence behavior, fragmentation. | D `characteristicChanged()` assumes complete 5/13-byte notifications and retains resistance. Record timing and raw boundaries, not just decoded values. |
| UNCERTAIN | Does a B1 command physically change this bike, what confirms it, and how long does it take? | D `forceResistance()` and generic wait do not prove actuation. D2 can be spontaneous. Requires a later supervised, separately bounded control test. |
| UNCERTAIN | Safe range/ramp, manual override precedence, watchdog, and resistance after disconnect. | H's 32 is a software limit, not safety calibration; D reconnect restores without re-arming. Must verify hardware behavior and define policy independently. |
| UNCERTAIN | EX-5 versus EX-5s/firmware differences, including 22-inch console access and lifecycle limits. | No distinguishing path in inspected D/H/B; V's EX-5s name is emulation. Record exact hardware/firmware and validate on the intended host. |
| UNCERTAIN | Estimated watts and speed accuracy. | D `wattsFromResistance()` and `characteristicChanged()` supply models, not direct measurements or EX-5s calibration evidence. Leave unavailable initially. |

## Risks

These are review findings tied to the source observations above, with proposed
mitigations for this project.

| Priority | Risk | Evidence and implication |
| --- | --- | --- |
| Blocking for control | False success | D `writeCharacteristic()` can finish on unrelated notification or timeout. Separate transport completion, protocol response, reported level, and physical confirmation; never promote one into another. |
| Blocking for control | Uncommanded restoration | D `controllerStateChanged()` plus `btinit()` restores saved resistance. Our reconnect must remain disarmed, discard old commands, and invalidate old callbacks. |
| Blocking for control | Stop does not inhibit writes | D `update()` processes resistance before clearing only `requestStop`; ERG requests can remain active. Our stop must invalidate dispatch authorization before any next write. |
| High | Premature readiness | D `descriptorWritten()` announces discovery before initialization and without tracking both subscriptions. Use distinct connected/discovered/subscribed/telemetry-valid states. |
| High | Wrong or malformed telemetry | D accepts any 13-byte frame and unvalidated D2 data. Its helpers also lack unsigned-byte normalization. Validate captured framing, use unsigned decoding, retain unknown packets without treating them as telemetry. |
| High | Stale resistance disguised as fresh | D `characteristicChanged()` reassigns existing resistance during status processing, while real D2 reports may be change-only. Preserve each field's last actual observation time separately from connection liveness. Define freshness from physical evidence. |
| High | Unknown initialization side effects | D `btinit()` writes B0 and can restore B1 after reconnect. Do not use QZ itself as a guaranteed notification-only probe or copy its init routine into an initial diagnostic client. |
| High | Overbroad identification | B matches a prefix; V can advertise an EX-5s name. Require user-selected physical identity and observed GATT capabilities before any future control. |
| High | Connection/lifecycle assumptions | D reconnect callback has no visible backoff, cancellation, or stale-session check. **INFERRED risk:** contention/retry loops and late events. Use bounded retries, session IDs, explicit close, and one owner. |
| High | Unsupported console deployment | The inspected code does not establish permissions, stock-app coexistence, or foreground-service survival on EX-5s. An external-phone success does not prove on-console feasibility. |
| Medium | Estimated power mistaken for measurement | D `watts()`/`wattsFromResistance()` derive power unless external. Keep power unavailable in the first diagnostic phase; independently validate any later estimator. |

The design in `docs/architecture.md` already requires disarming on faults,
monotonic freshness, and separation of workout intents from safety authorization.
Retain those requirements. Neither QZ's `noWriteResistance` member nor a UI stop
button alone is proof that every physical write path is inhibited (D constructor,
`update()`, `forceResistance()`, `proxyVirtualBikeCommand()`).

## First Physical Test

**Goal:** establish that the actual EX-5s controller is discoverable/connectable
and record its GATT schema and any spontaneous telemetry, without writing vendor
commands. This is a manual diagnostic test, not a workout or resistance-control test.

1. Record EX-5s model/console size, available bike/controller firmware, console
   Android/API version, current resistance, and whether the stock app is running.
   Keep the bike stationary initially and preserve access to its physical controls.
   Use an existing GATT inspection tool with automatic vendor commands disabled.
   Prefer the console if it supports the tool; otherwise use an external Android
   device and label the result “external central only.” No app changes are needed.
2. Scan for up to 60 seconds. Record name, advertised services, and a locally kept
   device identifier. Confirm which physical bike was found; do not connect solely
   because a name starts with `ECH`. If occupied/unavailable, record it, explicitly
   release the stock app's session through normal means, and perform one retry.
   Do not assume simultaneous connections are supported.
3. Connect once and discover services. Record the full UUIDs, characteristic
   properties, CCCDs, any pairing prompt, and result. If `...f1`/`...f2`/`...f3`/
   `...f4` are missing, stop and retain the inventory; do not guess replacements.
4. Subscribe to `...f3` then `...f4`, waiting for each CCCD `01 00` write result.
   These two descriptor writes are the only intended attribute writes. Send no
   A1/A3/B0 initialization, A0 polls, unlock bytes, B1 commands, or automatic
   reconnect/restore commands. This is subscription-only, not literally zero-write.
5. Capture up to 60 seconds of notifications with monotonic timestamps, source
   characteristic, raw bytes, lengths, and connection events. If manual bike
   controls operate normally, pedal slowly, stop, and optionally make one small
   manual resistance change within a comfortable existing setting. Log those
   actions so byte 10 and D2 byte 3 can be compared with observations. Do not
   claim independent cadence validation without a separate reference measurement.
6. Disconnect once, verify normal manual operation, and finish. Stop sooner for
   unexpected motor movement, loss of manual control, unexpected requests, or
   connection failure. No repeated retries or resistance fallback commands.

**Evidence to retain:** firmware/host context; redacted advertisement and GATT
inventory; descriptor outcomes; timestamped raw frames and manual action notes;
whether stock software had to disconnect; and disconnect outcome. Keep raw device
identifiers private, consistent with `AGENTS.md`.

**Pass for discovery:** expected schema found and both subscriptions succeed.
**Pass for passive telemetry:** captured packets correlate with manual observations.
**Inconclusive is valid:** no data after subscription does not prove incompatibility.
It may require initialization/unlocking. Stop and arrange a separate capture of
the stock app's normal handshake; do not escalate this test into guessed writes.
Neither pass authorizes resistance control or proves long-running console support.

## Implementation Recommendation

**NEEDS MORE RESEARCH for hardware-control implementation.** The first physical
test should precede selection of a production handshake/control profile. UUIDs,
candidate packet formats, and QZ behavior are sufficiently understood for a bounded
diagnostic reader and deterministic parser work in a later authorized task.

Minimum proposed types, consistent with the current module boundaries:

| Module | Proposed interface/class | Initial responsibility |
| --- | --- | --- |
| `core/bike` | `BikeTelemetrySource` | Connect/disconnect and expose connection state, capability observations, and telemetry; no resistance-write API initially. |
| `core/bike` | `BikeSample`, `ConnectionState`, `BikeCapabilities` | Units, per-field availability/provenance/timestamp, session identity, and observed capability evidence. Missing watts/speed remain unavailable. |
| `data/bike-ble` (future) | `AndroidBleTransport` with internal serialized operation queue | Scan, discover, subscribe sequentially, deliver raw frames, timeout/cancel, and close; injectable transport boundary for tests. No public arbitrary vendor-write escape hatch. |
| `data/bike-ble` (future) | `EchelonTelemetryDecoder` | Pure Kotlin parser, independent of Android APIs; decode only verified capture profiles and preserve rejected/unknown frames. JVM-testable despite adapter ownership. |
| `app` | `BikeSessionCoordinator` | Own one session, reject old callbacks, map samples, manage permission/lifecycle loss, and stop diagnostics. UI remains an observer. |
| `core/safety` | `ControlPolicy` | Default-deny policy before any command API is added; cannot be bypassed by UI/workout requests. Diagnostic phase exposes no control capability. |

Inject a monotonic clock and fake transport; no need for a separate timing framework.
Test valid/malformed captured frames, unsigned byte boundaries, duplicate/late
callbacks, either CCCD failing, timeout, disconnect, stale per-field data, and
absence of vendor writes. Add a hardware-validated initialization profile only
after its required commands/responses are known. Do not reuse QZ implementation
structure or table contents; write original Kotlin against documented observations.

Before enabling control, require evidence for: exact EX-5s identity/firmware and
host access; complete initialization with meaningful readiness criteria; B1
response and observed actuation semantics; validated limits/latency/ramp; manual
override and stop ordering; stale-data and disconnect policy; and failure tests
showing no writes after disarm/reconnect/process restoration. Protocol ambiguity
may require conservative refusal to control rather than an invented acknowledgment.

[D]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.cpp
[H]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/echelonconnectsport/echelonconnectsport.h
[B]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/devices/bluetooth.cpp#L2274-L2430
[V]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/virtualdevices/virtualbike.cpp
[S]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/src/qzsettings.cpp#L266-L267
[T]: https://github.com/cagnulein/qdomyos-zwift/blob/0bd17860cce73ee945f8717761c899e180e921f3/tst/Devices/devicetestdataindex.cpp#L322-L327
[Qt]: https://doc.qt.io/archives/qt-5.15/qlowenergyservice.html#writeCharacteristic
