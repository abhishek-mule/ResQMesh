# ResQMesh

## A Delay-Tolerant Emergency Communication System Using Opportunistic Mesh Networking

### Core Problem
During disasters, internet fails, cellular towers collapse, and communication dies. Victims become digitally invisible.

### Solution
ResQMesh enables nearby smartphones to become temporary emergency relay nodes using Bluetooth LE, local mesh relay, and store-and-forward networking.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                 ANDROID DEVICE NODE                 │
│                                                     │
│  ├── Sensor Layer (GPS, Accelerometer, Gyroscope)   │
│  ├── Passive Emergency Engine (Impact Detection)    │
│  ├── Communication Engine (BLE Mesh + Cloud)        │
│  ├── Relay Engine (TTL, Hop Count, Store-and-Forward)│
│  └── Room Database (Packets, Nodes, Relay Logs)     │
└─────────────────────────┬───────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────┐
│                 FASTAPI BACKEND                     │
│  ├── POST /emergency  ├── POST /sync                │
│  ├── GET  /nodes      ├── GET  /health              │
│  └── WS   /events                                  │
└─────────────────────────┬───────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────┐
│                NEXT.JS DASHBOARD                    │
│  ├── Live Emergency Feed  ├── Mesh Graph Vis        │
│  ├── GPS Map             ├── Relay Path Animation   │
│  └── Rescue Coordination Panel                     │
└─────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer           | Technology       |
| --------------- | ---------------- |
| Mobile          | Kotlin + Jetpack Compose |
| Mesh Networking | Bluetooth LE     |
| Local DB        | Room Database    |
| Backend         | FastAPI          |
| Frontend        | Next.js          |
| Visualization   | React Flow       |
| Maps            | Leaflet          |
| Realtime        | WebSockets       |
| Hosting         | Railway + Vercel |

---

## Project Structure

```
app/src/main/java/com/resqmesh/app/
├── MainActivity.kt                 # Main UI with Compose
├── ResQMeshApplication.kt          # Application class
├── data/
│   └── local/
│       ├── ResQMeshDatabase.kt     # Room Database
│       ├── dao/
│       │   ├── PacketDao.kt        # Emergency packet queries
│       │   ├── NodeDao.kt          # Nearby node queries
│       │   └── RelayLogDao.kt      # Relay log queries
│       └── entity/
│           ├── EmergencyPacket.kt  # Packet entity
│           ├── NearbyNode.kt       # Node entity
│           └── RelayLog.kt         # Relay log entity
├── services/
│   ├── BleRelayManager.kt          # BLE mesh networking
│   ├── CloudSyncService.kt         # Backend sync
│   ├── DecisionEngine.kt           # Emergency detection logic
│   └── PassiveEmergencyService.kt  # Foreground service
├── ui/theme/
│   ├── Color.kt                    # Emergency color scheme
│   ├── Theme.kt                    # App theme
│   └── Type.kt                     # Typography
└── utils/
    └── LocationHelper.kt           # GPS location helper
```

---

## Key Features

### 1. Passive Emergency Detection
- Accelerometer-based impact detection
- 45-second observation window
- Risk score calculation (impact + inactivity)
- Auto-emergency trigger if threshold exceeded

### 2. BLE Mesh Networking
- Continuous device discovery via BLE advertisements
- GATT connections for peer-to-peer communication
- Multi-hop packet relay (Victim → B → C → Gateway)
- Duplicate filtering and TTL handling
- Store-and-forward when offline

### 3. Cloud Synchronization
- Automatic sync when internet available
- Periodic pending packet synchronization
- Direct emergency endpoint for critical alerts

### 4. Emergency UI
- Giant emergency button with pulse animation
- Real-time mesh network status
- Nearby node discovery display
- Live emergency feed with risk levels

---

## Packet Structure

```json
{
  "packet_id": "uuid-v4",
  "source_device": "device_A",
  "type": "PASSIVE_EMERGENCY",
  "timestamp": 1747500000,
  "ttl": 5,
  "hop_count": 2,
  "risk_level": "HIGH",
  "location": {
    "lat": 18.5204,
    "lon": 73.8567,
    "accuracy": 40
  },
  "device_state": {
    "battery": 31,
    "internet": false,
    "mesh_enabled": true
  }
}
```

---

## Workflows

### Normal Internet
```
Emergency Trigger → GPS Captured → Cloud Upload → Dashboard Update
```

### Infrastructure Failure
```
Emergency Trigger → No Internet → Mesh Mode → Packet Relay → 
Store-and-Forward → Gateway Reconnect → Synchronization
```

### Passive Detection
```
Impact Detected → Observation Window → No Movement → 
No User Response → Automatic Emergency Broadcast
```

---

## Setup

### Android App

#### Prerequisites
- Android Studio Hedgehog or later
- Kotlin 2.0.21
- Min SDK 26 (Android 8.0)
- Target SDK 35 (Android 15)

#### Build
```bash
./gradlew assembleDebug
```

#### Run
```bash
./gradlew installDebug
```

### Dashboard

#### Prerequisites
- Node.js 18+
- npm or yarn

#### Install & Run
```bash
cd dashboard
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000)

#### WebSocket Demo Server
```bash
npm run ws-server
```

---

## Permissions

- BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE (Android 12+)
- ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
- FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION
- INTERNET, ACCESS_NETWORK_STATE
- POST_NOTIFICATIONS

---

## Color System

| Color           | Meaning             |
| --------------- | ------------------- |
| Dark Gray/Black | Disaster operations |
| Red (#D32F2F)   | Emergency           |
| Orange (#FF9800)| Warning             |
| Green (#4CAF50) | Active nodes        |
| Blue (#2196F3)  | Connectivity        |

---

## License

MIT License
