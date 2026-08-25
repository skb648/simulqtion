# Digital Twin schema

Each property carries `value`, `unit`, `status`, `confidence`, and `evidence[]`. Status is one of `OBSERVED | INFERRED | ESTIMATED | HYPOTHESIS | UNKNOWN`.

Hidden motor structure is never inserted as observed data. Exact windings, magnet strength, bearing friction and exact rotor inertia remain unknown.

```json
{"motor_type":{"value":"brushed_dc_motor","status":"INFERRED","confidence":0.78,"evidence":[{"source":"perception","detail":"external form + shaft-like feature"}]}}
```
