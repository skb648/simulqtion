from dataclasses import dataclass
import math

@dataclass(frozen=True)
class DCMotorParameters:
    resistance_ohm: float = 2.0
    inductance_h: float = 0.015
    torque_constant_nm_per_a: float = 0.03
    back_emf_v_per_rad_s: float = 0.03
    inertia_kg_m2: float = 1.2e-4
    viscous_friction_nms: float = 2.0e-5
    load_torque_nm: float = 0.0

@dataclass(frozen=True)
class DCMotorState:
    current_a: float = 0.0
    angular_velocity_rad_s: float = 0.0
    angle_rad: float = 0.0

@dataclass(frozen=True)
class DCMotorStep:
    state: DCMotorState
    back_emf_v: float
    electromagnetic_torque_nm: float
    friction_torque_nm: float
    load_torque_nm: float
    electrical_power_w: float
    mechanical_power_w: float

def step(state: DCMotorState, voltage_v: float, p: DCMotorParameters, dt_s: float) -> DCMotorStep:
    if dt_s <= 0 or not math.isfinite(dt_s): raise ValueError('dt_s must be positive and finite')
    if p.resistance_ohm <= 0 or p.inertia_kg_m2 <= 0 or p.inductance_h <= 0: raise ValueError('motor resistance, inductance, and inertia must be positive')
    back_emf = p.back_emf_v_per_rad_s * state.angular_velocity_rad_s
    di_dt = (voltage_v - p.resistance_ohm * state.current_a - back_emf) / p.inductance_h
    current = state.current_a + di_dt * dt_s
    torque = p.torque_constant_nm_per_a * current
    friction = p.viscous_friction_nms * state.angular_velocity_rad_s
    domega_dt = (torque - friction - p.load_torque_nm) / p.inertia_kg_m2
    omega = state.angular_velocity_rad_s + domega_dt * dt_s
    angle = state.angle_rad + omega * dt_s
    next_state = DCMotorState(current_a=current, angular_velocity_rad_s=omega, angle_rad=angle)
    return DCMotorStep(state=next_state, back_emf_v=back_emf, electromagnetic_torque_nm=torque, friction_torque_nm=friction, load_torque_nm=p.load_torque_nm, electrical_power_w=voltage_v * current, mechanical_power_w=torque * omega)

def simulate(voltage_v: float, duration_s: float, dt_s: float, p: DCMotorParameters, initial: DCMotorState | None = None):
    if duration_s <= 0 or not math.isfinite(duration_s): raise ValueError('duration_s must be positive and finite')
    if dt_s <= 0 or dt_s > duration_s: raise ValueError('dt_s must be positive and no greater than duration_s')
    state = initial or DCMotorState(); out = []; t = 0.0
    while t < duration_s - 1e-12:
        h = min(dt_s, duration_s - t)
        result = step(state, voltage_v, p, h); state = result.state; t += h; out.append((t, result))
    return out
