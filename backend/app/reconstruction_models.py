from typing import Literal
from pydantic import BaseModel, Field

class CameraCalibration(BaseModel):
    image_width: int = Field(gt=0)
    image_height: int = Field(gt=0)
    focal_length_x: float | None = None
    focal_length_y: float | None = None
    principal_point_x: float | None = None
    principal_point_y: float | None = None
    distortion: list[float] = Field(default_factory=list)
    source: Literal['DEVICE', 'ARCORE', 'ESTIMATION', 'UNKNOWN'] = 'UNKNOWN'

    @property
    def usable(self) -> bool:
        return all(v is not None for v in (self.focal_length_x, self.focal_length_y, self.principal_point_x, self.principal_point_y))

class CameraPose(BaseModel):
    timestamp_ns: int = Field(ge=0)
    translation_m: list[float] = Field(min_length=3, max_length=3)
    rotation_xyzw: list[float] = Field(min_length=4, max_length=4)
    tracking_state: str = 'UNKNOWN'
    source: Literal['ARCORE', 'ESTIMATION', 'UNKNOWN'] = 'UNKNOWN'
    confidence: float = Field(ge=0.0, le=1.0)

class FrameMetadata(BaseModel):
    id: str
    timestamp_ns: int = Field(ge=0)
    width: int = Field(gt=0)
    height: int = Field(gt=0)
    quality: float = Field(ge=0.0, le=1.0)
    feature_points: int = Field(ge=0)
    calibration: CameraCalibration | None = None
    pose: CameraPose | None = None
    accelerometer: list[float] | None = None
    gyroscope: list[float] | None = None
    magnetometer: list[float] | None = None

class ReferenceScale(BaseModel):
    status: Literal['KNOWN', 'ESTIMATED', 'UNKNOWN'] = 'UNKNOWN'
    dimension_m: float | None = Field(default=None, gt=0)
    observed_extent_axis: Literal['x', 'y', 'z', 'largest'] | None = None
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    source: str = 'UNKNOWN'

class ReconstructionMetadata(BaseModel):
    scan_id: str
    coordinate_system: str = 'RIGHT_HANDED_CAMERA_OR_WORLD'
    frames: list[FrameMetadata] = Field(default_factory=list)
    reference_scale: ReferenceScale = Field(default_factory=ReferenceScale)

class ReconstructionMetrics(BaseModel):
    frame_count: int = 0
    valid_matches: int = 0
    reconstructed_points: int = 0
    reprojection_error_px: float | None = None
    tracking_confidence: float = 0.0
    scale_confidence: float = 0.0
    pose_source: str = 'UNKNOWN'

class ReconstructionResultV2(BaseModel):
    scan_id: str
    artifact_id: str | None = None
    representation: str
    coordinate_system: str
    image_count: int
    sparse_point_count: int
    dimensions_m: dict[str, float] = Field(default_factory=dict)
    dimensions_arbitrary_units: dict[str, float] = Field(default_factory=dict)
    scale_status: Literal['KNOWN', 'ESTIMATED', 'UNKNOWN']
    scale_confidence: float = Field(ge=0.0, le=1.0)
    metrics: ReconstructionMetrics
    warnings: list[str] = Field(default_factory=list)
