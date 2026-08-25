import cv2
import numpy as np
from .models import ScanAssessment, ScanRequest, ReconstructionResult
from .reconstruction_models import ReconstructionMetadata, ReconstructionResultV2, ReconstructionMetrics

SYNC_LIMIT_NS = 20_000_000


def assess_scan(req: ScanRequest) -> ScanAssessment:
    sectors = len({int(((v.yaw_deg % 360) / 45) % 8) for v in req.views})
    tracked = sum(1 for v in req.views if v.tracked)
    quality = sum(v.quality for v in req.views) / len(req.views)
    points = sum(v.feature_points for v in req.views) / len(req.views)
    coverage = sectors / 8.0
    geometry_quality = min(1.0, 0.55 * coverage + 0.3 * quality + 0.15 * min(points / 500.0, 1.0))
    if coverage < 0.625:
        return ScanAssessment(sufficient=False, reason='More views required. Move around the motor.', coverage=coverage, estimated_geometry_quality=geometry_quality)
    if quality < 0.45:
        return ScanAssessment(sufficient=False, reason='Lighting is insufficient. Improve illumination.', coverage=coverage, estimated_geometry_quality=geometry_quality)
    if tracked < max(2, len(req.views) // 3):
        return ScanAssessment(sufficient=False, reason='Object tracking is weak. Move more slowly and keep the motor visible.', coverage=coverage, estimated_geometry_quality=geometry_quality)
    return ScanAssessment(sufficient=True, reason='Pose-aware viewpoint diversity is sufficient for an approximate representation.', coverage=coverage, estimated_geometry_quality=geometry_quality)


def scale_error_pct(measured_m: float, reference_m: float) -> float:
    if reference_m <= 0:
        raise ValueError('reference_m must be positive')
    return abs(measured_m - reference_m) / reference_m * 100.0


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
    if not len(points):
        return 0.0
    homog = np.hstack((points, np.ones((len(points), 1))))
    projected = (P @ homog.T).T
    valid = np.abs(projected[:, 2]) > 1e-9
    if not valid.any():
        return float('inf')
    projected = projected[valid, :2] / projected[valid, 2:3]
    return float(np.mean(np.linalg.norm(projected - pixels[valid], axis=1)))


def _pose_coverage(frames) -> float:
    poses = [f.pose for f in frames if f.pose and f.pose.source == 'ARCORE' and f.pose.tracking_state == 'TRACKING']
    if len(poses) < 2:
        return 0.0
    forwards = []
    centers = []
    for p in poses:
        R = _quat_to_rotation(p.rotation_xyzw)
        forwards.append(-(R @ np.array([0.0, 0.0, 1.0])))
        centers.append(np.asarray(p.translation_m, dtype=np.float64))
    angular = []
    for i in range(len(forwards)):
        for j in range(i + 1, len(forwards)):
            dot = float(np.clip(np.dot(forwards[i], forwards[j]), -1.0, 1.0))
            angular.append(np.arccos(dot) / np.pi)
    center_array = np.vstack(centers)
    baseline = float(np.linalg.norm(np.ptp(center_array, axis=0)))
    baseline_score = min(1.0, baseline / 0.20)
    angular_score = float(np.mean(angular)) if angular else 0.0
    return float(np.clip(0.7 * angular_score + 0.3 * baseline_score, 0.0, 1.0))


def _sync_stats(frames):
    deltas = [f.timestamp_delta_ns for f in frames if f.timestamp_delta_ns is not None]
    good = [d for d in deltas if d <= SYNC_LIMIT_NS]
    return (float(np.mean(deltas)) / 1e6 if deltas else None, float(np.max(deltas)) / 1e6 if deltas else None, len(good))


def _base_result(metadata, image_count, warnings=None, metrics=None, representation='none'):
    return ReconstructionResultV2(scan_id=metadata.scan_id, artifact_schema_version=metadata.artifact_schema_version, representation=representation, coordinate_system=metadata.coordinate_system, pose_convention=metadata.pose_convention, units=metadata.units, image_count=image_count, sparse_point_count=0, scale_status='UNKNOWN', scale_confidence=0.0, metrics=metrics or ReconstructionMetrics(frame_count=image_count), warnings=warnings or [])


def reconstruct_images(images: list[bytes]) -> ReconstructionResult:
    result, _ = reconstruct_multiview(images, ReconstructionMetadata(scan_id='legacy'))
    confidence = 0.05 if result.representation == 'none' else min(0.95, result.metrics.tracking_confidence or 0.12)
    return ReconstructionResult(representation=result.representation, image_count=result.image_count, sparse_point_count=result.sparse_point_count, dimensions_arbitrary_units=result.dimensions_arbitrary_units, confidence=confidence, warnings=result.warnings)


def reconstruct_multiview(images: list[bytes], metadata: ReconstructionMetadata):
    if len(images) < 3:
        return _base_result(metadata, len(images), ['At least three distinct views are required.']), np.empty((0, 3))
    decoded = []
    orb = cv2.ORB_create(nfeatures=2000)
    for index, data in enumerate(images):
        frame = cv2.imdecode(np.frombuffer(data, dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
        if frame is None:
            continue
        kp, des = orb.detectAndCompute(frame, None)
        decoded.append((index, frame, kp, des))
    if len(decoded) < 3:
        return _base_result(metadata, len(decoded), ['Some scan images could not be decoded.']), np.empty((0, 3))

    h, w = decoded[0][1].shape[:2]
    calibration = next((f.calibration for f in metadata.frames if f.calibration and f.calibration.usable), None)
    warnings: list[str] = []
    if calibration:
        K = np.array([[calibration.focal_length_x, 0, calibration.principal_point_x], [0, calibration.focal_length_y, calibration.principal_point_y], [0, 0, 1]], dtype=np.float64)
    else:
        f = 0.8 * max(w, h)
        K = np.array([[f, 0, w / 2], [0, f, h / 2], [0, 0, 1]], dtype=np.float64)
        warnings.append('Camera intrinsics unavailable; focal length/principal point were estimated from image dimensions.')

    frame_by_index = {i: f for i, f in enumerate(metadata.frames)}
    sync_mean, sync_max, synchronized_count = _sync_stats(metadata.frames)
    pose_frames = [f for f in metadata.frames if f.pose and f.pose.source == 'ARCORE' and f.pose.tracking_state == 'TRACKING' and (f.timestamp_delta_ns is None or f.timestamp_delta_ns <= SYNC_LIMIT_NS)]
    pose_source = 'ARCORE' if pose_frames else 'UNKNOWN'
    tracking_conf = float(np.mean([p.pose.confidence for p in pose_frames])) if pose_frames else 0.0
    coverage = _pose_coverage(metadata.frames)
    tracking_failures = sum(1 for f in metadata.frames if f.pose and f.pose.tracking_state != 'TRACKING')
    accepted_frames = len(pose_frames) if pose_frames else len(decoded)
    rejected_frames = max(0, len(decoded) - accepted_frames)
    if pose_source == 'ARCORE' and synchronized_count < len(pose_frames):
        warnings.append('Some frames exceeded the synchronization threshold and were excluded from pose-aware reconstruction.')
    if pose_source == 'ARCORE' and coverage < 0.45:
        warnings.append('Viewpoint diversity is limited; additional rear/side/top views are recommended.')

    all_points: list[np.ndarray] = []
    reprojection_errors: list[float] = []
    valid_matches = 0
    matcher = cv2.BFMatcher(cv2.NORM_HAMMING)
    reference = decoded[0]
    ref_meta = frame_by_index.get(reference[0])
    for current in decoded[1:]:
        if pose_source == 'ARCORE':
            if not ref_meta or not ref_meta.pose or ref_meta.pose.source != 'ARCORE' or ref_meta.pose.tracking_state != 'TRACKING' or (ref_meta.timestamp_delta_ns is not None and ref_meta.timestamp_delta_ns > SYNC_LIMIT_NS):
                continue
            cur_meta = frame_by_index.get(current[0])
            if not cur_meta or not cur_meta.pose or cur_meta.pose.source != 'ARCORE' or cur_meta.pose.tracking_state != 'TRACKING' or (cur_meta.timestamp_delta_ns is not None and cur_meta.timestamp_delta_ns > SYNC_LIMIT_NS):
                continue
        _, _, kp1, des1 = reference
        _, _, kp2, des2 = current
        if des1 is None or des2 is None:
            continue
        good = [m for m, n in matcher.knnMatch(des1, des2, k=2) if m.distance < 0.72 * n.distance]
        if len(good) < 12:
            continue
        pts1 = np.float32([kp1[m.queryIdx].pt for m in good])
        pts2 = np.float32([kp2[m.trainIdx].pt for m in good])
        try:
            F, mask = cv2.findFundamentalMat(pts1, pts2, cv2.FM_RANSAC, 1.5, 0.995)
            if F is None or mask is None:
                continue
            inliers = mask.ravel().astype(bool)
            if inliers.sum() < 8:
                continue
            if pose_source == 'ARCORE':
                P0 = _projection_from_pose(K, ref_meta.pose)
                P1 = _projection_from_pose(K, cur_meta.pose)
            else:
                E = K.T @ F @ K
                _, R, t, pose_mask = cv2.recoverPose(E, pts1[inliers], pts2[inliers], K)
                if pose_mask is None:
                    continue
                P0 = K @ np.hstack((np.eye(3), np.zeros((3, 1))))
                P1 = K @ np.hstack((R, t))
            points4 = cv2.triangulatePoints(P0, P1, pts1[inliers].T, pts2[inliers].T)
            X = (points4[:3] / points4[3]).T
            finite = np.isfinite(X).all(axis=1) & (np.linalg.norm(X, axis=1) < 1000)
            if pose_source == 'ARCORE':
                R0 = _quat_to_rotation(ref_meta.pose.rotation_xyzw)
                C0 = np.asarray(ref_meta.pose.translation_m)
                R1 = _quat_to_rotation(cur_meta.pose.rotation_xyzw)
                C1 = np.asarray(cur_meta.pose.translation_m)
                z0 = (R0.T @ (X - C0).T)[2]
                z1 = (R1.T @ (X - C1).T)[2]
                finite &= (z0 > 0) & (z1 > 0)
            if not finite.any():
                continue
            Xv = X[finite]
            err0 = _reprojection_error(P0, Xv, pts1[inliers][finite])
            err1 = _reprojection_error(P1, Xv, pts2[inliers][finite])
            if max(err0, err1) > 5.0:
                continue
            all_points.append(Xv)
            reprojection_errors.extend([err0, err1])
            valid_matches += int(len(Xv))
        except (cv2.error, np.linalg.LinAlgError, ValueError):
            continue

    metrics = ReconstructionMetrics(frame_count=len(decoded), accepted_frame_count=accepted_frames, rejected_frame_count=rejected_frames, valid_matches=valid_matches, reconstructed_points=0, tracking_confidence=tracking_conf, pose_confidence=tracking_conf, pose_source=pose_source, timestamp_sync_mean_ms=sync_mean, timestamp_sync_max_ms=sync_max, synchronized_frame_count=synchronized_count, coverage_score=coverage, tracking_failures=tracking_failures, scale_validation_status='UNKNOWN')
    if not all_points:
        if pose_source == 'UNKNOWN':
            warnings.append('No validated ARCore trajectory was supplied; OpenCV estimated-pose fallback remains active.')
        warnings.append('Views did not contain enough stable feature matches for triangulation.')
        warnings.append('No reliable metric scale was established.')
        return _base_result(metadata, len(decoded), warnings, metrics, 'sparse_point_cloud_unavailable'), np.empty((0, 3))

    cloud = np.vstack(all_points)
    extent = np.ptp(cloud, axis=0)
    dimensions_arbitrary = {'x': float(extent[0]), 'y': float(extent[1]), 'z': float(extent[2])}
    dimensions_m: dict[str, float] = {}
    scale_status = 'UNKNOWN'
    scale_confidence = 0.0
    scale_error_pct = None
    if pose_source == 'ARCORE':
        dimensions_m = dimensions_arbitrary.copy()
        scale_status = 'KNOWN'
        scale_confidence = max(0.5, tracking_conf)
        warnings.append('Metric units are sourced from ARCore pose translation. Physical accuracy is UNVALIDATED until a known-size reference is measured on hardware.')
        metrics.scale_validation_status = 'UNVALIDATED'
        if metadata.reference_scale.dimension_m:
            scale_error_pct = scale_error_pct_fn = scale_error_pct_value = scale_error_pct = scale_error_pct if False else scale_error_pct
            measured = float(np.max(extent))
            scale_error_pct = scale_error_pct_fn = scale_error_pct_value = scale_error_pct
            scale_error_pct = abs(measured - metadata.reference_scale.dimension_m) / metadata.reference_scale.dimension_m * 100.0
            warnings.append('Reference comparison uses the largest reconstructed extent as the reference axis; independent object-axis correspondence is not established.')
    elif metadata.reference_scale.dimension_m:
        axis = metadata.reference_scale.observed_extent_axis or 'largest'
        values = np.array([extent[0], extent[1], extent[2]])
        axis_index = int(np.argmax(values)) if axis == 'largest' else {'x': 0, 'y': 1, 'z': 2}[axis]
        if values[axis_index] > 1e-9:
            factor = metadata.reference_scale.dimension_m / values[axis_index]
            dimensions_m = {k: v * factor for k, v in dimensions_arbitrary.items()}
            scale_status = 'ESTIMATED'
            scale_confidence = min(0.9, max(0.5, metadata.reference_scale.confidence))
            warnings.append('Metric scale is derived from a user-supplied reference dimension; it is not independently validated.')
            metrics.scale_validation_status = 'UNVALIDATED'
    else:
        warnings.append('Scale remains UNKNOWN. Capture validated ARCore poses or provide a metric reference.')

    reproj = float(np.mean(reprojection_errors)) if reprojection_errors else None
    metrics.reconstructed_points = len(cloud)
    metrics.reprojection_error_px = reproj
    metrics.scale_confidence = scale_confidence
    metrics.scale_error_pct = scale_error_pct
    if reproj is not None and reproj > 3.0:
        warnings.append(f'High reprojection error: {reproj:.2f} px.')
    result = ReconstructionResultV2(scan_id=metadata.scan_id, artifact_schema_version=metadata.artifact_schema_version, representation='sparse_multiview_point_cloud', coordinate_system=metadata.coordinate_system, pose_convention=metadata.pose_convention, units=metadata.units, image_count=len(decoded), sparse_point_count=len(cloud), dimensions_m=dimensions_m, dimensions_arbitrary_units=dimensions_arbitrary, scale_status=scale_status, scale_confidence=scale_confidence, metrics=metrics, warnings=warnings)
    return result, cloud
