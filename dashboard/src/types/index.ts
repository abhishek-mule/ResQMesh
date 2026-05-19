export interface EmergencyPacket {
  packet_id: string
  source_device: string
  type: string
  risk_level: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  timestamp: number
  ttl: number
  hop_count: number
  latitude: number
  longitude: number
  accuracy: number
  message: string
  battery: number
  internet_available: boolean
  status: 'PENDING' | 'RELAYED' | 'SYNCED'
}

export interface Node {
  id: string
  name: string
  type: 'victim' | 'relay' | 'gateway' | 'rescue'
  status: 'online' | 'relaying' | 'internet_lost' | 'gateway_active' | 'offline'
  battery: number
  latitude: number
  longitude: number
  lastSeen: number
  rssi?: number
}

export interface RelayPath {
  packetId: string
  hops: RelayHop[]
}

export interface RelayHop {
  from: string
  to: string
  timestamp: number
  action: string
}

export interface DashboardEvent {
  id: string
  type: 'emergency' | 'relay' | 'node_connected' | 'node_disconnected' | 'sync' | 'mode_change'
  severity: 'critical' | 'high' | 'medium' | 'low' | 'info'
  message: string
  timestamp: number
  nodeId?: string
  packetId?: string
}

export type ConnectionMode = 'CLOUD' | 'MESH'

export interface DashboardState {
  mode: ConnectionMode
  nodes: Node[]
  events: DashboardEvent[]
  packets: EmergencyPacket[]
  activeEmergencies: number
  internetConnected: boolean
}
