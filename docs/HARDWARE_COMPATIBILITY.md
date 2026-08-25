# Reality Compiler — Android Hardware Compatibility

## Scope

This document defines the hardware prerequisites for the **physical validation path**. It does not certify a device merely because Android APIs report a capability. Physical tracking, synchronization, and metric accuracy remain hardware measurements.

## Required

| Capability | Requirement | Validation meaning |
|---|---|---|
| Android | ARCore-supported Android device; this project is built as an Android application with the current project `minSdk` | Software prerequisite; model-specific ARCore support must still be checked at runtime |
| Rear camera | Camera permission + usable rear camera | Required for the scan |
| Camera2 | Camera2-compatible camera configuration | Required by the ARCore Shared Camera path |
| ARCore | Device must be on Google's supported-device list and have Google Play Services for AR available where applicable | Runtime support check is required |
| Shared Camera | ARCore Shared Camera feature must be supported by the runtime session | Required for pose/image synchronization validation |
| CPU image acquisition | Shared Camera CPU image reader path must initialize successfully | Required for the captured reconstruction image |
| Gyroscope | Available | Required for the intended ARCore motion-tracking validation path |
| Accelerometer | Available | Required for the intended ARCore motion-tracking validation path |
| Camera intrinsics | Intrinsics must be obtainable for the selected capture configuration | Required for calibrated reconstruction |
| Network | Reachable configured backend for cloud reconstruction | Required for backend reconstruction; local diagnostics remain possible without it |

## Recommended

- ARCore-certified device with recent Google Play Services for AR.
- Rear camera with autofocus and stable exposure.
- 4 GB RAM or more for practical field testing. This is a **test recommendation**, not an ARCore certification requirement.
- At least 2 GB free storage for temporary scan artifacts and exported validation packages.
- Stable Wi-Fi or 5G for uploads.
- Device with Depth API support for additional geometry evidence when available.

## Optional

- Magnetometer.
- Depth API.
- Hardware depth sensor.
- Higher-resolution/faster rear-camera modes, provided the selected configuration remains compatible with the calibrated capture path.

## ARCore support

ARCore support is model-specific. Google's supported-device catalog states that certification checks camera, motion sensors, device design, and CPU capability. It also distinguishes ARCore support from individual optional capabilities such as Depth API. Do not infer support from Android version alone. See Google's current supported-device list before selecting a validation phone.

## Runtime states

The application reports capabilities using:

- `AVAILABLE`
- `UNAVAILABLE`
- `UNKNOWN`
- `NOT_TESTED`

`AVAILABLE` means the relevant API/runtime capability was detected. It does **not** mean that physical tracking or metric accuracy has been validated.

## Physical validation gate

The validation path requires:

- camera permission
- ARCore
- Shared Camera
- gyroscope
- accelerometer
- camera intrinsics
- reachable backend

Depth and magnetometer are optional and must not block the validation session.

If a required capability is unavailable or untested, the validation gate must report `VALIDATION BLOCKED` and the reason.

## Device selection procedure

1. Check the exact model against Google's current ARCore supported-device catalog.
2. Install/update Google Play Services for AR where applicable.
3. Install the Reality Compiler debug APK.
4. Open **Physical Device Validation**.
5. Confirm every required capability is `AVAILABLE`.
6. Only then begin the reference-object benchmark.

A successful capability check is not a physical validation result. Hardware evidence must still be collected and exported.
