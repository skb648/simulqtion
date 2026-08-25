import numpy as np
from backend.app.reconstruction import _pose_coverage, _projection_from_pose, _reprojection_error, _sync_stats
from backend.app.reconstruction_models import CameraPose, FrameMetadata


def pose(x, y=0.0, z=0.0, timestamp=1_000_000_000, delta=5_000_000):
    return CameraPose(
        timestamp_ns=timestamp,
        translation_m=[x, y, z],
        rotation_xyzw=[0.0, 0.0, 0.0, 1.0],
        tracking_state='TRACKING',
        source='ARCORE',
        confidence=0.95,
        timestamp_delta_ns=delta,
    )


def frame(i, p):
    return FrameMetadata(id=f'frame-{i}', timestamp_ns=p.timestamp_ns, image_timestamp_ns=p.timestamp_ns, arcore_timestamp_ns=p.timestamp_ns, timestamp_delta_ns=p.timestamp_delta_ns, width=640, height=480, quality=1.0, feature_points=100, pose=p)


def test_projection_from_identity_pose_is_metric_camera_matrix():
    K = np.array([[500.0, 0, 320.0], [0, 500.0, 240.0], [0, 0, 1]], dtype=float)
    P = _projection_from_pose(K, pose(1.0))
    X = np.array([[1.0, 0.0, 2.0, 1.0]]).T
    pixel = (P @ X).reshape(3)
    pixel = pixel[:2] / pixel[2]
    assert np.allclose(pixel, [320.0, 240.0])


def test_reprojection_error_zero_for_exact_projection():
    K = np.array([[500.0, 0, 320.0], [0, 500.0, 240.0], [0, 0, 1]], dtype=float)
    P = _projection_from_pose(K, pose(0.0))
    points = np.array([[0.0, 0.0, 2.0], [0.2, 0.1, 3.0]])
    homog = np.hstack((points, np.ones((len(points), 1))))
    projected = (P @ homog.T).T
    pixels = projected[:, :2] / projected[:, 2:3]
    assert _reprojection_error(P, points, pixels) < 1e-9


def test_timestamp_sync_statistics_flag_large_delta():
    frames = [frame(0, pose(0, delta=5_000_000)), frame(1, pose(0.1, timestamp=2_000_000_000, delta=40_000_000))]
    mean_ms, max_ms, good = _sync_stats(frames)
    assert mean_ms == 22.5
    assert max_ms == 40.0
    assert good == 1


def test_pose_coverage_increases_with_viewpoint_diversity():
    narrow = [frame(0, pose(0.0)), frame(1, pose(0.02))]
    diverse = [frame(0, pose(0.0)), frame(1, pose(0.2)), frame(2, pose(0.4))]
    assert 0.0 <= _pose_coverage(narrow) < 1.0
    assert _pose_coverage(diverse) >= _pose_coverage(narrow)


def test_tracking_loss_is_not_treated_as_valid_pose():
    lost = pose(0.1)
    lost.tracking_state = 'PAUSED'
    frames = [frame(0, pose(0.0)), frame(1, lost)]
    assert _pose_coverage(frames) == 0.0
