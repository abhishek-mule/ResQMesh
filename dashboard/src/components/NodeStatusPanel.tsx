'use client'

import { Node } from '@/types'

interface NodeStatusPanelProps {
  nodes: Node[]
}

const statusColors: Record<string, string> = {
  online: '#4caf50',
  relaying: '#ff9800',
  gateway_active: '#2196f3',
  offline: '#666666',
  internet_lost: '#d32f2f',
}

const statusLabels: Record<string, string> = {
  online: 'ONLINE',
  relaying: 'RELAYING',
  gateway_active: 'GATEWAY ACTIVE',
  offline: 'OFFLINE',
  internet_lost: 'INTERNET LOST',
}

function getBatteryColor(battery: number): string {
  if (battery > 60) return '#4caf50'
  if (battery > 30) return '#ff9800'
  return '#d32f2f'
}

export function NodeStatusPanel({ nodes }: NodeStatusPanelProps) {
  return (
    <div className="node-status-panel">
      <div className="panel-header">
        <span className="panel-title">Node Status Panel</span>
        <span className="node-count">{nodes.length} nodes</span>
      </div>

      <div className="node-list">
        <table className="node-table">
          <thead>
            <tr>
              <th>Node</th>
              <th>Status</th>
              <th>Battery</th>
              <th>Type</th>
            </tr>
          </thead>
          <tbody>
            {nodes.map(node => (
              <tr key={node.id} className={node.status === 'offline' ? 'node-offline' : ''}>
                <td className="node-name">{node.name}</td>
                <td>
                  <span
                    className="status-badge"
                    style={{
                      backgroundColor: `${statusColors[node.status]}20`,
                      color: statusColors[node.status],
                      border: `1px solid ${statusColors[node.status]}`,
                    }}
                  >
                    {statusLabels[node.status]}
                  </span>
                </td>
                <td>
                  <div className="battery-bar">
                    <div
                      className="battery-fill"
                      style={{
                        width: `${node.battery}%`,
                        backgroundColor: getBatteryColor(node.battery),
                      }}
                    ></div>
                    <span className="battery-text">{node.battery}%</span>
                  </div>
                </td>
                <td className="node-type">{node.type.toUpperCase()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
