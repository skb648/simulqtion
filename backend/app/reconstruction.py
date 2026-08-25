import cv2
import numpy as np
from .models import ScanAssessment, ScanRequest, ReconstructionResult
from .reconstruction_models import ReconstructionMetadata, ReconstructionResultV2, ReconstructionMetrics


def assess_scan(req: ScanRequest) -> ScanAssessment:
    views = req.views
    sectors = len({int(((v.yaw_deg % 360) / 45) % 8) for v in views})
    tracked = sum(1 for v in views if v.tracked)
    quality = sum(v.quality for v in views) / len(views)
    points = sum(v.feature_points for v in views) / len(views)
    coverage = sectors / 8.0
    geometry_quality = min(1.0, 0.55 * coverage + 0.3 * quality + 0.15 * min(points / 500.0, 1.0))
    if coverage < 0.625:
        return ScanAssessment(sufficient=False, reason='More views required. Move around the motor.', coverage=coverage, estimated_geometry_quality=geometry_quality)
    if quality < 0.45:
        return ScanAssessment(sufficient=False, reason='Lighting is insufficient. Improve illumination.', coverage=coverage, estimated_geometry_quality=geometry_quality)
    if tracked < max(2, len(views) // 3):
        return ScanAssessment(sufficient=False, reason='Object tracking is weak. Move more slowly and keep the motor visible.', coverage=coverage, estimated_geometry_quality=geometry_quality)
    return ScanAssessment(sufficient=True, reason='Scan coverage is sufficient for an approximate representation.', coverage=coverage, estimated_geometry_quality=geometry_quality)


def _quat_to_rotation(q: list[float]) -> np.ndarray:
    x, y, z, w = q
    n = x*x + y*y + z*z + w*w
    if n <= 1e-12:
        return np.eye(3)
    s = 2.0 / n
    return np.array([[1-s*(y*y+z*z), s*(x*y-z*w), s*(x*z+y*w)], [s*(x*y+z*w), 1-s*(x*x+z*z), s*(y*z-x*w)], [s*(x*z-y*w), s*(y*z+x*w), 1-s*(x*x+y*y)]], dtype=np.float64)


def _projection_from_pose(K: np.ndarray, pose) -> np.ndarray:
    R_cw = _quat_to_rotation(pose.rotation_xyzw)
    C = np.asarray(pose.translation_m, dtype=np.float64).reshape(3)
    R_wc = R_cw.T
    t_wc = -R_wc @ C
    return K @ np.hstack((R_wc, t_wc.reshape(3, 1)))


def _reprojection_error(P: np.ndarray, points: np.ndarray, pixels: np.ndarray) -> float:
    homog = np.hstack((points, np.ones((len(points), 1))))
    projected = (P @ homog.T).T
    projected = projected[:, :2] / projected[:, 2:3]
    return float(np.mean(np.linalg.norm(projected - pixels, axis=1))) if len(points) else 0.0


def reconstruct_images(images: list[bytes]) -> ReconstructionResult:
    result, _ = reconstruct_multiview(images, ReconstructionMetadata(scan_id='legacy'))
    confidence = 0.05 if result.representation == 'none' else min(0.95, result.metrics.tracking_confidence or 0.12)
    return ReconstructionResult(representation=result.representation, image_count=result.image_count, sparse_point_count=result.sparse_point_count, dimensions_arbitrary_units=result.dimensions_arbitrary_units, confidence=confidence, warnings=result.warnings)


def reconstruct_multiview(images: list[bytes], metadata: ReconstructionMetadata):
    if len(images) < 3:
        return ReconstructionResultV2(scan_id=metadata.scan_id, representation='none', coordinate_system=metadata.coordinate_system, image_count=len(images), sparse_point_count=0, scale_status='UNKNOWN', scale_confidence=0.0, metrics=ReconstructionMetrics(frame_count=len(images)), warnings=['At least three distinct views are required.']), np.empty((0, 3))
    decoded = []
    orb = cv2.ORB_create(nfeatures=2000)
    for index, data in enumerate(images):
        frame = cv2.imdecode(np.frombuffer(data, dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
        if frame is None:
            continue
        kp, des = orb.detectAndCompute(frame, None)
        decoded.append((index, frame, kp, des))
    if len(decoded) < 3:
        return ReconstructionResultV2(scan_id=metadata.scan_id, representation='none', coordinate_system=metadata.coordinate_system, image_count=len(decoded), sparse_point_count=0, scale_status='UNKNOWN', scale_confidence=0.0, metrics=ReconstructionMetrics(frame_count=len(decoded)), warnings=['Some scan images could not be decoded.']), np.empty((0, 3))

    h, w = decoded[0][1].shape[:2]
    calibration = next((f.calibration for f in metadata.frames if f.calibration and f.calibration.usable), None)
    warnings: list[str] = []
    if calibration:
        K = np.array([[calibration.focal_length_x, 0, calibration.principal_point_x], [0, calibration.focal_length_y, calibration.principal_point_y], [0, 0, 1]], dtype=np.float64)
    else:
        f = 0.8 * max(w, h)
        K = np.array([[f, 0, w / 2], [0, f, h / 2], [0, 0, 1]], dtype=np.float64)
        warnings.append('Camera intrinsics unavailable; focal length/principal point were estimated from image dimensions.')

    poses = [f.pose for f in metadata.frames if f.pose is not None]
    pose_source = poses[0].source if poses else 'UNKNOWN'
    tracking_conf = float(np.mean([p.confidence for p in poses])) if poses else 0.0
    all_points: list[np.ndarray] = []
    reprojection_errors: list[float] = []
    valid_matches = 0
    matcher = cv2.BFMatcher(cv2.NORM_HAMMING)

    for idx in range(1, len(decoded)):
        i0, _, kp1, des1 = decoded[0]
        i1, _, kp2, des2 = decoded[idx]
        if des1 is None or des2 is None:
            continue
        knn = matcher.knnMatch(des1, des2, k=2)
        good = [m for m, n in knn if m.distance < 0.72 * n.distance]
        if len(good) < 12:
            continue
        pts1 = np.float32([kp1[m.queryIdx].pt for m in good])
        pts2 = np.float32([kp2[m.trainIdx].pt for m in good])
        try:
            F, mask = cv2.findFundamentalMat(pts1, pts2, cv2.FM_RANSAC, 1.5, 0.995)
        except cv2.error:
            continue
        if F is None or mask is None:
            continue
        inliers = mask.ravel().astype(bool)
        if inliers.sum() < 8:
            continue
        p0 = next((f for f in metadata.frames if f.id in {str(i0), f'frame-{i0}'}), None)
        p1m = next((f for f in metadata.frames if f.id in {str(i1), f'frame-{i1}'}), None)
        try:
            if p0 and p1m and p0.pose and p1m.pose and p0.pose.source == 'ARCORE' and p1m.pose.source == 'ARCORE':
                P0 = _projection_from_pose(K, p0.pose)
                P1 = _projection_from_pose(K, p1m.pose)
                points4 = cv2.triangulatePoints(P0, P1, pts1[inliers].T, pts2[inliers].T)
                X = (points4[:3] / points4[3]).T
                valid = np.isfinite(X).all(axis=1)
                if valid.any():
                    Xv = X[valid]
                    all_points.append(Xv)
                    reprojection_errors += [_reprojection_error(P0, Xv, pts1[inliers][valid]), _reprojection_error(P1, Xv, pts2[inliers][valid])]
                    valid_matches += int(valid.sum())
                continue
            E = K.T @ F @ K
            _, R, t, pose_mask = cv2.recoverPose(E, pts1[inliers], pts2[inliers], K)
            if pose_mask is None:
                continue
            P0 = K @ np.hstack((np.eye(3), np.zeros((3, 1))))
            P1 = K @ np.hstack((R, t))
            points4 = cv2.triangulatePoints(P0, P1, pts1[inliers].T, pts2[inliers].T)
            X = (points4[:3] / points4[3]).T
            valid = np.isfinite(X).all(axis=1) & (np.linalg.norm(X, axis=1) < 1000)
            if valid.any():
                Xv = X[valid]
                all_points.append(Xv)
                reprojection_errors += [_reprojection_error(P0, Xv, pts1[inliers][valid]), _reprojection_error(P1, Xv, pts2[inliers][valid])]
                valid_matches += int(valid.sum())
        except (cv2.error, np.linalg.LinAlgError, ValueError):
            continue

    if not all_points:
        result = ReconstructionResultV2(scan_id=metadata.scan_id, representation='sparse_point_cloud_unavailable', coordinate_system=metadata.coordinate_system, image_count=len(decoded), sparse_point_count=0, scale_status='UNKNOWN', scale_confidence=0.0, metrics=ReconstructionMetrics(frame_count=len(decoded), tracking_confidence=tracking_conf, pose_source=pose_source), warnings=warnings + ['Views did not contain enough stable feature matches for triangulation.', 'No reliable metric scale was established.'])
        return result, np.empty((0, 3))

    cloud = np.vstack(all_points)
    extent = np.ptp(cloud, axis=0)
    dimensions_arbitrary = {'x': float(extent[0]), 'y': float(extent[1]), 'z': float(extent[2])}
    dimensions_m: dict[str, float] = {}
    scale_status = 'UNKNOWN'
    scale_confidence = 0.0
    arcore_metric = all(p.pose and p.pose.source == 'ARCORE' for p in metadata.frames if p.pose is not None) and len(poses) >= 2
    if arcore_metric:
        dimensions_m = dimensions_arbitrary.copy()
        scale_status = 'KNOWN'
        scale_confidence = max(0.5, tracking_conf)
    elif metadata.reference_scale.dimension_m:
        axis = metadata.reference_scale.observed_extent_axis or 'largest'
        values = np.array([extent[0], extent[1], extent[2]])
        axis_index = int(np.argmax(values)) if axis == 'largest' else {'x': 0, 'y': 1, 'z': 2}[axis]
        if values[axis_index] > 1e-9:
            factor = metadata.reference_scale.dimension_m / values[axis_index]
            dimensions_m = {k: v * factor for k, v in dimensions_arbitrary.items()}
            scale_status = 'ESTIMATED'
            scale_confidence = min(0.9, max(0.5, metadata.reference_scale.confidence))
            warnings.append('Metric scale is derived from a user-supplied reference dimension; axis correspondence was not independently measured.')
    else:
        warnings.append('Scale remains UNKNOWN. Provide a metric reference or capture validated ARCore poses.')
    reproj = float(np.mean(reprojection_errors)) if reprojection_errors else None
    metrics = ReconstructionMetrics(frame_count=len(decoded), valid_matches=valid_matches, reconstructed_points=len(cloud), reprojection_error_px=reproj, tracking_confidence=tracking_conf, scale_confidence=scale_confidence, pose_source=pose_source)
    if reproj is not None and reproj > 3.0:
        warnings.append(f'High reprojection error: {reproj:.2f} px.')
    result = ReconstructionResultV2(scan_id=metadata.scan_id, representation='sparse_multiview_point_cloud', coordinate_system=metadata.coordinate_system, image_count=len(decoded), sparse_point_count=len(cloud), dimensions_m=dimensions_m, dimensions_arbitrary_units=dimensions_arbitrary, scale_status=scale_status, scale_confidence=scale_confidence, metrics=metrics, warnings=warnings)
    return result, cloud
