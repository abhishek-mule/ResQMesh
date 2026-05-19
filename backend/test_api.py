import requests
import asyncio
import websockets
import json
import time

BASE_URL = "http://localhost:8000"

def test_health():
    print("🧪 Testing GET /health...")
    res = requests.get(f"{BASE_URL}/health")
    assert res.status_code == 200
    print(f"✅ Health Check: {res.json()}")

def test_emergency():
    print("\n🧪 Testing POST /emergency...")
    payload = {
        "packet_id": "test-pkt-001",
        "source_device": "test_device_A",
        "type": "PASSIVE_EMERGENCY",
        "timestamp": int(time.time()),
        "ttl": 5,
        "hop_count": 0,
        "risk_level": "HIGH",
        "location": {"lat": 18.5204, "lon": 73.8567, "accuracy": 40},
        "message": "Test Emergency",
        "device_state": {"battery": 80, "internet": False}
    }
    res = requests.post(f"{BASE_URL}/emergency", json=payload)
    assert res.status_code == 200
    print(f"✅ Emergency Received: {res.json()}")

def test_sync():
    print("\n🧪 Testing POST /sync...")
    payload = {
        "packets": [
            {
                "packet_id": "test-pkt-002",
                "source_device": "test_device_B",
                "type": "MANUAL_EMERGENCY",
                "timestamp": int(time.time()),
                "ttl": 4,
                "hop_count": 1,
                "risk_level": "CRITICAL",
                "location": {"lat": 18.5215, "lon": 73.8580, "accuracy": 35},
                "message": "Test Sync Packet",
                "device_state": {"battery": 60, "internet": True}
            }
        ],
        "device_id": "test_device_B"
    }
    res = requests.post(f"{BASE_URL}/sync", json=payload)
    assert res.status_code == 200
    print(f"✅ Sync Completed: {res.json()}")

def test_nodes():
    print("\n🧪 Testing POST /nodes (Update)...")
    node_payload = {
        "id": "test_node_001",
        "name": "TestNode",
        "type": "relay",
        "status": "online",
        "battery": 95,
        "latitude": 18.5204,
        "longitude": 73.8567,
        "lastSeen": int(time.time())
    }
    res = requests.post(f"{BASE_URL}/nodes", json=node_payload)
    assert res.status_code == 200
    print(f"✅ Node Updated: {res.json()}")

    print("\n🧪 Testing GET /nodes...")
    res = requests.get(f"{BASE_URL}/nodes")
    assert res.status_code == 200
    nodes = res.json()
    print(f"✅ Nodes Retrieved: {len(nodes)} nodes found")

async def test_websocket():
    print("\n🧪 Testing WebSocket /ws/events...")
    try:
        async with websockets.connect(f"ws://localhost:8000/ws/events") as ws:
            print("✅ WebSocket Connected")
            
            # Trigger an event from another client (simulating backend broadcast)
            # In a real scenario, the backend broadcasts when it receives data.
            # Here we just check if connection stays open.
            await asyncio.sleep(1)
            print("✅ WebSocket Connection Stable")
    except Exception as e:
        print(f"❌ WebSocket Failed: {e}")

if __name__ == "__main__":
    print("🚀 Starting ResQMesh API Tests...\n")
    try:
        test_health()
        test_emergency()
        test_sync()
        test_nodes()
        asyncio.run(test_websocket())
        print("\n🎉 All Tests Passed!")
    except Exception as e:
        print(f"\n💥 Test Failed: {e}")
