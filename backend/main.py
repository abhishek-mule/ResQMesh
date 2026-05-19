import sqlite3
import json
import asyncio
from typing import List, Optional
from contextlib import contextmanager
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import uvicorn

# --- Database Setup ---
DATABASE = "resqmesh.db"

def init_db():
    conn = sqlite3.connect(DATABASE)
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS packets (
            packet_id TEXT PRIMARY KEY,
            source_device TEXT,
            type TEXT,
            risk_level TEXT,
            timestamp INTEGER,
            ttl INTEGER,
            hop_count INTEGER,
            latitude REAL,
            longitude REAL,
            accuracy REAL,
            message TEXT,
            battery INTEGER,
            internet_available BOOLEAN,
            status TEXT
        )
    ''')
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS nodes (
            node_id TEXT PRIMARY KEY,
            name TEXT,
            type TEXT,
            status TEXT,
            battery INTEGER,
            latitude REAL,
            longitude REAL,
            last_seen INTEGER
        )
    ''')
    conn.commit()
    conn.close()

init_db()

@contextmanager
def get_db():
    conn = sqlite3.connect(DATABASE)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
    finally:
        conn.close()

# --- Pydantic Models ---
class Location(BaseModel):
    lat: float
    lon: float
    accuracy: float

class DeviceState(BaseModel):
    battery: int
    internet: bool
    mesh_enabled: bool = True

class EmergencyPacket(BaseModel):
    packet_id: str
    source_device: str
    type: str
    timestamp: int
    ttl: int
    hop_count: int
    risk_level: str
    location: Location
    message: str = ""
    device_state: Optional[DeviceState] = None

class SyncRequest(BaseModel):
    packets: List[EmergencyPacket]
    device_id: str

class NodeUpdate(BaseModel):
    id: str
    name: str
    type: str
    status: str
    battery: int
    latitude: float
    longitude: float
    lastSeen: int

# --- WebSocket Manager ---
class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast(self, data: dict):
        for connection in self.active_connections:
            try:
                await connection.send_json(data)
            except Exception:
                pass

manager = ConnectionManager()

# --- FastAPI App ---
app = FastAPI(title="ResQMesh Backend")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
def health_check():
    return {"status": "ok", "message": "ResQMesh Backend is running"}

@app.post("/emergency")
async def receive_emergency(packet: EmergencyPacket):
    with get_db() as conn:
        cursor = conn.cursor()
        try:
            cursor.execute('''
                INSERT OR REPLACE INTO packets 
                (packet_id, source_device, type, risk_level, timestamp, ttl, hop_count, latitude, longitude, accuracy, message, battery, internet_available, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                packet.packet_id, packet.source_device, packet.type, packet.risk_level,
                packet.timestamp, packet.ttl, packet.hop_count,
                packet.location.lat, packet.location.lon, packet.location.accuracy,
                packet.message, packet.device_state.battery if packet.device_state else 0,
                packet.device_state.internet if packet.device_state else False, "PENDING"
            ))
            conn.commit()
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

    await manager.broadcast({
        "type": "emergency",
        "payload": {
            "packet_id": packet.packet_id,
            "source_device": packet.source_device,
            "risk_level": packet.risk_level,
            "timestamp": packet.timestamp,
            "latitude": packet.location.lat,
            "longitude": packet.location.lon,
            "accuracy": packet.location.accuracy,
            "message": packet.message,
            "battery": packet.device_state.battery if packet.device_state else 0,
            "internet_available": packet.device_state.internet if packet.device_state else False,
            "status": "PENDING",
            "ttl": packet.ttl,
            "hop_count": packet.hop_count
        }
    })
    return {"status": "received", "packet_id": packet.packet_id}

@app.post("/sync")
async def sync_packets(request: SyncRequest):
    received_count = 0
    with get_db() as conn:
        cursor = conn.cursor()
        for packet in request.packets:
            try:
                cursor.execute('''
                    INSERT OR REPLACE INTO packets 
                    (packet_id, source_device, type, risk_level, timestamp, ttl, hop_count, latitude, longitude, accuracy, message, battery, internet_available, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ''', (
                    packet.packet_id, packet.source_device, packet.type, packet.risk_level,
                    packet.timestamp, packet.ttl, packet.hop_count,
                    packet.location.lat, packet.location.lon, packet.location.accuracy,
                    packet.message, packet.device_state.battery if packet.device_state else 0,
                    packet.device_state.internet if packet.device_state else False, "SYNCED"
                ))
                received_count += 1
            except Exception:
                continue
        conn.commit()

    await manager.broadcast({
        "type": "sync",
        "payload": {"device_id": request.device_id, "count": received_count}
    })
    return {"status": "synced", "received_count": received_count}

@app.get("/nodes")
async def get_nodes():
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM nodes ORDER BY last_seen DESC")
        rows = cursor.fetchall()
        return [dict(row) for row in rows]

@app.post("/nodes")
async def update_node(node: NodeUpdate):
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute('''
            INSERT OR REPLACE INTO nodes (node_id, name, type, status, battery, latitude, longitude, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ''', (node.id, node.name, node.type, node.status, node.battery, node.latitude, node.longitude, node.lastSeen))
        conn.commit()

    await manager.broadcast({
        "type": "node_update",
        "payload": dict(node)
    })
    return {"status": "updated", "node_id": node.id}

@app.websocket("/ws/events")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket)

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
