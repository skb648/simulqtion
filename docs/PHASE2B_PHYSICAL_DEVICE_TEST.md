# Reality Compiler — Phase 2B physical-device validation

## Purpose

Validate the ARCore Shared Camera path on real Android hardware without treating software compilation as evidence of ARCore operation or metric accuracy.

## Architecture under test

```text
Camera2 sensor
   ├── ARCore Shared Camera
   │      └── Frame.timestamp + Camera.pose
   └── app ImageReader
          └── image.timestamp + JPEG

image.timestamp ↔ ARCore Frame.timestamp
              ↓
      synchronized frame metadata
              ↓
       calibrated pose-aware
          reconstruction
```

CameraX remains the fallback when ARCore is unavailable. It is not described as metric-pose reconstruction.

## Coordinate convention

- ARCore world: right-handed.
- World X: right.
- World Y: up.
- Camera forward: negative Z in the camera coordinate convention.
- Stored pose convention: `CAMERA_TO_WORLD`.
- Translation units: meters.
- Quaternion order: `x, y, z, w`.
- Pose matrix: 4x4 ARCore `Pose.toMatrix()` representation.

## Synchronization rule

A reconstruction frame is accepted only when:

- an ARCore pose exists;
- ARCore tracking state is `TRACKING`; and
- absolute image/ARCore sensor timestamp delta is <= 20 ms.

The exact delta is persisted in `timestamp_delta_ns`.

## Device procedure

1. Install the debug APK on a supported ARCore phone.
2. Grant camera permission.
3. Start the scanner.
4. Confirm `ARCore supported` and `Shared Camera` status.
5. Wait until the UI reports ARCore tracking active.
6. Verify diagnostic timestamps and tracking state.
7. Place a rigid reference object with a measured dimension, e.g. 100.0 mm.
8. Capture at least 8 diverse viewpoints, including front, rear, left/right and elevated views.
9. Avoid blur and rapid motion.
10. Reconstruct through the configured backend.
11. Record frame count, accepted/rejected frames, synchronization mean/max, point count, reprojection error and coverage.
12. Measure the same reference dimension in the reconstructed cloud.
13. Calculate absolute error and relative error percentage.
14. Repeat with the DC motor.
15. Inspect the actual point cloud.
16. Generate the Digital Twin.
17. Run the existing DC motor simulation.
18. Run the existing What-If voltage experiment.

## Required evidence

Record:

- phone model;
- Android version;
- ARCore availability/update state;
- depth support;
- camera intrinsics availability;
- gyroscope/accelerometer availability;
- capture FPS if measured;
- synchronized-frame count;
- timestamp delta statistics;
- tracking failures;
- reconstructed point count;
- reprojection error;
- coverage score;
- known reference dimension;
- reconstructed reference dimension;
- scale error percentage;
- backend latency;
- upload success/failure;
- Digital Twin artifact ID.

## Scientific boundary

ARCore metric coordinates are treated as metric **units**, but physical reconstruction accuracy remains `UNVALIDATED` until a known-size reference has been measured on real hardware. A low reprojection error alone is not proof of dimensional accuracy.

No claim about hidden/internal motor geometry is permitted.

## Environment limitation

If no physical Android device is available, the result must explicitly state:

> Physical-device validation blocked by unavailable hardware.

The software build, unit tests and metadata validation may still be reported independently.
