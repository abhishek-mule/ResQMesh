'use client'

import { useState, useEffect } from 'react'
import styles from './page.module.css'
import { TopStatusBar } from '@/components/TopStatusBar'
import { MeshGraph } from '@/components/MeshGraph'
import { GpsMap } from '@/components/GpsMap'
import { EventFeed } from '@/components/EventFeed'
import { NodeStatusPanel } from '@/components/NodeStatusPanel'
import { RelayPathVisualization } from '@/components/RelayPathVisualization'
import { wsManager } from '@/lib/websocket'
import { Node, EmergencyPacket, DashboardEvent, ConnectionMode } from '@/types'

export default function Home() {
  const [mode, setMode] = useState<ConnectionMode>('MESH')
  const [nodes, setNodes] = useState<Node[]>([])
  const [packets, setPackets] = useState<EmergencyPacket[]>([])
  const [events, setEvents] = useState<DashboardEvent[]>([])
  const [internetConnected, setInternetConnected] = useState(false)
  const [selectedPacket, setSelectedPacket] = useState<string | null>(null)

  useEffect(() => {
    wsManager.on('connection', (data) => {
      console.log('Connection status:', data)
    })

    wsManager.on('emergency', (packet: EmergencyPacket) => {
      setPackets(prev => [packet, ...prev])
      const newEvent: DashboardEvent = {
        id: `evt-${Date.now()}`,
        type: 'emergency',
        severity: packet.risk_level === 'CRITICAL' ? 'critical' : 'high',
        message: packet.message,
        timestamp: packet.timestamp,
        nodeId: packet.source_device,
        packetId: packet.packet_id
      }
      setEvents(prev => [newEvent, ...prev])
    })

    wsManager.on('packet', (packet: EmergencyPacket) => {
      setPackets(prev => {
        const exists = prev.find(p => p.packet_id === packet.packet_id)
        if (exists) return prev
        return [packet, ...prev]
      })
    })

    wsManager.on('node', (node: Node) => {
      setNodes(prev => {
        const exists = prev.find(n => n.id === node.id)
        if (exists) {
          return prev.map(n => n.id === node.id ? node : n)
        }
        return [node, ...prev]
      })
    })

    wsManager.on('mode_change', (data: { mode: ConnectionMode }) => {
      setMode(data.mode)
    })

    wsManager.connect()

    return () => {
      wsManager.disconnect()
    }
  }, [])

  const activeEmergencies = packets.filter(p => p.status !== 'SYNCED').length
  const onlineNodes = nodes.filter(n => n.status !== 'offline').length

  return (
    <div className={styles['dashboard-container']}>
      <TopStatusBar
        mode={mode}
        nodeCount={onlineNodes}
        emergencyCount={activeEmergencies}
        internetConnected={internetConnected}
        onToggleInternet={() => setInternetConnected(prev => !prev)}
      />

      <div className={styles['dashboard-grid']}>
        <div className={styles['mesh-graph-container']}>
          <MeshGraph
            nodes={nodes}
            packets={packets}
            mode={mode}
          />
        </div>

        <div className={styles['event-feed-container']}>
          <EventFeed
            events={events}
            packets={packets}
          />
        </div>
      </div>

      <div className={styles['map-container']}>
        <GpsMap
          nodes={nodes}
          packets={packets}
        />
      </div>

      <div className={styles['bottom-panels']}>
        <NodeStatusPanel nodes={nodes} />
        <RelayPathVisualization
          packets={packets}
          nodes={nodes}
          selectedPacket={selectedPacket}
          onSelectPacket={setSelectedPacket}
        />
      </div>
    </div>
  )
}
