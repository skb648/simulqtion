from fastapi.testclient import TestClient
from backend.app.main import app

client = TestClient(app)

def test_health():
    assert client.get('/health').status_code == 200

def test_scan_rejects_insufficient_coverage():
    r = client.post('/v1/scans/assess', json={'views':[{'id':'a','yaw_deg':0,'quality':0.9,'tracked':True,'feature_points':300}]})
    assert r.status_code == 200
    assert r.json()['sufficient'] is False

def test_twin_tracks_unknowns():
    views=[{'id':str(i),'yaw_deg':i*45,'quality':0.9,'tracked':True,'feature_points':500} for i in range(8)]
    r = client.post('/v1/digital-twins/dc-motor', json={'views':views})
    assert r.status_code == 200
    data=r.json()
    assert data['properties']['internal_windings']['status']=='UNKNOWN'
    assert data['object_type']['status']=='INFERRED'
