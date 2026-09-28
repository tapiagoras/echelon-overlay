# Bike boundary

Pure Kotlin home for telemetry, connection state, capabilities, and bike interfaces.
No BLE implementation or assumed EX-5s protocol is included.

Future samples must carry units, a monotonic reception timestamp, and explicit
unavailable/invalid states. Cadence (rpm), power (W), speed (m/s), and resistance
(device level with validated bounds) must not use zero to represent missing data.
Distinguish reported values from estimates and requested resistance from confirmed
resistance. An eventual Android BLE adapter will live in `:data:bike-ble`, depending
on this module. It must expose command outcomes and disconnects without hiding them.

Production command access belongs exclusively to the session coordinator after
safety authorization. Neither workout nor overlay modules may send bike commands.
