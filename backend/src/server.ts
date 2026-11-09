import 'dotenv/config'

import cors from 'cors'
import express from 'express'
import { z } from 'zod'

import { initializeDatabase } from './lib/db.js'
import { getDashboard, getKnowledge, handleChatMessage } from './services/supportEngine.js'

const app = express()
const port = Number(process.env.PORT ?? 4000)
const clientOrigin = process.env.CLIENT_ORIGIN ?? 'http://localhost:5173'

app.use(
  cors({
    origin: clientOrigin,
  }),
)
app.use(express.json())

app.get('/api/health', async (_request, response) => {
  response.json({
    ok: true,
    service: 'support-backend',
    mode: process.env.DATABASE_URL || process.env.PGHOST ? 'database-enabled' : 'demo-mode',
  })
})

app.get('/api/dashboard', async (_request, response, next) => {
  try {
    response.json(await getDashboard())
  } catch (error) {
    next(error)
  }
})

app.get('/api/knowledge', async (_request, response, next) => {
  try {
    response.json(await getKnowledge())
  } catch (error) {
    next(error)
  }
})

app.post('/api/chat', async (request, response, next) => {
  try {
    const body = z.object({ message: z.string().min(3).max(2000) }).parse(request.body)
    response.json(await handleChatMessage(body.message))
  } catch (error) {
    next(error)
  }
})

app.use((error: unknown, _request: express.Request, response: express.Response, _next: express.NextFunction) => {
  const message = error instanceof Error ? error.message : 'Unexpected server error'
  response.status(500).json({ ok: false, message })
})

async function start() {
  try {
    await initializeDatabase()
  } catch (error) {
    console.warn('Database bootstrap skipped, using demo mode instead.', error)
  }

  app.listen(port, () => {
    console.log(`Support backend ready on http://localhost:${port}`)
  })
}

start()
