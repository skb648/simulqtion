from enum import Enum
from typing import Any
from pydantic import BaseModel, Field

class KnowledgeStatus(str, Enum):
    OBSERVED = 'OBSERVED'
    INFERRED = 'INFERRED'
    ESTIMATED = 'ESTIMATED'
    HYPOTHESIS = 'HYPOTHESIS'
    UNKNOWN = 'UNKNOWN'

class Evidence(BaseModel):
    source: str
    detail: str
    capture_ids: list[str] = Field(default_factory=list)

class PropertyValue(BaseModel):
    value: Any | None = None
    unit: str | None = None
    status: KnowledgeStatus
    confidence: float = Field(ge=0.0, le=1.0)
    evidence: list[Evidence] = Field(default_factory=list)

class Geometry(BaseModel):
    representation: str
    dimensions_m: dict[str, float] = Field(default_factory=dict)
    point_count: int = 0
    mesh_available: bool = False
    confidence: float = Field(ge=0.0, le=1.0)

class Component(BaseModel):
    id: str
    kind: str
    properties: dict[str, PropertyValue] = Field(default_factory=dict)

class DigitalTwin(BaseModel):
    schema_version: str = '1.0'
    id: str
    object_type: PropertyValue
    geometry: Geometry
    components: list[Component] = Field(default_factory=list)
    properties: dict[str, PropertyValue] = Field(default_factory=dict)
    observations: list[Evidence] = Field(default_factory=list)
    inferences: list[Evidence] = Field(default_factory=list)
    hypotheses: list[PropertyValue] = Field(default_factory=list)
    unknowns: list[str] = Field(default_factory=list)
    simulation_parameters: dict[str, PropertyValue] = Field(default_factory=dict)

class ScanView(BaseModel):
    id: str
    yaw_deg: float
    pitch_deg: float = 0.0
    quality: float = Field(ge=0.0, le=1.0)
    tracked: bool = False
    feature_points: int = 0

class ScanRequest(BaseModel):
    views: list[ScanView] = Field(min_length=1)

class ScanAssessment(BaseModel):
    sufficient: bool
    reason: str
    coverage: float
    estimated_geometry_quality: float

class MotorSimulationRequest(BaseModel):
    voltage_v: float
    duration_s: float = Field(default=2.0, gt=0)
    dt_s: float = Field(default=0.001, gt=0)
    resistance_ohm: float = Field(default=2.0, gt=0)
    inductance_h: float = Field(default=0.015, gt=0)
    torque_constant: float = Field(default=0.03, gt=0)
    back_emf_constant: float = Field(default=0.03, gt=0)
    inertia_kg_m2: float = Field(default=1.2e-4, gt=0)
    friction_nms: float = Field(default=2e-5, ge=0)
    load_torque_nm: float = Field(default=0.0, ge=0)

class SimulationPoint(BaseModel):
    time_s: float
    current_a: float
    angular_velocity_rad_s: float
    speed_rpm: float
    back_emf_v: float
    torque_nm: float
    electrical_power_w: float
    mechanical_power_w: float

class SimulationResult(BaseModel):
    model: str
    duration_s: float
    points: list[SimulationPoint]
    final: SimulationPoint
    warnings: list[str] = Field(default_factory=list)

class ExperimentRequest(BaseModel):
    baseline: MotorSimulationRequest
    experiment: MotorSimulationRequest

class ReconstructionResult(BaseModel):
    representation: str
    image_count: int
    sparse_point_count: int
    dimensions_arbitrary_units: dict[str, float]
    confidence: float
    warnings: list[str] = Field(default_factory=list)
