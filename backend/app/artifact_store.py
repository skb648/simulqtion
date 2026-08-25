import json
import os
from pathlib import Path
from uuid import uuid4
import numpy as np

class ReconstructionArtifactStore:
    def __init__(self, root: str | None = None):
        self.root = Path(root or os.getenv('RC_ARTIFACT_DIR', '/tmp/reality-compiler-artifacts'))
        self.root.mkdir(parents=True, exist_ok=True)

    def save(self, metadata: dict, points: np.ndarray) -> str:
        artifact_id = f"recon-{uuid4()}"
        folder = self.root / artifact_id
        folder.mkdir(parents=True, exist_ok=False)
        (folder / 'metadata.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
        np.savez_compressed(folder / 'point_cloud.npz', points=points.astype(np.float32))
        return artifact_id

    def load_metadata(self, artifact_id: str) -> dict:
        folder = self.root / artifact_id
        if not folder.is_dir() or not (folder / 'metadata.json').is_file():
            raise FileNotFoundError(artifact_id)
        return json.loads((folder / 'metadata.json').read_text(encoding='utf-8'))

    def load_points(self, artifact_id: str) -> np.ndarray:
        folder = self.root / artifact_id
        if not folder.is_dir() or not (folder / 'point_cloud.npz').is_file():
            raise FileNotFoundError(artifact_id)
        with np.load(folder / 'point_cloud.npz') as data:
            return data['points']
