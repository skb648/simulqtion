# Reality Compiler — Phase 2D Validation

## Purpose

Phase 2D makes the existing scanner, reconstruction, Digital Twin and simulation pipeline reproducible and traceable for a future real-device run. It does **not** claim physical accuracy without physical evidence.

## Validation state machine

`NOT_STARTED → DEVICE_CHECK → SCANNING → RECONSTRUCTION → MEASUREMENT → REPEATABILITY → MOTOR_VALIDATION → SIMULATION_VALIDATION → COMPLETE`

`BLOCKED` and `FAILED` are explicit terminal/interruption states. `COMPLETE` is rejected unless required physical evidence exists: captured scan, reconstructed measurement, reconstruction evidence, Digital Twin ID and simulation ID.

## Evidence classes

- `SOFTWARE_VERIFIED`: deterministic software behavior verified by automated tests.
- `DEVICE_CAPABILITY_REPORTED`: API-reported capability; not proof of physical behavior.
- `DEVICE_MEASURED`: observation from a real device run.
- `PHYSICAL_REFERENCE_MEASURED`: ruler/caliper/user-provided physical reference.
- `RECONSTRUCTION_MEASURED`: measurement made from the reconstructed artifact.
- `SIMULATION_EXECUTED`: existing physics model actually executed.
- `USER_REPORTED`: explicitly supplied by tester.
- `UNKNOWN`: not established.

`NOT MEASURED` never becomes `PASS` automatically.

## Physical procedure

1. Build/install the debug APK from the exact Git commit under test.
2. Open Physical Device Validation.
3. Run the capability checklist and record the report.
4. Enter a precisely measured reference dimension and its unit/method.
5. Capture distinct views of the reference object.
6. Reconstruct using the existing backend pipeline.
7. Select two reconstructed endpoints corresponding to the physical reference.
8. Record reconstructed distance, absolute error, relative error and scale factor.
9. Repeat the reference scan up to five times and report N, mean, median, standard deviation, min, max and range.
10. Review timestamp distribution, tracking losses, frame counts, calibration and reconstruction metrics.
11. Scan the real DC motor and record only observed/measured geometry; keep inferred/estimated/unknown properties separate.
12. Generate the Digital Twin and execute the existing baseline simulation and What-If flow.
13. Export the validation session and offline package.

## Export traceability

Every session should carry session ID, scan ID, software version, artifact schema version, device identity, timestamp, artifact/Digital Twin/simulation IDs and evidence classification. The offline ZIP contains `validation-session.json` plus selected captured files.

The existing artifact schema remains `2.1`; Phase 2D validation metadata is additive and does not reinterpret 2.1 artifacts.

## Backend replay

`backend/app/validation_replay.py` deterministically recomputes reference and timestamp statistics from previously captured validation JSON. It never synthesizes camera frames or physical measurements. Use it to debug a captured run after the phone is no longer available.

## Security

Do not commit credentials, API keys or production secrets. Camera files and validation packages are engineering data and should only be retained/exported when intentionally requested. Production endpoints must use authenticated HTTPS configuration rather than hardcoded credentials.

## Performance

Real device FPS, memory, CPU, upload size, backend latency and reconstruction runtime are deliberately left unpopulated until a physical run. Synthetic/golden fixtures are software tests only and are never physical performance claims.

## Acceptance

Software-complete means CI passes and the validation infrastructure is deterministic and traceable. Physical validation remains `BLOCKED` until a real Android device produces the required evidence.
