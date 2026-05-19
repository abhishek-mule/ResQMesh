import { WebSocketServer, WebSocket } from 'ws'

const wss = new WebSocketServer({ port: 8000 })

console.log('WebSocket server running on ws://localhost:8000')

const clients = new Set<WebSocket>()

wss.on('connection', (ws) => {
  console.log('Client connected')
  clients.add(ws)

  ws.on('message', (message) => {
    console.log('Received:', message.toString())
  })

  ws.on('close', () => {
    console.log('Client disconnected')
    clients.delete(ws)
  })

  ws.send(JSON.stringify({
    type: 'connection',
    payload: { connected: true, timestamp: Date.now() }
  }))
})

function broadcast(type: string, payload: any) {
  const message = JSON.stringify({ type, payload })
  clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(message)
    }
  })
}
