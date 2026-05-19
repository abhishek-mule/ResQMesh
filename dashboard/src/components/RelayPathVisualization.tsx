'use client'

import { EmergencyPacket, Node } from '@/types'

interface RelayPathVisualizationProps {
  packets: EmergencyPacket[]
  nodes: Node[]
  selectedPacket: string | null
  onSelectPacket: (packetId: string | null) => void
}

function formatTimestamp(timestamp: number): string {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

export function RelayPathVisualization({
  packets,
  nodes,
  selectedPacket,
  onSelectPacket,
}: RelayPathVisualizationProps) {
  const getNodeName = (deviceId: string): string => {
    const node = nodes.find(n => n.id === deviceId)
    return node ? node.name : deviceId
  }

  const getRelayPath = (packet: EmergencyPacket): string[] => {
    const path = [packet.source_device]

    for (let i = 1; i <= packet.hop_count; i++) {
      const nextNode = nodes[i]
      if (nextNode) {
        path.push(nextNode.id)
      }
    }

    return path
  }

  return (
    <div className="relay-path-panel">
      <div className="panel-header">
        <span className="panel-title">Relay Path Visualization</span>
        <span className="packet-count">{packets.length} packets</span>
      </div>

      <div className="packet-list">
        {packets.map(packet => {
          const path = getRelayPath(packet)
          const isSelected = selectedPacket === packet.packet_id

          return (
            <div
              key={packet.packet_id}
              className={`packet-item ${isSelected ? 'packet-selected' : ''}`}
              onClick={() => onSelectPacket(isSelected ? null : packet.packet_id)}
            >
              <div className="packet-header">
                <span
                  className="risk-badge"
                  style={{
                    backgroundColor:
                      packet.risk_level === 'CRITICAL'
                        ? '#d32f2f'
                        : packet.risk_level === 'HIGH'
                        ? '#ff9800'
                        : '#2196f3',
                  }}
                >
                  {packet.risk_level}
                </span>
                <span className="packet-id">{packet.packet_id}</span>
                <span className="packet-time">{formatTimestamp(packet.timestamp)}</span>
              </div>

              {isSelected && (
                <div className="relay-path-detail slide-in">
                  <div className="path-steps">
                    {path.map((nodeId, index) => (
                      <div key={nodeId} className="path-step">
                        <div className="step-node">
                          <span className="step-dot"></span>
                          <span className="step-name">{getNodeName(nodeId)}</span>
                        </div>
                        {index < path.length - 1 && (
                          <div className="step-arrow">↓</div>
                        )}
                      </div>
                    ))}
                    <div className="path-step">
                      <div className="step-node step-server">
                        <span className="step-dot"></span>
                        <span className="step-name">Server</span>
                      </div>
                    </div>
                  </div>

                  <div className="packet-details">
                    <div className="detail-row">
                      <span className="detail-label">Type:</span>
                      <span className="detail-value">{packet.type}</span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">Hops:</span>
                      <span className="detail-value">{packet.hop_count}</span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">TTL:</span>
                      <span className="detail-value">{packet.ttl}</span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">Battery:</span>
                      <span className="detail-value">{packet.battery}%</span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">Status:</span>
                      <span className="detail-value">{packet.status}</span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )
        })}

        {packets.length === 0 && (
          <div className="empty-state">No packets received yet</div>
        )}
      </div>
    </div>
  )
}
