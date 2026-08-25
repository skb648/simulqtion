import numpy as np
import cv2
from backend.app.artifact_store import ReconstructionArtifactStore
from backend.app.reconstruction import reconstruct_multiview
from backend.app.reconstruction_models import CameraCalibration, CameraPose, FrameMetadata, ReconstructionMetadata, ReferenceScale


def test_calibration_model_accepts_device_intrinsics():
    calibration = CameraCalibration(image_width=640, image_height=480, focal_length_x=500, focal_length_y=501, principal_point_x=320, principal_point_y=240, source='DEVICE')
    assert calibration.usable


def test_scale_remains_unknown_without_metric_reference_or_pose():
    image = np.zeros((480, 640), dtype=np.uint8)
    cv2.rectangle(image, (100, 100), (500, 380), 255, 4)
    ok, data = cv2.imencode('.jpg', image)
    assert ok
    metadata = ReconstructionMetadata(scan_id='scale-unknown')
    result, _ = reconstruct_multiview([data.tobytes()] * 3, metadata)
    assert result.scale_status == 'UNKNOWN'


def test_artifact_store_round_trip(tmp_path):
    store = ReconstructionArtifactStore(str(tmp_path))
    points = np.array([[0, 0, 0], [0.1, 0.2, 0.3]], dtype=np.float32)
    artifact_id = store.save({'scan_id': 's1', 'scale_status': 'UNKNOWN'}, points)
    assert store.load_metadata(artifact_id)['scan_id'] == 's1'
    assert np.allclose(store.load_points(artifact_id), points)


def test_arcore_pose_schema_is_metric_meters():
    pose = CameraPose(timestamp_ns=1, translation_m=[0.0, 0.0, 1.0], rotation_xyzw=[0, 0, 0, 1], source='ARCORE', confidence=0.9)
    frame = FrameMetadata(id='frame-0', timestamp_ns=1, width=640, height=480, quality=0.9, feature_points=100, pose=pose)
    metadata = ReconstructionMetadata(scan_id='pose', frames=[frame], reference_scale=ReferenceScale())
    assert metadata.frames[0].pose.translation_m[2] == 1.0
