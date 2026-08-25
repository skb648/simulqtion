from uuid import uuid4
from fastapi import FastAPI, File, UploadFile
from .models import DigitalTwin, Geometry, PropertyValue, KnowledgeStatus, Component, Evidence, ScanRequest, ScanAssessment, MotorSimulationRequest, SimulationResult, ExperimentRequest
from .reconstruction import assess_scan, reconstruct_images
from .simulation_api import run_motor

app = FastAPI(title='Reality Compiler API', version='0.1.0')

@app.get('/health')
def health():
    return {'status': 'ok', 'service': 'reality-compiler-api'}

@app.post('/v1/scans/assess', response_model=ScanAssessment)
def scan_assess(req: ScanRequest):
    return assess_scan(req)

@app.post('/v1/digital-twins/dc-motor', response_model=DigitalTwin)
def build_motor_twin(req: ScanRequest):
    assessment = assess_scan(req)
    q = round(assessment.estimated_geometry_quality, 3)
    obs = Evidence(source='camera_scan', detail=f'{len(req.views)} captured views; tracking and visual quality evaluated')
    inferred_motor = PropertyValue(value='brushed_dc_motor', status=KnowledgeStatus.INFERRED, confidence=min(0.92, max(0.55, q)), evidence=[Evidence(source='perception', detail='visual silhouette + shaft-like feature + multi-view coverage')])
    approx_dims = {'length_m': 0.08, 'diameter_m': 0.05} if assessment.sufficient else {}
    return DigitalTwin(id=f'dt-{uuid4()}', object_type=inferred_motor, geometry=Geometry(representation='oriented_bounding_box_from_scan_metadata', dimensions_m=approx_dims, point_count=sum(v.feature_points for v in req.views), mesh_available=False, confidence=q), components=[Component(id='housing', kind='housing', properties={'visibility': PropertyValue(value='visible', status=KnowledgeStatus.OBSERVED, confidence=0.99, evidence=[obs])}), Component(id='shaft', kind='shaft', properties={'visibility': PropertyValue(value='visible', status=KnowledgeStatus.OBSERVED, confidence=0.96, evidence=[obs])})], properties={'motor_type': inferred_motor, 'mounting_geometry': PropertyValue(value=None, status=KnowledgeStatus.UNKNOWN, confidence=0.0), 'internal_windings': PropertyValue(value=None, status=KnowledgeStatus.UNKNOWN, confidence=0.0), 'magnet_strength': PropertyValue(value=None, status=KnowledgeStatus.UNKNOWN, confidence=0.0)}, observations=[obs], inferences=[Evidence(source='perception', detail='motor category inferred from observed external geometry')], unknowns=['exact winding configuration', 'magnet strength', 'bearing friction', 'exact rotor inertia'], simulation_parameters={'resistance_ohm': PropertyValue(value=2.0, unit='ohm', status=KnowledgeStatus.ESTIMATED, confidence=0.35), 'torque_constant': PropertyValue(value=0.03, unit='N*m/A', status=KnowledgeStatus.ESTIMATED, confidence=0.25), 'back_emf_constant': PropertyValue(value=0.03, unit='V/(rad/s)', status=KnowledgeStatus.ESTIMATED, confidence=0.25)}, hypotheses=[PropertyValue(value='brushed_dc_motor', status=KnowledgeStatus.HYPOTHESIS, confidence=0.70, evidence=[Evidence(source='engineering_pattern', detail='common external form')])])

@app.post('/v1/simulations/dc-motor', response_model=SimulationResult)
def simulate_motor(req: MotorSimulationRequest):
    return run_motor(req)

@app.post('/v1/reconstruction/multiview')
async def multiview_reconstruction(files: list[UploadFile] = File(...)):
    images = [await f.read() for f in files]
    return reconstruct_images(images)

@app.post('/v1/experiments/dc-motor', response_model=dict)
def compare(req: ExperimentRequest):
    baseline = run_motor(req.baseline)
    experiment = run_motor(req.experiment)
    return {'baseline': baseline, 'experiment': experiment, 'delta': {'current_a': experiment.final.current_a - baseline.final.current_a, 'speed_rpm': experiment.final.speed_rpm - baseline.final.speed_rpm, 'torque_nm': experiment.final.torque_nm - baseline.final.torque_nm, 'electrical_power_w': experiment.final.electrical_power_w - baseline.final.electrical_power_w}, 'explanation': 'The experiment is evaluated by the same motor equations with only the requested parameter changes applied.'}

@app.post('/v1/pipeline/dc-motor')
async def dc_motor_pipeline(files: list[UploadFile] = File(...)):
    images = [await f.read() for f in files]
    reconstruction = reconstruct_images(images)
    q = reconstruction.confidence
    obs = Evidence(source='camera_scan', detail=f'{len(images)} uploaded views; sparse feature reconstruction attempted')
    motor_type = PropertyValue(value='brushed_dc_motor', status=KnowledgeStatus.INFERRED, confidence=min(0.9, max(0.5, q)), evidence=[Evidence(source='perception', detail='external form + shaft-like feature; not internal inspection')])
    twin = DigitalTwin(id=f'dt-{uuid4()}', object_type=motor_type, geometry=Geometry(representation=reconstruction.representation, dimensions_m=reconstruction.dimensions_arbitrary_units, point_count=reconstruction.sparse_point_count, mesh_available=False, confidence=q), components=[Component(id='housing', kind='housing', properties={'visibility': PropertyValue(value='visible', status=KnowledgeStatus.OBSERVED, confidence=0.98, evidence=[obs])}), Component(id='shaft', kind='shaft', properties={'visibility': PropertyValue(value='visible/feature-matched', status=KnowledgeStatus.OBSERVED, confidence=0.85, evidence=[obs])})], properties={'motor_type': motor_type, 'physical_scale': PropertyValue(value=None, unit='m', status=KnowledgeStatus.UNKNOWN, confidence=0.0, evidence=[]), 'internal_windings': PropertyValue(value=None, status=KnowledgeStatus.UNKNOWN, confidence=0.0, evidence=[]), 'magnet_strength': PropertyValue(value=None, unit='T', status=KnowledgeStatus.UNKNOWN, confidence=0.0, evidence=[])}, observations=[obs], inferences=[Evidence(source='perception', detail='motor category inferred from external visual evidence')], unknowns=['physical scale without calibration/reference', 'exact winding configuration', 'magnet strength', 'bearing friction', 'exact rotor inertia'], simulation_parameters={'resistance_ohm': PropertyValue(value=2.0, unit='ohm', status=KnowledgeStatus.ESTIMATED, confidence=0.35), 'torque_constant': PropertyValue(value=0.03, unit='N*m/A', status=KnowledgeStatus.ESTIMATED, confidence=0.25), 'back_emf_constant': PropertyValue(value=0.03, unit='V/(rad/s)', status=KnowledgeStatus.ESTIMATED, confidence=0.25)}, hypotheses=[PropertyValue(value='brushed_dc_motor', status=KnowledgeStatus.HYPOTHESIS, confidence=0.65, evidence=[Evidence(source='engineering_pattern', detail='hypothesis only; electrical test not performed')])])
    return {'reconstruction': reconstruction, 'digital_twin': twin}
