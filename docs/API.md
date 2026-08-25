# API

`GET /health` — service health.

`POST /v1/scans/assess` — assess scan coverage.

`POST /v1/reconstruction/multiview` — multipart JPEG upload; returns sparse reconstruction metadata.

`POST /v1/pipeline/dc-motor` — multipart JPEG upload; returns reconstruction + uncertainty-aware Digital Twin.

`POST /v1/simulations/dc-motor` — run one DC motor simulation.

`POST /v1/experiments/dc-motor` — run baseline and experiment branches.
