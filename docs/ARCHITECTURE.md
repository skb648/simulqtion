# Reality Compiler architecture — DC motor vertical slice

## Flow
Android camera → multi-view JPEG cache → FastAPI multipart upload → sparse reconstruction → uncertainty-aware digital twin → DC motor ODE simulation → What-If branch comparison → Android numeric visualization.

## Android / cloud boundary
Local: CameraX capture, permissions, cache, scan UX, basic result rendering.
Cloud: feature extraction / multi-view reconstruction, digital-twin generation, physics simulation, experiment comparison.

## Reconstruction limitation
The reconstruction is sparse and uncalibrated. ORB correspondences, RANSAC geometry, and triangulation create a relative point cloud when enough matches exist. Metric dimensions are not claimed.

## Failure behavior
Insufficient views, low image quality, weak matches, malformed uploads, and HTTP failures are surfaced explicitly. No canned simulation result is used.
