# Workout engine

Pure Kotlin home for workout definitions, interval progression, and target intents.
No Android, transport, or UI dependencies. A workout produces a desired target;
it does not authorize or transmit a resistance command.

Inject monotonic time. Plan explicit idle, running, paused, completed, and aborted
states. Paused time does not advance intervals. Use a fake clock and table-driven
tests for interval boundaries, pause/resume, completion, and invalid definitions.
