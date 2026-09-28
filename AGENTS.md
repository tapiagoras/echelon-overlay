# Development rules

These rules apply throughout this repository.

## Scope and implementation

- Use Kotlin for Android and Kotlin DSL for Gradle. Keep changes focused and
  document which features are working versus planned.
- Do not implement BLE in the initialization phase. Add a BLE adapter only when
  explicitly requested in a later task.
- Do not copy or port code from qDomyos-Zwift. Use original implementations and
  documented protocol evidence; record provenance and licenses for other sources.
- Do not infer EX-5s UUIDs, packet formats, resistance bounds, acknowledgment
  semantics, or safe ramp rates. Record unknowns and verify on the target hardware.

## Boundaries

- Keep `core/bike`, `core/workout`, and `core/safety` pure Kotlin with no Android,
  Bluetooth, UI, or wall-clock dependencies. Inject clocks and external inputs.
- Workouts produce target intents. Safety authorizes them. Only the session
  coordinator may dispatch authorized commands through the bike boundary.
- Overlay UI renders snapshots and sends rider actions to the coordinator; it
  must never own a BLE connection, run the workout clock, or bypass safety.
- Keep Android lifecycle, permissions, and future foreground-service ownership in
  `app`; place eventual BLE platform code in a separate adapter module.
- Prefer explicit constructor injection, immutable state, and small interfaces.
  Avoid global mutable state and unnecessary frameworks.

## Reliability and safety

- Default automatic resistance control to disabled. Require explicit rider arming
  and validated capabilities before writes. Never auto-resume control after
  process death, reconnect, permission loss, or a safety fault.
- Treat missing, stale, malformed, or out-of-range telemetry as unavailable, not
  zero. Track units, freshness, and requested versus confirmed resistance.
- Serialize commands, bound pending work, rate-limit using validated limits, and
  handle timeouts explicitly. Discard targets from old sessions. Do not blindly
  retry writes or automatically command minimum resistance on failures.
- Pause/stop/manual override must cancel pending automatic targets. Preserve a
  visible notification stop action when the overlay is unavailable.
- Never claim software stop is a physical emergency stop. Document limitations
  and require supervised hardware validation before enabling actual control.
- Keep logs bounded; do not commit device identifiers, credentials, captures with
  personal information, signing keys, or local SDK paths.

## Verification

- Add deterministic JVM tests when introducing workout and safety behavior. Use
  fake bike interfaces and a fake monotonic clock; do not use real sleeps or BLE
  hardware in unit tests.
- Cover interval boundaries, pause/resume, stale data, disconnect/reconnect,
  command timeout, manual override, stop, and process restoration as applicable.
- Use Android tests for permissions, service lifecycle, notifications, and overlay
  behavior. Validate streaming coexistence and console firmware on real hardware.
- Run affected checks and Android assembly/lint when relevant. State exactly
  which checks ran and which were blocked; never equate empty tests with coverage.
- Keep dependencies pinned and document build setup and architecture changes.
