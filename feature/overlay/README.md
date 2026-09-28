# Overlay presentation

Reserved Android library for the minimal workout overlay. No overlay is installed
or displayed yet. It will render immutable session snapshots and emit rider
actions to the coordinator. It has no dependency on bike, safety, or workout modules.

Plan cadence, power, speed, resistance, interval time, connection/fault status,
and accessible pause/stop controls. Missing readings display as unavailable.
Requested and confirmed resistance must remain distinguishable. Avoid covering
video controls. Overlay denial/removal must not hide the ability to stop control:
the session notification and main activity provide equivalent actions.
