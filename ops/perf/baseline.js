// Read-path load baseline (R13) with k6 — run against a running stack, never against production data:
//   docker run --rm -i --network host -e BASE_URL=http://localhost:8088 grafana/k6:1.3.0 run - < ops/perf/baseline.js
// setup() registers a NEW throwaway workspace and seeds customers, sites, work orders and visits, so
// it never reads or changes existing data. Thresholds below are the R13 p95 targets.
import http from 'k6/http'
import { check, fail } from 'k6'

const BASE = (__ENV.BASE_URL || 'http://localhost:8088') + '/api/v1'
const SEED = Number(__ENV.SEED || 40)

export const options = {
  scenarios: {
    office: { executor: 'constant-vus', vus: Number(__ENV.VUS || 20), duration: __ENV.DURATION || '60s' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:customers}': ['p(95)<300'],
    'http_req_duration{name:customerSearch}': ['p(95)<300'],
    'http_req_duration{name:jobs}': ['p(95)<300'],
    'http_req_duration{name:jobDetail}': ['p(95)<300'],
    'http_req_duration{name:visits}': ['p(95)<300'],
    'http_req_duration{name:unread}': ['p(95)<150'],
    'http_req_duration{name:dashboard}': ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

function headers(token) {
  return { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest', Authorization: `Bearer ${token}` }
}

function post(path, body, token) {
  const r = http.post(BASE + path, JSON.stringify(body), { headers: headers(token), tags: { name: 'seed' } })
  if (r.status >= 300) fail(`${path} → ${r.status} ${r.body}`)
  return r.json()
}

export function setup() {
  const stamp = Date.now()
  const reg = http.post(
    BASE + '/auth/register',
    JSON.stringify({
      email: `perf-${stamp}@load.test`,
      password: `Pf9-${Math.random().toString(36).slice(2, 12)}-Zq`,
      firstName: 'Load',
      lastName: 'Test',
      workspaceName: `Load ${stamp}`,
      language: 'ar',
      sector: 'MAINTENANCE',
    }),
    { headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }, tags: { name: 'seed' } },
  )
  if (reg.status !== 201 && reg.status !== 200) fail(`register → ${reg.status} ${reg.body}`)
  const token = reg.json('accessToken')
  const jobIds = []
  for (let i = 0; i < SEED; i++) {
    const c = post('/contacts', { type: 'CLIENT', kind: 'COMPANY', company: `شركة الاختبار ${i}`, phone: `+2022345${String(1000 + i)}` }, token)
    const s = post('/locations', { contactId: c.id, type: 'COMMERCIAL', name: `الفرع ${i}`, address: { city: 'القاهرة' } }, token)
    const j = post('/jobs', { title: `صيانة دورية ${i}`, contactId: c.id, locationId: s.id, type: 'CORRECTIVE_REPAIR', priority: 'NORMAL' }, token)
    const start = new Date(Date.now() + (i % 7) * 86400000)
    post(`/jobs/${j.id}/visits`, { scheduledStart: start.toISOString(), scheduledEnd: new Date(start.getTime() + 7200000).toISOString() }, token)
    jobIds.push(j.id)
  }
  return { token, jobIds }
}

export default function (data) {
  const h = { headers: headers(data.token) }
  const get = (name, path) => {
    const r = http.get(BASE + path, Object.assign({ tags: { name } }, h))
    check(r, { [`${name} 200`]: (x) => x.status === 200 })
  }
  get('customers', '/contacts?page=0&size=20&sort=createdAt,desc')
  get('customerSearch', '/contacts?page=0&size=20&q=' + encodeURIComponent('الاختبار 1'))
  get('jobs', '/jobs?page=0&size=20&sort=createdAt,desc')
  get('jobDetail', `/jobs/${data.jobIds[Math.floor(Math.random() * data.jobIds.length)]}`)
  get('visits', '/visits?size=50&sort=scheduledStart,asc')
  get('unread', '/notifications/unread-count')
  get('dashboard', '/reports/dashboard?period=MONTH')
}
