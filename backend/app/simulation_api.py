from simulation.dc_motor.model import DCMotorParameters, DCMotorState, simulate
from .models import MotorSimulationRequest, SimulationPoint, SimulationResult

def run_motor(req: MotorSimulationRequest) -> SimulationResult:
    p = DCMotorParameters(resistance_ohm=req.resistance_ohm, inductance_h=req.inductance_h, torque_constant_nm_per_a=req.torque_constant, back_emf_v_per_rad_s=req.back_emf_constant, inertia_kg_m2=req.inertia_kg_m2, viscous_friction_nms=req.friction_nms, load_torque_nm=req.load_torque_nm)
    raw = simulate(req.voltage_v, req.duration_s, req.dt_s, p, DCMotorState())
    points = [SimulationPoint(time_s=t, current_a=r.state.current_a, angular_velocity_rad_s=r.state.angular_velocity_rad_s, speed_rpm=r.state.angular_velocity_rad_s * 60.0 / (2.0 * 3.141592653589793), back_emf_v=r.back_emf_v, torque_nm=r.electromagnetic_torque_nm, electrical_power_w=r.electrical_power_w, mechanical_power_w=r.mechanical_power_w) for t, r in raw]
    warnings = []
    if max(p.current_a for p in points) > 5.0:
        warnings.append('Simulated current exceeds 5 A; thermal behavior is not represented in this model.')
    return SimulationResult(model='simplified_dc_motor', duration_s=req.duration_s, points=points, final=points[-1], warnings=warnings)
