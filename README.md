# Reality Compiler

AI-powered Reality → Digital Twin → Interactive Simulation.

## DC motor vertical slice

This repository currently targets one object only: a **DC motor**.

```text
Android camera
  ↓
Multi-view capture
  ↓
Sparse reconstruction
  ↓
Uncertainty-aware Digital Twin
  ↓
DC motor equations
  ↓
Baseline / What-If simulation
  ↓
Android visualization
```

### Scientific honesty

The system does not claim to see through the motor housing. Exact windings, magnet strength, bearing friction, and exact rotor inertia are represented as unknown. The current visual reconstruction is sparse and uncalibrated, so reconstructed dimensions are relative rather than meters.

### Local development

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cd ..
PYTHONPATH=. pytest -q
PYTHONPATH=. python -m backend.app
```

For Android, open `android/RealityCompiler` in Android Studio. The project targets current Android tooling (AGP 9.3.1, Gradle 9.5.0, Compose BOM 2026.08.00, CameraX 1.6.1). The backend URL in the current prototype is an emulator-oriented development default (`10.0.2.2:8000`); a physical-device deployment needs the backend host changed to an address reachable from the phone.

See `docs/ARCHITECTURE.md`, `docs/DIGITAL_TWIN.md`, `docs/SIMULATION.md`, and `docs/API.md`.
