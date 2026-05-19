'use client'

import { DashboardEvent, EmergencyPacket } from '@/types'

interface EventFeedProps {
  events: DashboardEvent[]
  packets: EmergencyPacket[]
}

const severityColors: Record<string, string> = {
  critical: '#d32f2f',
  high: '#ff9800',
  medium: '#2196f3',
  low: '#4caf50',
  info: '#666666',
}

const severityIcons: Record<string, string> = {
  critical: '🔴',
  high: '🟠',
  medium: '🔵',
  low: '🟢',
  info: '⚪',
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

export function EventFeed({ events, packets }: EventFeedProps) {
  return (
    <div className="event-feed">
      <div className="panel-header">
        <span className="panel-title">Live Event Feed</span>
        <span className="event-count">{events.length} events</span>
      </div>

      <div className="event-list">
        {events.map(event => (
          <div
            key={event.id}
            className={`event-item slide-in`}
            style={{ borderLeftColor: severityColors[event.severity] }}
          >
            <div className="event-header">
              <span className="event-severity">
                {severityIcons[event.severity]} [{event.severity.toUpperCase()}]
              </span>
              <span className="event-time">{formatTimestamp(event.timestamp)}</span>
            </div>

            <div className="event-message">{event.message}</div>

            <div className="event-meta">
              {event.nodeId && (
                <span className="meta-item">Node: {event.nodeId}</span>
              )}
              {event.packetId && (
                <span className="meta-item">Packet: {event.packetId}</span>
              )}
            </div>
          </div>
        ))}

        {events.length === 0 && (
          <div className="empty-state">No events yet</div>
        )}
      </div>
    </div>
  )
}
