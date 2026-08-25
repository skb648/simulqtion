import json

from backend.app.validation_replay import replay_validation


def test_replay_recomputes_reference_and_timestamp_metrics(tmp_path):
    payload = {
        "artifactSchemaVersion": "2.1",
        "scanId": "scan-test",
        "frames": [
            {"timestampDeltaNs": 5_000_000, "trackingState": "TRACKING"},
            {"timestampDeltaNs": 25_000_000, "trackingState": "PAUSED"},
        ],
        "referenceMeasurements": [
            {"referenceValue": 100.0, "reconstructedValue": 102.5}
        ],
        "reconstructionMetrics": {"reprojectionErrorPx": 0.8},
        "scaleMetrics": {"status": "VALIDATED"},
        "digitalTwin": {"id": "twin-test"},
    }
    path = tmp_path / "validation.json"
    path.write_text(json.dumps(payload), encoding="utf-8")
    result = replay_validation(path)
    assert result["frameCount"] == 2
    assert result["timestamp"]["within20msPct"] == 50.0
    assert result["trackingLossFrames"] == 1
    assert result["referenceMeasurements"][0]["absoluteError"] == 2.5
    assert result["referenceMeasurements"][0]["relativeErrorPct"] == 2.5
