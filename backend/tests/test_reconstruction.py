import cv2
import numpy as np
from backend.app.reconstruction import reconstruct_images

def test_invalid_images_fail_explicitly():
    result = reconstruct_images([b'bad', b'bad', b'bad'])
    assert result.sparse_point_count == 0
    assert result.confidence <= 0.05

def test_reconstruction_output_has_no_fake_metric_scale():
    img = np.zeros((480, 640), dtype=np.uint8)
    cv2.circle(img, (200, 200), 60, 255, -1)
    ok, data = cv2.imencode('.jpg', img)
    assert ok
    result = reconstruct_images([data.tobytes()] * 3)
    assert 'x' in result.dimensions_arbitrary_units or result.sparse_point_count == 0
