# Reality Compiler — Physical Device Installation

## 1. APK source

The CI workflow runs:

```text
gradle -p android/RealityCompiler testDebugUnitTest assembleDebug
```

The installable debug APK is produced at:

```text
android/RealityCompiler/app/build/outputs/apk/debug/app-debug.apk
```

CI also publishes the debug APK as a workflow artifact named:

```text
reality-compiler-debug-apk
```

Download that artifact from a successful **Reality Compiler CI** workflow run.

## 2. Device prerequisites

Use a real Android device that passes the requirements in `docs/HARDWARE_COMPATIBILITY.md`.

For ARCore validation, check the exact model against Google's current ARCore supported-device catalog before testing.

## 3. USB installation

Enable Developer Options and USB debugging on the phone, connect it to the development computer, and verify:

```bash
adb devices
```

Install the APK:

```bash
adb install -r app-debug.apk
```

If an older debug build is installed, `-r` updates it while preserving app data.

## 4. Permissions

On first launch:

1. Grant camera permission.
2. Keep the rear camera unobstructed.
3. Allow ARCore/Google Play Services for AR to install or update if Android requests it.

Do not grant unrelated permissions.

## 5. Backend configuration

The app's backend endpoint is configurable from the existing scanner UI. For a phone connected to the same LAN as a development computer, use the computer's LAN IP address rather than an emulator-only address.

Example pattern:

```text
http://<development-machine-lan-ip>:8000
```

This is for local development only. Production deployments must use an HTTPS endpoint.

Before scanning, verify the backend health endpoint from the phone's network path. If the backend is unreachable, physical validation should remain blocked rather than silently failing during reconstruction.

## 6. Validation sequence

Open **Physical Device Validation** and confirm:

```text
CAMERA              AVAILABLE
ARCORE              AVAILABLE
SHARED_CAMERA       AVAILABLE
GYROSCOPE           AVAILABLE
ACCELEROMETER       AVAILABLE
CAMERA_INTRINSICS   AVAILABLE
NETWORK             AVAILABLE
```

Depth and magnetometer may be unavailable without blocking the validation path.

The app distinguishes capability detection from physical validation. `AVAILABLE` does not mean that ARCore tracking or metric accuracy has been experimentally verified.

## 7. First reference test

Use a rigid object with a precisely measured external dimension. Record the measurement method and units.

Recommended first procedure:

1. Enter the reference dimension.
2. Start the scan.
3. Capture diverse viewpoints.
4. Finish reconstruction.
5. Select the two reconstructed endpoints corresponding to the physical measurement.
6. Record reconstructed distance and calculated error.
7. Export the validation package.
8. Repeat the scan before drawing conclusions about repeatability.

Do not enter a reconstructed value manually as though it were measured by the system.

## 8. DC motor test

Only after the reference benchmark succeeds:

1. Record motor identity if known.
2. Measure at least one external dimension manually.
3. Scan the motor.
4. Inspect the actual point cloud.
5. Compare external dimensions.
6. Generate the Digital Twin.
7. Keep hidden winding, magnet, bearing, and inertia properties as unknown or estimated unless evidence exists.
8. Run the existing simulation.
9. Run the existing What-If experiment.
10. Export the complete validation package.

## 9. Evidence rule

Never report physical measurements from a synthetic fixture, emulator, or software-only test as hardware results.

The final report must distinguish:

- `SOFTWARE VERIFIED`
- `DEVICE CAPABILITY REPORTED`
- `DEVICE MEASURED`
- `PHYSICAL_REFERENCE_MEASURED`
- `RECONSTRUCTION_MEASURED`
- `SIMULATION_EXECUTED`
- `USER_REPORTED`
- `UNKNOWN`

## 10. Troubleshooting

### ARCore unavailable

Check the exact device model against Google's ARCore supported-device catalog and update Google Play Services for AR.

### Shared Camera unavailable

The physical validation path is blocked. Do not substitute an independent CameraX stream and call it Shared Camera validation.

### Camera intrinsics unavailable

The calibrated reconstruction validation path is blocked until a valid calibration source is available.

### Backend unreachable

Confirm the phone and backend host are on a reachable network, verify the configured LAN address/port, and check the backend health endpoint.

### Poor tracking

Stop the scan and record the tracking state/failure reason. Do not treat a poor-quality reconstruction as a successful measurement.
