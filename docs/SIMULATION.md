# DC motor simulation

The model uses:

`L di/dt = V - R i - K_e ω`

`J dω/dt = K_t i - b ω - τ_load`

`dθ/dt = ω`

`P_e = V i`

`P_m = τ ω`

The implementation uses explicit time stepping. Thermal behavior is not claimed; high simulated current produces a warning.

For zero load and steady state, the analytical benchmark is:

`ω = V / (K_e + R b / K_t)`.
