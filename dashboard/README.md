# ResQMesh Dashboard

## Emergency Operations Dashboard

Real-time visualization system for the ResQMesh delay-tolerant emergency communication network.

---

## Features

### 1. Live Mesh Propagation Graph
- Real-time network topology visualization using React Flow
- Node types: Victim (Red), Relay (Orange), Gateway (Blue), Rescue (Green)
- Animated packet flow showing multi-hop relay paths
- Interactive zoom, pan, and node inspection

### 2. GPS Rescue Map
- Leaflet.js-based dark-themed map
- Emergency location markers with accuracy radius
- Node density visualization
- Real-time position updates

### 3. Live Event Feed
- Chronological emergency events
- Severity-based color coding (Critical, High, Medium, Low, Info)
- Packet and node metadata display
- Real-time WebSocket updates

### 4. Relay Path Visualization
- Click-to-expand packet journey visualization
- Step-by-step relay path: Victim → Relay → Gateway → Server
- Packet details: hops, TTL, battery, status
- Timestamp tracking for each hop

### 5. Node Status Panel
- Real-time node monitoring
- Status indicators: Online, Relaying, Gateway Active, Offline, Internet Lost
- Battery level visualization
- Node type classification

### 6. Cloud/Mesh Mode Switching
- Visual mode indicator in top status bar
- Demo button to simulate internet failure
- Automatic mode transition visualization

---

## Tech Stack

| Purpose        | Library     |
| -------------- | ----------- |
| Framework      | Next.js 14  |
| Network Graph  | React Flow  |
| Maps           | Leaflet.js  |
| Realtime       | WebSockets  |
| Styling        | CSS Modules |
| Language       | TypeScript  |

---

## Setup

### Prerequisites
- Node.js 18+
- npm or yarn

### Install Dependencies
```bash
cd dashboard
npm install
```

### Run Development Server
```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000)

### Run WebSocket Server (Optional)
For real-time demo with simulated events:
```bash
npm run ws-server
```

---

## Demo Flow

### 1. Initial State
- Dashboard loads with mock data
- Shows active mesh network
- All nodes visible on graph and map

### 2. Simulate Internet Failure
- Click "Simulate Failure" button
- Status changes to MESH MODE
- Internet indicator turns red

### 3. Show Mesh Propagation
- New emergency events appear in feed
- Animated edges show packet relay paths
- GPS map shows emergency locations with accuracy radius

### 4. Demonstrate Relay Path
- Click on any packet in relay path panel
- Expands to show full journey: Victim → Node_B → Node_C → Gateway → Server

### 5. Restore Internet
- Click "Restore Internet" button
- Mode switches back to CLOUD MODE
- Packets sync to server

---

## Design Philosophy

### Emergency Operations Center Aesthetic
- Dark background (#121212) for reduced eye strain
- Matte surfaces for tactical feel
- Monospace fonts for data readability
- Color-coded severity system
- No gradients, glassmorphism, or flashy effects

### Color System
| Color           | Hex       | Meaning             |
| --------------- | --------- | ------------------- |
| Emergency Red   | #D32F2F   | Critical/Victim     |
| Warning Orange  | #FF9800   | Warning/Relay       |
| Connectivity Blue| #2196F3  | Gateway/Cloud       |
| Active Green    | #4CAF50   | Rescue/Online       |
| Dark Background | #121212   | Operations base     |

---

## Project Structure

```
dashboard/
├── src/
│   ├── app/
│   │   ├── layout.tsx          # Root layout
│   │   ├── page.tsx            # Main dashboard page
│   │   ├── globals.css         # Global styles
│   │   └── page.module.css     # Page-specific styles
│   ├── components/
│   │   ├── TopStatusBar.tsx    # Top status bar
│   │   ├── MeshGraph.tsx       # React Flow mesh graph
│   │   ├── GpsMap.tsx          # Leaflet GPS map
│   │   ├── EventFeed.tsx       # Live event feed
│   │   ├── NodeStatusPanel.tsx # Node status table
│   │   ├── RelayPathVisualization.tsx # Relay path viewer
│   │   └── components.css      # Component styles
│   ├── lib/
│   │   ├── websocket.ts        # WebSocket manager
│   │   ├── mockData.ts         # Demo mock data
│   │   └── server.ts           # WebSocket demo server
│   └── types/
│       └── index.ts            # TypeScript types
├── public/
├── package.json
├── next.config.js
└── tsconfig.json
```

---

## API Integration

The dashboard connects to the FastAPI backend via WebSocket at `ws://localhost:8000/ws/events`.

### Expected WebSocket Messages

```json
{
  "type": "emergency",
  "payload": {
    "packet_id": "uuid",
    "source_device": "device_id",
    "risk_level": "HIGH",
    "timestamp": 1234567890,
    "latitude": 18.5204,
    "longitude": 73.8567,
    "message": "Emergency message"
  }
}
```

Message types: `emergency`, `packet_relayed`, `node_update`, `mode_change`, `sync`

---

## License

MIT
