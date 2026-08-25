from backend.app.models import MotorSimulationRequest
from backend.app.simulation_api import run_motor

def test_no_load_steady_state_is_close_to_analytical_solution():
    v = 12.0
    R = 2.0
    K = 0.03
    b = 2e-5
    analytical_omega = v / (0.03 + R * b / K)
    r = run_motor(MotorSimulationRequest(voltage_v=v, duration_s=2.0, dt_s=0.0002))
    assert abs(r.final.angular_velocity_rad_s - analytical_omega) / analytical_omega < 0.04
