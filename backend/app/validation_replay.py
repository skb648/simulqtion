import json
from pathlib import Path
from statistics import mean, median, stdev


def replay_validation(path: str | Path) -> dict:
    """Replay calculations from a captured validation JSON package.

    This never synthesizes camera frames or physical measurements. It only
    recomputes deterministic statistics from values already captured.
    """
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    frames = data.get("frames", [])
    deltas_ms = [f["timestampDeltaNs"] / 1_000_000.0 for f in frames if f.get("timestampDeltaNs") is not None]
    tracking_losses = sum(1 for f in frames if str(f.get("trackingState", "")).upper() not in {"TRACKING", ""})
    result = {
        "scanId": data.get("scanId"),
        "artifactSchemaVersion": data.get("artifactSchemaVersion", data.get("schemaVersion")),
        "frameCount": len(frames),
        "timestamp": _stats(deltas_ms),
        "trackingLossFrames": tracking_losses,
        "reconstructionMetrics": data.get("reconstructionMetrics", {}),
        "scaleMetrics": data.get("scaleMetrics", {}),
        "digitalTwin": data.get("digitalTwin", {}),
    }
    refs = data.get("referenceMeasurements", [])
    replayed_refs = []
    for ref in refs:
        reference = float(ref["referenceValue"])
        measured = ref.get("reconstructedValue")
        if measured is None:
            replayed_refs.append({"status": "NOT_MEASURED", "referenceValue": reference})
            continue
        measured = float(measured)
        absolute = abs(measured - reference)
        replayed_refs.append({
            "status": "MEASURED",
            "referenceValue": reference,
            "reconstructedValue": measured,
            "absoluteError": absolute,
            "relativeErrorPct": absolute / reference * 100.0,
            "scaleFactor": measured / reference,
        })
    result["referenceMeasurements"] = replayed_refs
    return result


def _stats(values: list[float]) -> dict:
    if not values:
        return {"count": 0, "minimumMs": None, "maximumMs": None, "meanMs": None, "medianMs": None, "standardDeviationMs": None, "within20msPct": None, "over20msPct": None}
    return {
        "count": len(values),
        "minimumMs": min(values),
        "maximumMs": max(values),
        "meanMs": mean(values),
        "medianMs": median(values),
        "standardDeviationMs": stdev(values) if len(values) > 1 else 0.0,
        "within20msPct": sum(v <= 20.0 for v in values) / len(values) * 100.0,
        "over20msPct": sum(v > 20.0 for v in values) / len(values) * 100.0,
    }
