# Reality Compiler — Phase 2C Physical Validation

## Purpose

Phase 2C validates the existing Phase 2B capture/reconstruction pipeline on real Android hardware and measures metric accuracy. No physical measurement is inferred from simulation output.

## Current execution status

**Physical-device validation: BLOCKED** when no supported Android device is attached to the execution environment.

The software-side validation infrastructure may be built and tested in CI, but hardware observations must come from an actual device.

## Required hardware

- Android phone with rear camera
- Google ARCore supported and installed
- ARCore Shared Camera support
- gyroscope preferred; accelerometer required for the existing sensor path
- stable, repeatable lighting
- ruler or preferably caliper with known measurement resolution
- reference object with a measured dimension
- reachable backend endpoint for reconstruction

## Test record

For every run record:

- run ID / scan ID
- device manufacturer and model
- Android version
- ARCore version and availability
- Shared Camera support
- depth support
- camera ID and capture resolution
- fx, fy, cx, cy and distortion parameters
- lighting condition
- object distance
- object orientation
- network type
- backend endpoint/configuration
- test date/time

## Reference measurement

Before scanning, measure a physical feature with a traceable method. Enter:

- `referenceValue`
- `referenceUnit`
- `referenceAxis`
- `measurementMethod`
- `measurementConfidence`

The physical measurement must correspond to an explicitly selected pair of reconstructed points. Do not compare an arbitrary bounding-box axis to a physical measurement unless that correspondence is documented.

## Scan procedure

1. Install the debug APK on the real device.
2. Grant camera permission.
3. Open the scanner.
4. Open **Device diagnostics** and record device/camera/ARCore capabilities.
5. Verify Shared Camera is active where supported.
6. Place the reference object under controlled lighting.
7. Record its measured dimension and the exact endpoints/axis.
8. Capture a trajectory with distinct viewpoints; avoid repeated near-identical frames.
9. Record timestamp deltas and rejected frames.
10. Upload the scan.
11. Run reconstruction.
12. Inspect the real point cloud.
13. Measure the corresponding reconstructed distance.
14. Calculate absolute error, relative error and scale factor.
15. Export the validation run.
16. Repeat for additional orientations/distances/lighting conditions.
17. After the reference benchmark, repeat with the real DC motor.
18. Generate the Digital Twin and run the existing baseline and What-If simulation.

## Accuracy metrics

For reference `R` and reconstructed measurement `M`:

- absolute error = `|M - R|`
- relative error (%) = `|M - R| / R × 100`
- scale factor = `M / R`

Raw values are persisted; reporting must not round away meaningful error.

## Repeatability

For repeated measurements, report:

- sample count
- mean
- median
- standard deviation
- minimum
- maximum
- range
- 95th percentile when sample count is sufficient

A small sample is descriptive only; it must not be presented as statistically significant evidence.

## Timestamp validation

For each accepted frame store image timestamp, ARCore timestamp and delta. The current Phase 2B acceptance threshold is 20 ms.

Report:

- minimum delta
- maximum delta
- mean
- median
- standard deviation
- percentage within 20 ms
- rejected count and rejection reason

Do not relax the threshold merely to increase acceptance rate.

## Test matrix

Recommended baseline:

| Variable | Conditions |
|---|---|
| Repeatability | 5 scans minimum |
| Object orientation | multiple orientations |
| Camera distance | 30 cm, 50 cm, 100 cm, 150 cm where practical |
| Lighting | good, moderate, poor |
| Viewpoint motion | small, moderate, large |

Each row must contain measured results, not expected values.

## Required reconstruction evidence

Each scan report should contain:

- total frames
- accepted frames
- rejected frames
- feature matches
- triangulated points
- reprojection error
- pose quality
- timestamp statistics
- coverage
- scale status
- reference measurement
- reconstructed measurement
- absolute error
- relative error
- point-cloud dimensions
- artifact ID
- Digital Twin ID

## Failure criteria

A test is **FAIL** when a required measurement is absent, pose is invalid, reconstruction is unusable, scale is falsely claimed, or artifacts are inconsistent.

A test is **BLOCKED** when the required physical hardware or external dependency is unavailable.

A test is **PASS** only when all required evidence is present and the recorded result is reproducible.

## DC motor validation

Measure external dimensions with a ruler/caliper. Keep the Digital Twin provenance explicit:

- **OBSERVED:** externally visible housing, shaft, mounting features actually captured
- **INFERRED:** probable motor category or structural interpretation
- **ESTIMATED:** simulation parameters not directly measured
- **HYPOTHESIS:** unverified interpretations
- **UNKNOWN:** exact winding configuration, magnet strength, bearing friction, rotor inertia unless separately measured

Geometry alone does not identify all physical motor parameters.

## Offline behavior

If the backend is unavailable, retain the local scan package and diagnostics. Do not convert an offline condition into a successful reconstruction claim.

## Evidence needed to close Phase 2C

At minimum, attach/export:

1. APK/build identifier.
2. Real device model and Android version.
3. ARCore version and Shared Camera capability.
4. Calibration values for the exact capture resolution.
5. Real timestamp statistics.
6. Known-size reference measurement and method.
7. Reconstructed measurement and raw error calculation.
8. Repeatability results.
9. Real DC motor scan and external measurements.
10. Point-cloud artifact and metadata.
11. Digital Twin provenance.
12. Baseline and What-If simulation results.

No numbers should be filled in until they are observed on physical hardware.
