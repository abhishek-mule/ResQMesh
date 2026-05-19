import { DashboardEvent, EmergencyPacket, Node, ConnectionMode } from '@/types'

type MessageHandler = (data: any) => void

export class WebSocketManager {
  private ws: WebSocket | null = null
  private handlers: Map<string, MessageHandler[]> = new Map()
  private reconnectInterval = 3000
  private maxReconnectAttempts = 10
  private reconnectAttempts = 0
  private url: string

  constructor(url: string = 'ws://localhost:8000/ws/events') {
    this.url = url
  }

  connect() {
    try {
      this.ws = new WebSocket(this.url)

      this.ws.onopen = () => {
        console.log('WebSocket connected')
        this.reconnectAttempts = 0
        this.emit('connection', { connected: true })
      }

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          this.handleMessage(data)
        } catch (e) {
          console.error('Failed to parse WebSocket message:', e)
        }
      }

      this.ws.onclose = () => {
        console.log('WebSocket disconnected')
        this.emit('connection', { connected: false })
        this.attemptReconnect()
      }

      this.ws.onerror = (error) => {
        console.error('WebSocket error:', error)
      }
    } catch (e) {
      console.error('Failed to create WebSocket:', e)
      this.attemptReconnect()
    }
  }

  private attemptReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      setTimeout(() => {
        console.log(`Reconnecting... Attempt ${this.reconnectAttempts}`)
        this.connect()
      }, this.reconnectInterval)
    }
  }

  private handleMessage(data: any) {
    const { type, payload } = data

    switch (type) {
      case 'emergency':
        this.emit('emergency', payload)
        break
      case 'packet_relayed':
        this.emit('packet', payload)
        break
      case 'node_update':
        this.emit('node', payload)
        break
      case 'mode_change':
        this.emit('mode_change', payload)
        break
      case 'sync':
        this.emit('sync', payload)
        break
      default:
        this.emit('message', data)
    }
  }

  on(event: string, handler: MessageHandler) {
    if (!this.handlers.has(event)) {
      this.handlers.set(event, [])
    }
    this.handlers.get(event)!.push(handler)
  }

  off(event: string, handler: MessageHandler) {
    const handlers = this.handlers.get(event)
    if (handlers) {
      this.handlers.set(event, handlers.filter(h => h !== handler))
    }
  }

  private emit(event: string, data: any) {
    const handlers = this.handlers.get(event)
    if (handlers) {
      handlers.forEach(handler => handler(data))
    }
  }

  send(data: any) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }

  disconnect() {
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }
}

export const wsManager = new WebSocketManager()
