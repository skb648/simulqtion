import cv2
import numpy as np
from .models import ScanAssessment, ScanRequest, ReconstructionResult

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

def reconstruct_images(images: list[bytes]) -> ReconstructionResult:
    if len(images) < 3:
        return ReconstructionResult(representation='none', image_count=len(images), sparse_point_count=0, dimensions_arbitrary_units={}, confidence=0.0, warnings=['At least three distinct views are required.'])
    decoded = []
    orb = cv2.ORB_create(nfeatures=1500)
    for data in images:
        frame = cv2.imdecode(np.frombuffer(data, dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
        if frame is None:
            continue
        kp, des = orb.detectAndCompute(frame, None)
        decoded.append((frame, kp, des))
    if len(decoded) < 3:
        return ReconstructionResult(representation='none', image_count=len(images), sparse_point_count=0, dimensions_arbitrary_units={}, confidence=0.05, warnings=['Some scan images could not be decoded.'])
    h, w = decoded[0][0].shape[:2]
    f = 0.8 * max(w, h)
    K = np.array([[f, 0, w / 2], [0, f, h / 2], [0, 0, 1]], dtype=np.float64)
    matcher = cv2.BFMatcher(cv2.NORM_HAMMING)
    all_points = []
    inlier_pairs = 0
    for idx in range(1, len(decoded)):
        _, kp1, des1 = decoded[0]
        _, kp2, des2 = decoded[idx]
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
        try:
            E = K.T @ F @ K
            _, R, t, pose_mask = cv2.recoverPose(E, pts1[inliers], pts2[inliers], K)
            if pose_mask is None:
                continue
            p1 = np.hstack((np.eye(3), np.zeros((3, 1))))
            p2 = np.hstack((R, t))
            matched1 = pts1[inliers]
            matched2 = pts2[inliers]
            ones = np.ones((matched1.shape[0], 1), dtype=np.float32)
            tri1 = np.linalg.inv(K) @ np.hstack((matched1, ones)).T
            tri2 = np.linalg.inv(K) @ np.hstack((matched2, ones)).T
            X = cv2.triangulatePoints(p1, p2, tri1[:2], tri2[:2])
        except (cv2.error, np.linalg.LinAlgError, ValueError):
            continue
        X = (X[:3] / X[3]).T
        valid = np.isfinite(X).all(axis=1) & (np.linalg.norm(X, axis=1) < 1000)
        if valid.any():
            all_points.append(X[valid])
            inlier_pairs += int(valid.sum())
    if not all_points:
        return ReconstructionResult(representation='sparse_point_cloud_unavailable', image_count=len(decoded), sparse_point_count=0, dimensions_arbitrary_units={}, confidence=0.12, warnings=['Views did not contain enough stable feature matches for triangulation.', 'Scale is uncalibrated; dimensions are not physical measurements.'])
    cloud = np.vstack(all_points)
    extent = np.ptp(cloud, axis=0)
    confidence = min(0.9, 0.25 + 0.65 * min(inlier_pairs / 1000.0, 1.0))
    return ReconstructionResult(representation='sparse_multiview_point_cloud', image_count=len(decoded), sparse_point_count=int(cloud.shape[0]), dimensions_arbitrary_units={'x': float(extent[0]), 'y': float(extent[1]), 'z': float(extent[2])}, confidence=float(confidence), warnings=['Reconstruction scale is uncalibrated; values are relative units, not physical measurements.'])
