'use client'

import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Circle, Marker, Popup, useMap } from 'react-leaflet'
import L from 'leaflet'
import { Node, EmergencyPacket } from '@/types'

interface GpsMapProps {
  nodes: Node[]
  packets: EmergencyPacket[]
}

function createColoredIcon(color: string) {
  if (typeof window === 'undefined') return undefined
  
  return L.divIcon({
    className: 'custom-marker',
    html: `<div style="
      background-color: ${color};
      width: 16px;
      height: 16px;
      border-radius: 50%;
      border: 2px solid #fff;
      box-shadow: 0 0 10px ${color};
    "></div>`,
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  })
}

const nodeColors: Record<string, string> = {
  victim: '#d32f2f',
  relay: '#ff9800',
  gateway: '#2196f3',
  rescue: '#4caf50',
}

function MapUpdater({ nodes }: { nodes: Node[] }) {
  const map = useMap()

  useEffect(() => {
    if (nodes.length > 0) {
      const bounds = L.latLngBounds(nodes.map(n => [n.latitude, n.longitude]))
      map.fitBounds(bounds, { padding: [50, 50] })
    }
  }, [nodes, map])

  return null
}

export default function GpsMap({ nodes, packets }: GpsMapProps) {
  const [mounted, setMounted] = useState(false)
  
  const center: [number, number] = nodes.length > 0
    ? [nodes[0].latitude, nodes[0].longitude]
    : [18.5204, 73.8567]

  useEffect(() => {
    setMounted(true)
    if (typeof window !== 'undefined') {
      delete (L.Icon.Default.prototype as any)._getIconUrl
      L.Icon.Default.mergeOptions({
        iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
        iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
        shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
      })
    }
  }, [])

  if (!mounted) {
    return (
      <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#1e1e1e', color: '#666' }}>
        Loading Map...
      </div>
    )
  }

  return (
    <div style={{ width: '100%', height: '100%' }}>
      <div className="panel-header">
        <span className="panel-title">GPS Rescue Map</span>
        <span className="panel-subtitle">{nodes.length} nodes tracked</span>
      </div>
      <MapContainer
        center={center}
        zoom={15}
        style={{ width: '100%', height: 'calc(100% - 40px)' }}
        zoomControl={true}
      >
        <TileLayer
          url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/">CARTO</a>'
        />

        <MapUpdater nodes={nodes} />

        {nodes.map(node => {
          const icon = createColoredIcon(nodeColors[node.type])
          return (
            <Marker
              key={node.id}
              position={[node.latitude, node.longitude]}
              icon={icon}
            >
              <Popup>
                <div style={{ color: '#333', fontFamily: 'monospace' }}>
                  <strong>{node.name}</strong><br />
                  Type: {node.type.toUpperCase()}<br />
                  Status: {node.status}<br />
                  Battery: {node.battery}%<br />
                  Lat: {node.latitude.toFixed(4)}<br />
                  Lon: {node.longitude.toFixed(4)}
                </div>
              </Popup>
            </Marker>
          )
        })}

        {packets.map(packet => (
          <Circle
            key={packet.packet_id}
            center={[packet.latitude, packet.longitude]}
            radius={packet.accuracy}
            pathOptions={{
              color: packet.risk_level === 'CRITICAL' ? '#d32f2f' : '#ff9800',
              fillColor: packet.risk_level === 'CRITICAL' ? '#d32f2f' : '#ff9800',
              fillOpacity: 0.2,
            }}
          >
            <Popup>
              <div style={{ color: '#333', fontFamily: 'monospace' }}>
                <strong>Emergency: {packet.risk_level}</strong><br />
                {packet.message}<br />
                Accuracy: ±{packet.accuracy}m<br />
                Hops: {packet.hop_count}
              </div>
            </Popup>
          </Circle>
        ))}
      </MapContainer>
    </div>
  )
}