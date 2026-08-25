# Phase 2B — ARCore Shared Camera architecture

## Selected design

The Android capture path uses the ARCore Shared Camera API and Camera2 for the AR-enabled path. CameraX remains available as a capability-safe fallback.

The AR-enabled path is:

```text
ARCore Session.Feature.SHARED_CAMERA
          +
Camera2 CameraDevice
          +
ARCore-owned camera surfaces
          +
CPU ImageReader (YUV_420_888)
          +
preview Surface
          ↓
common Camera2 sensor stream
          ↓
image.timestamp + ARCore Frame.timestamp + Camera.pose
          ↓
synchronized CapturedFrameMetadata
```

This avoids opening two independent camera pipelines and then guessing whether their frames correspond.

## Why CameraX is not used for the ARCore path

ARCore Shared Camera is a Camera2-level integration. The official ARCore architecture supplies ARCore-owned surfaces and wraps Camera2 device/capture-session callbacks. The existing CameraX path is retained only as the graceful fallback for devices where ARCore Shared Camera is unavailable.

## Timestamp policy

The image and ARCore pose use the camera sensor timestamp domain. A frame is accepted only when:

```text
abs(imageTimestampNs - arCoreTimestampNs) <= 20 ms
```

The exact delta is persisted and included in reconstruction quality metrics.

## Pose convention

- World frame: right-handed ARCore world.
- X: right.
- Y: up.
- Camera forward: negative Z.
- Pose convention stored by Reality Compiler: `CAMERA_TO_WORLD`.
- Translation: meters.
- Rotation: quaternion `x,y,z,w`.
- Full 4x4 pose matrix is persisted for reproducibility.

## Calibration

Camera2 `LENS_INTRINSIC_CALIBRATION` and `LENS_DISTORTION` are used when available. Calibration source is recorded as `DEVICE` or `ARCORE` for the AR-enabled path. Missing calibration does not cause invented values to be stored; the backend may use an explicitly warned estimation fallback.

## Metric reconstruction

When synchronized ARCore poses are present, OpenCV feature correspondences are used to reject bad matches, while triangulation uses the known camera poses rather than `recoverPose`.

The original essential-matrix / `recoverPose` path remains the fallback when validated ARCore poses are unavailable.

## Scale

ARCore pose translation is in meters, so the ARCore path produces metric-coordinate geometry. This is reported as metric units but marked `UNVALIDATED` until a known-size physical reference is measured. A supplied reference can produce a computed scale-error percentage; this is not treated as proof of physical accuracy.

## Coverage

Coverage is derived from pose-aware viewpoint diversity and camera translation baseline. It is intentionally not described as a complete surface-visibility solution: without object segmentation/depth, the system cannot prove that a specific hidden surface was observed.

## Hardware boundary

Build/CI success does not prove ARCore operation. Physical validation requires a real ARCore-capable phone and a measured reference object. If no such device is available, physical validation remains blocked.
