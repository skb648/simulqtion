from backend.app.models import MotorSimulationRequest
from backend.app.simulation_api import run_motor

def test_zero_voltage_zero_load_stays_at_rest():
    r = run_motor(MotorSimulationRequest(voltage_v=0, duration_s=0.1, dt_s=0.001))
    assert abs(r.final.current_a) < 1e-12
    assert abs(r.final.angular_velocity_rad_s) < 1e-12

def test_voltage_drives_current_and_speed():
    r = run_motor(MotorSimulationRequest(voltage_v=12, duration_s=0.5, dt_s=0.0005))
    assert r.final.current_a > 0
    assert r.final.speed_rpm > 0
    assert r.final.torque_nm > 0
