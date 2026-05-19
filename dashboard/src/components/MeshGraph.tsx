'use client'

import { useMemo, useEffect, useState } from 'react'
import ReactFlow, {
  Node,
  Edge,
  Controls,
  Background,
  MarkerType,
  Position,
  useNodesState,
  useEdgesState,
} from 'reactflow'
import { Node as NodeType, EmergencyPacket, ConnectionMode } from '@/types'

interface MeshGraphProps {
  nodes: NodeType[]
  packets: EmergencyPacket[]
  mode: ConnectionMode
}

const nodeColors: Record<string, string> = {
  victim: '#d32f2f',
  relay: '#ff9800',
  gateway: '#2196f3',
  rescue: '#4caf50',
}

const nodeStatusColors: Record<string, string> = {
  online: '#4caf50',
  relaying: '#ff9800',
  gateway_active: '#2196f3',
  offline: '#666666',
  internet_lost: '#d32f2f',
}

function createReactFlowNode(node: NodeType, index: number, total: number): Node {
  const angle = (index / total) * Math.PI * 2
  const radius = 200
  const x = 400 + radius * Math.cos(angle)
  const y = 250 + radius * Math.sin(angle)

  return {
    id: node.id,
    type: 'default',
    position: { x, y },
    data: {
      label: node.name,
      type: node.type,
      status: node.status,
      battery: node.battery,
    },
    style: {
      background: '#1e1e1e',
      border: `2px solid ${nodeColors[node.type]}`,
      borderRadius: '8px',
      color: '#eaeaea',
      fontSize: '12px',
      fontWeight: '600',
      padding: '10px 16px',
      boxShadow: node.status === 'offline' ? 'none' : `0 0 10px ${nodeColors[node.type]}40`,
      width: 'auto',
    },
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
  }
}

function createReactFlowEdges(nodes: NodeType[], packets: EmergencyPacket[]): Edge[] {
  const edges: Edge[] = []

  for (let i = 0; i < nodes.length - 1; i++) {
    if (nodes[i].status !== 'offline' && nodes[i + 1].status !== 'offline') {
      edges.push({
        id: `edge-${nodes[i].id}-${nodes[i + 1].id}`,
        source: nodes[i].id,
        target: nodes[i + 1].id,
        type: 'smoothstep',
        animated: true,
        style: { stroke: '#2196f3', strokeWidth: 2 },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: '#2196f3',
        },
      })
    }
  }

  return edges
}

export function MeshGraph({ nodes, packets, mode }: MeshGraphProps) {
  const [flowNodes, setFlowNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [animatedEdges, setAnimatedEdges] = useState<Edge[]>([])

  useEffect(() => {
    const rfNodes = nodes.map((node, index) => createReactFlowNode(node, index, nodes.length))
    setFlowNodes(rfNodes)

    const rfEdges = createReactFlowEdges(nodes, packets)
    setEdges(rfEdges)
  }, [nodes, packets])

  useEffect(() => {
    if (packets.length > 0) {
      const latestPacket = packets[0]
      const newAnimatedEdges = edges.map(edge => ({
        ...edge,
        animated: true,
        style: {
          ...edge.style,
          stroke: latestPacket.risk_level === 'CRITICAL' ? '#d32f2f' : '#ff9800',
          strokeWidth: 3,
        },
      }))
      setAnimatedEdges(newAnimatedEdges)

      const timer = setTimeout(() => {
        setAnimatedEdges(edges)
      }, 3000)

      return () => clearTimeout(timer)
    }
  }, [packets])

  const activeEdges = animatedEdges.length > 0 ? animatedEdges : edges

  return (
    <div style={{ width: '100%', height: '100%' }}>
      <div className="panel-header">
        <span className="panel-title">Live Mesh Propagation Graph</span>
        <span className={`mode-indicator ${mode === 'MESH' ? 'mode-mesh' : 'mode-cloud'}`}>
          {mode}
        </span>
      </div>
      <ReactFlow
        nodes={flowNodes}
        edges={activeEdges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        fitView
        attributionPosition="bottom-right"
      >
        <Background color="#333" gap={20} />
        <Controls />
      </ReactFlow>
      <div className="graph-legend">
        <div className="legend-item">
          <span className="legend-dot" style={{ backgroundColor: nodeColors.victim }}></span>
          <span>Victim</span>
        </div>
        <div className="legend-item">
          <span className="legend-dot" style={{ backgroundColor: nodeColors.relay }}></span>
          <span>Relay Node</span>
        </div>
        <div className="legend-item">
          <span className="legend-dot" style={{ backgroundColor: nodeColors.gateway }}></span>
          <span>Gateway</span>
        </div>
        <div className="legend-item">
          <span className="legend-dot" style={{ backgroundColor: nodeColors.rescue }}></span>
          <span>Rescue</span>
        </div>
      </div>
    </div>
  )
}
