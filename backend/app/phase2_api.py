from uuid import uuid4
from fastapi import APIRouter, File, UploadFile, Form, HTTPException, Query
from fastapi.responses import FileResponse
from .models import DigitalTwin, Geometry, PropertyValue, KnowledgeStatus, Component, Evidence
from .reconstruction import reconstruct_multiview
from .reconstruction_models import ReconstructionMetadata
from .artifact_store import ReconstructionArtifactStore

router = APIRouter(prefix='/v1')
store = ReconstructionArtifactStore()

def _save_artifact(result, metadata, points):
    if not len(points):
        return None
    return store.save({'reconstruction': result.model_dump(), 'scan_metadata': metadata.model_dump()}, points)

@router.post('/reconstruction/multiview')
async def reconstruction(files: list[UploadFile] = File(...), metadata_json: str = Form('{}')):
    try:
        metadata = ReconstructionMetadata.model_validate_json(metadata_json)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f'Invalid reconstruction metadata: {exc}')
    images = [await f.read() for f in files]
    result, points = reconstruct_multiview(images, metadata)
    result.artifact_id = _save_artifact(result, metadata, points)
    return result

@router.get('/reconstructions/{artifact_id}')
def reconstruction_metadata(artifact_id: str):
    try:
        return store.load_metadata(artifact_id)
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail='Reconstruction artifact not found')

@router.get('/reconstructions/{artifact_id}/point-cloud')
def reconstruction_point_cloud(artifact_id: str):
    folder = store.root / artifact_id / 'point_cloud.npz'
    if not folder.is_file():
        raise HTTPException(status_code=404, detail='Reconstruction artifact not found')
    return FileResponse(folder, media_type='application/octet-stream', filename='point_cloud.npz')

@router.get('/reconstructions/{artifact_id}/point-cloud-preview')
def point_cloud_preview(artifact_id: str, max_points: int = Query(default=2500, ge=100, le=10000)):
    try:
        points = store.load_points(artifact_id)
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail='Reconstruction artifact not found')
    if len(points) > max_points:
        step = max(1, len(points) // max_points)
        points = points[::step][:max_points]
    return {'artifact_id': artifact_id, 'points': points.astype(float).tolist()}

@router.post('/pipeline/dc-motor')
async def dc_motor_pipeline(files: list[UploadFile] = File(...), metadata_json: str = Form('{}')):
    try:
        metadata = ReconstructionMetadata.model_validate_json(metadata_json)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f'Invalid reconstruction metadata: {exc}')
    images = [await f.read() for f in files]
    reconstruction, points = reconstruct_multiview(images, metadata)
    reconstruction.artifact_id = _save_artifact(reconstruction, metadata, points)
    q = reconstruction.metrics.tracking_confidence or 0.12
    evidence = Evidence(source='camera_scan', detail=f'{len(images)} uploaded views; calibrated reconstruction attempted', capture_ids=[f.id for f in metadata.frames])
    motor_type = PropertyValue(value='brushed_dc_motor', status=KnowledgeStatus.INFERRED, confidence=min(0.9, max(0.5, q)), evidence=[Evidence(source='perception', detail='external form + shaft-like feature; not internal inspection')])
    scale_status = KnowledgeStatus.ESTIMATED if reconstruction.scale_status == 'ESTIMATED' else (KnowledgeStatus.OBSERVED if reconstruction.scale_status == 'KNOWN' else KnowledgeStatus.UNKNOWN)
    geometry = Geometry(representation=reconstruction.representation, dimensions_m=reconstruction.dimensions_m, point_count=reconstruction.sparse_point_count, mesh_available=False, confidence=q, coordinate_system=reconstruction.coordinate_system, scale_status=reconstruction.scale_status, artifact_id=reconstruction.artifact_id, properties={
        'scale': PropertyValue(value=reconstruction.dimensions_m or None, unit='m' if reconstruction.dimensions_m else None, status=scale_status, confidence=reconstruction.scale_confidence, evidence=[evidence]),
        'reprojection_error': PropertyValue(value=reconstruction.metrics.reprojection_error_px, unit='px', status=KnowledgeStatus.OBSERVED if reconstruction.metrics.reprojection_error_px is not None else KnowledgeStatus.UNKNOWN, confidence=max(0.0, 1.0 - min((reconstruction.metrics.reprojection_error_px or 10.0) / 10.0, 1.0)), evidence=[evidence]),
    })
    twin = DigitalTwin(id=f'dt-{uuid4()}', object_type=motor_type, geometry=geometry,
        components=[Component(id='housing', kind='housing', properties={'visibility': PropertyValue(value='visible', status=KnowledgeStatus.OBSERVED, confidence=0.98, evidence=[evidence])}), Component(id='shaft', kind='shaft', properties={'visibility': PropertyValue(value='visible/feature-matched', status=KnowledgeStatus.OBSERVED, confidence=0.85, evidence=[evidence])})],
        properties={'motor_type': motor_type, 'physical_scale': geometry.properties['scale'], 'internal_windings': PropertyValue(value=None, status=KnowledgeStatus.UNKNOWN, confidence=0.0), 'magnet_strength': PropertyValue(value=None, unit='T', status=KnowledgeStatus.UNKNOWN, confidence=0.0)},
        observations=[evidence], inferences=[Evidence(source='perception', detail='motor category inferred from external visual evidence')], unknowns=['exact winding configuration', 'magnet strength', 'bearing friction', 'exact rotor inertia'],
        simulation_parameters={'resistance_ohm': PropertyValue(value=2.0, unit='ohm', status=KnowledgeStatus.ESTIMATED, confidence=0.35, uncertainty={'relative_fraction': 0.5}), 'torque_constant': PropertyValue(value=0.03, unit='N*m/A', status=KnowledgeStatus.ESTIMATED, confidence=0.25, uncertainty={'relative_fraction': 0.5}), 'back_emf_constant': PropertyValue(value=0.03, unit='V/(rad/s)', status=KnowledgeStatus.ESTIMATED, confidence=0.25, uncertainty={'relative_fraction': 0.5})},
        hypotheses=[PropertyValue(value='brushed_dc_motor', status=KnowledgeStatus.HYPOTHESIS, confidence=0.65, evidence=[Evidence(source='engineering_pattern', detail='hypothesis only; electrical test not performed')])])
    return {'reconstruction': reconstruction, 'digital_twin': twin}
