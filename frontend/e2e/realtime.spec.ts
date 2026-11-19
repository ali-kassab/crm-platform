import type { WebSocketRoute } from '@playwright/test'
import { expect, test } from './fixtures'

/**
 * Realtime hook (ADR-0015) against a fake Socket.IO server: the browser must authenticate the
 * socket with a one-time ticket from the Java API (never in the URL), fetch a fresh ticket for every
 * connection, refetch the matching screens on a hint, and refetch everything after a reconnect.
 * Engine.IO v4 / Socket.IO v5 wire format: "0{…}" open, "40{auth}" connect, "42[event, data]" event.
 */

const OPEN = '0' + JSON.stringify({ sid: 'e1', upgrades: [], pingInterval: 25000, pingTimeout: 20000, maxPayload: 1e6 })

interface FakeSocketServer {
  sockets: WebSocketRoute[]
  urls: string[]
  /** `auth` objects the client sent when connecting, in order. */
  auths: unknown[]
}

test.beforeEach(({ api }) => {
  api.signedIn = true
})

async function fakeRealtime(page: import('@playwright/test').Page): Promise<{ server: FakeSocketServer; tickets: string[] }> {
  const tickets: string[] = []
  await page.route('**/api/v1/realtime/ticket', (route) => {
    const ticket = `ticket-${tickets.length + 1}`.padEnd(43, 'x')
    tickets.push(ticket)
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ticket, expiresInSeconds: 60 }) })
  })
  const server: FakeSocketServer = { sockets: [], urls: [], auths: [] }
  await page.routeWebSocket(/\/realtime\/socket\.io\//, (ws) => {
    server.sockets.push(ws)
    server.urls.push(ws.url())
    ws.send(OPEN)
    ws.onMessage((raw) => {
      const m = String(raw)
      if (m.startsWith('40')) {
        server.auths.push(m.length > 2 ? JSON.parse(m.slice(2)) : null)
        ws.send('40' + JSON.stringify({ sid: `s${server.sockets.length}` }))
        ws.send('42' + JSON.stringify(['ready']))
      }
    })
  })
  return { server, tickets }
}

const emit = (ws: WebSocketRoute, event: string, data?: unknown) =>
  ws.send('42' + JSON.stringify(data === undefined ? [event] : [event, data]))

test('hints refresh the open screen; reconnects use a fresh ticket and refetch', async ({ page, api }) => {
  const { server, tickets } = await fakeRealtime(page)
  const jobLists = () => api.requests.filter((r) => r.method === 'GET' && /^\/jobs(\?|$)/.test(r.path)).length

  await page.goto('/jobs')
  await expect(page.getByText('لا توجد أوامر عمل')).toBeVisible()
  await expect.poll(() => server.auths.length).toBe(1)

  // the ticket travels only in the handshake auth, never in the URL; one ticket per connection
  expect(server.auths[0]).toEqual({ ticket: tickets[0] })
  expect(server.urls[0]).not.toContain(tickets[0])
  expect(tickets).toHaveLength(1)

  // a work-order hint refetches the list (ids only — the data comes from the API)
  const before = jobLists()
  emit(server.sockets[0], 'changed', { entityType: 'JOB', entityId: null, jobId: null, action: 'STATUS_CHANGED' })
  await expect.poll(jobLists).toBeGreaterThan(before)

  // a board hint does not touch work-order queries
  const afterJob = jobLists()
  emit(server.sockets[0], 'changed', { entityType: 'BOARD', entityId: '11111111-1111-4111-8111-111111111111', jobId: null, action: 'TASK_MOVED' })
  await page.waitForTimeout(600)
  expect(jobLists()).toBe(afterJob)

  // connection lost: the client reconnects with a NEW ticket and refetches what is on screen
  await server.sockets[0].close()
  await expect.poll(() => server.auths.length, { timeout: 15_000 }).toBe(2)
  expect(tickets).toHaveLength(2)
  expect(server.auths[1]).toEqual({ ticket: tickets[1] })
  expect(tickets[1]).not.toBe(tickets[0])
  await expect.poll(jobLists).toBeGreaterThan(afterJob)
})

test('no realtime service: the app keeps working and opens no socket', async ({ page, api }) => {
  let sockets = 0
  await page.routeWebSocket(/\/realtime\/socket\.io\//, () => {
    sockets++
  })
  // the default fake API answers the ticket request with 404 (realtime not deployed)
  await page.goto('/jobs')
  await expect(page.getByText('لا توجد أوامر عمل')).toBeVisible()
  await expect.poll(() => api.requests.some((r) => r.path === '/realtime/ticket')).toBe(true)
  await page.waitForTimeout(500)
  expect(sockets).toBe(0)
})

test('signing out closes the socket', async ({ page, isMobile }) => {
  const { server } = await fakeRealtime(page)
  await page.goto('/')
  await expect.poll(() => server.auths.length).toBe(1)
  let closed = false
  server.sockets[0].onClose(() => {
    closed = true
  })
  if (isMobile) await page.getByRole('button', { name: 'Toggle Sidebar' }).first().click() // sidebar is a sheet
  await page.getByRole('button', { name: 'تسجيل الخروج' }).click()
  await expect.poll(() => closed).toBe(true)
})
