'use client'

import { useState, useEffect } from 'react'
import dynamic from 'next/dynamic'
import styles from './page.module.css'
import { TopStatusBar } from '@/components/TopStatusBar'
import { EventFeed } from '@/components/EventFeed'
import { NodeStatusPanel } from '@/components/NodeStatusPanel'
import { RelayPathVisualization } from '@/components/RelayPathVisualization'
import { wsManager } from '@/lib/websocket'
import { Node, EmergencyPacket, DashboardEvent, ConnectionMode } from '@/types'

const MeshGraph = dynamic(async () => {
  const mod = await import('@/components/MeshGraph')
  return { default: mod.MeshGraph }
}, { 
  ssr: false,
  loading: () => <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', background: '#1e1e1e', color: '#666' }}>Loading Graph...</div>
})

const GpsMap = dynamic(async () => {
  const mod = await import('@/components/GpsMap')
  return { default: mod.default }
}, { 
  ssr: false, 
  loading: () => <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', background: '#1e1e1e', color: '#666' }}>Loading Map...</div> 
})

export default function Home() {
  const [mode, setMode] = useState<ConnectionMode>('MESH')
  const [nodes, setNodes] = useState<Node[]>([])
  const [packets, setPackets] = useState<EmergencyPacket[]>([])
  const [events, setEvents] = useState<DashboardEvent[]>([])
  const [internetConnected, setInternetConnected] = useState(false)
  const [selectedPacket, setSelectedPacket] = useState<string | null>(null)
  const [wsConnected, setWsConnected] = useState(false)

  useEffect(() => {
    // Fetch initial nodes from backend
    fetch('http://192.168.1.2:8000/nodes')
      .then(res => res.json())
      .then(data => {
        console.log('Initial nodes fetched:', data)
        const initialNodes = data.map((n: any) => ({
          id: n.node_id,
          name: n.name,
          type: n.type || 'relay',
          status: n.status || 'online',
          battery: n.battery || 50,
          latitude: n.latitude || 18.5204,
          longitude: n.longitude || 73.8567,
          lastSeen: n.last_seen || Date.now()
        }))
        setNodes(initialNodes)
      })
      .catch(err => console.log('Failed to fetch initial nodes:', err))

    wsManager.on('connection', (data) => {
      console.log('Connection status:', data)
      setWsConnected(data.connected)
    })

    wsManager.on('emergency', (packet: EmergencyPacket) => {
      console.log('Received emergency:', packet)
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
      console.log('Received packet:', packet)
      setPackets(prev => {
        const exists = prev.find(p => p.packet_id === packet.packet_id)
        if (exists) return prev
        return [packet, ...prev]
      })
    })

    wsManager.on('node', (node: Node) => {
      console.log('Received node:', node)
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
        wsConnected={wsConnected}
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
