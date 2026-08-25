# Reality Compiler Phase 2 — Sensor-aware reconstruction

## Current architecture

`Android CameraX -> frame timestamp + Camera2 intrinsics + IMU metadata -> multipart metadata -> FastAPI -> calibrated OpenCV reconstruction -> persisted NPZ/JSON artifact -> Digital Twin geometry`

ARCore is an optional capability. The app checks ARCore and Depth availability without making them mandatory. The current CameraX scanner does **not** yet own an ARCore camera session, so ARCore camera poses are represented in the backend schema but are not claimed as captured by the current Android path.

## Calibration

Camera2 `LENS_INTRINSIC_CALIBRATION` is used when exposed by the selected CameraX camera. Distortion coefficients are retained when available. If intrinsics are absent, the backend falls back to an explicitly estimated pinhole matrix and labels the result accordingly.

## Sensors

Accelerometer, gyroscope, magnetometer, and rotation-vector samples are recorded using Android sensor timestamps. At image capture, the newest sample within 100 ms is attached. No interpolation is performed; missing/late samples remain absent.

## Coordinate system

The backend reconstruction schema uses a right-handed coordinate-system label. When ARCore poses are supplied, their translation is interpreted in meters and their quaternion as ARCore's object-to-world pose. The current Android CameraX path does not yet produce those poses.

## Scale

Scale is never silently assumed.

- `KNOWN`: validated metric camera poses are supplied.
- `ESTIMATED`: a user supplies a known dimension and the backend maps it to the selected point-cloud extent axis.
- `UNKNOWN`: neither source is available.

User-reference scaling is explicitly warned as estimated because correspondence between the measured physical dimension and the reconstructed extent is not independently established.

## Persistence

Each successful reconstruction is stored as:

- `metadata.json` — JSON metadata, calibration, metrics, scale and provenance.
- `point_cloud.npz` — compressed binary point cloud.

The artifact API returns metadata separately and serves the point cloud as binary, avoiding large JSON payloads.

## Metrics

The reconstruction records frame count, valid triangulated points, reprojection error in pixels, tracking confidence when pose metadata exists, scale confidence, and pose source. A reprojection error above 3 px is explicitly warned; this is a quality signal, not an accuracy guarantee.

## Physical-device procedure

1. Build with a reachable backend URL, e.g. `-PRC_BACKEND_URL=http://<LAN-IP>:8000` for development on a trusted local network.
2. Install the APK on an ARCore-capable Android phone when available.
3. Grant camera permission.
4. Verify the capability panel reports ARCore/sensors accurately.
5. Enter a measured motor dimension if available.
6. Capture at least three distinct views; eight diverse views are recommended.
7. Keep the motor visible, avoid blur and poor lighting, and move around it rather than only rotating the phone in place.
8. Save the endpoint and reconstruct.
9. Verify returned reconstruction metrics and scale status.
10. Inspect the Digital Twin provenance before simulation.
11. Run baseline simulation and What-If experiment.
12. Repeat once with backend unavailable to verify local cache/error behavior.

## Known Phase 2 limitation

A true ARCore-pose-driven CameraX/ARCore shared-camera session has not been enabled yet. Doing this requires replacing the current independent CameraX preview/capture path with an ARCore-owned camera/rendering pipeline or a validated shared-camera integration. Until that is implemented and tested on a physical device, ARCore pose availability is capability information only, not evidence that a captured scan used ARCore poses.
