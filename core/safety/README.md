# Safety policy

Pure Kotlin home for automatic-control authorization and fault handling.
No Android or transport dependencies. No active control policy is implemented yet.

Future decisions must explicitly allow or reject a target with a reason. Inputs
include rider arming, connection/session identity, telemetry freshness, confirmed
resistance, validated device bounds, command status, and monotonic time. Default to
disarmed. Unknown capabilities, stale/invalid telemetry, timeouts, or disconnects
must inhibit automatic commands. Validate limits against the actual bike before
enabling control; do not invent a safe resistance range or ramp rate.

Test every rejection path, manual override, stop, reconnect, and delayed response.
Software stop means stopping automatic commands; it cannot guarantee that the
bike physically lowers resistance or stops its flywheel.
